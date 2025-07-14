/*
 *  Copyright 2025 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.boxtin;

import java.io.IOException;

import java.lang.reflect.Modifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import java.util.function.Predicate;

import static org.cojen.boxtin.ConstantPool.*;
import static org.cojen.boxtin.Utils.*;

/**
 * ClassInfo is used for performing basic reflection.
 *
 * @author Brian S. O'Neill
 */
final class ClassInfo {
    private static final SoftCache<Module, SoftCache<String, ClassInfo>> cCache = new SoftCache<>();

    /**
     * @param fullClassName name must have '/' characters as separators
     * @param rules used to find the module name which provides the class
     * @param layer finds the module for providing the class data
     * @return null if not found
     */
    static ClassInfo find(String fullClassName, RuleSet rules, ModuleLayer layer)
        throws IOException, ClassFormatException
    {
        String packageName = packageName(fullClassName);
        String moduleName = rules.moduleForPackage(packageName);

        if (moduleName == null) {
            return null;
        }

        Module module = layer.findModule(moduleName).orElse(null);

        return module == null ? null : find(fullClassName, packageName, module);
    }

    /**
     * @param packageName can be null to derive from fullClassName
     * @param module provides the class data
     * @return null if not found
     */
    static ClassInfo find(String fullClassName, String packageName, Module module)
        throws IOException, ClassFormatException
    {
        SoftCache<String, ClassInfo> infos = cCache.get(module);

        if (infos == null) {
            synchronized (module) {
                infos = cCache.get(module);
                if (infos == null) {
                    infos = new SoftCache<>();
                    cCache.put(module, infos);
                }
            }
        }

        ClassInfo info = infos.get(fullClassName);

        if (info == null) {
            byte[] classFile;
            String path = fullClassName + ".class";
            try (var in = module.getResourceAsStream(path)) {
                if (in == null) {
                    return null;
                }
                classFile = in.readAllBytes();
            }
            if (packageName == null) {
                packageName = packageName(fullClassName);
            }
            info = new ClassInfo(module.getLayer(), packageName, fullClassName, classFile);
            infos.put(fullClassName.intern(), info);
        }

        return info;
    }

    private final ModuleLayer mLayer;

    private final String mPackageName, mClassName;

    private final int mClassFlags;

    private final boolean mSealed;

    private final String mSuperClassName;

    private final Set<String> mInterfaceNames;

    // Maps names of accessible methods to descriptor sets. The descriptors are partial
    // descriptors, in that they omit parenthesis and the return type.
    private final Map<String, Object> mInstanceMethods, mStaticMethods;

    ClassInfo(ModuleLayer layer, String packageName, String fullClassName, byte[] classFile)
        throws IOException, ClassFormatException
    {
        mLayer = layer;

        var decoder = new BufferDecoder(classFile);

        if (decoder.readInt() != 0xCAFEBABE) {
            throw new ClassFormatException(fullClassName);
        }

        // Skip minor/major version.
        decoder.readInt();

        var cp = ConstantPool.decode(decoder);

        mClassFlags = decoder.readUnsignedShort();

        int thisClassIndex = decoder.readUnsignedShort();

        if (!cp.findConstant(thisClassIndex, C_Class.class).mValue.equals(fullClassName)) {
            throw new ClassFormatException(fullClassName);
        }

        mPackageName = packageName;
        mClassName = className(packageName, fullClassName);

        int superClassIndex = decoder.readUnsignedShort();

        if (superClassIndex == 0) {
            mSuperClassName = null;
        } else {
            mSuperClassName = cp.findConstant(superClassIndex, C_Class.class).mValue.str().intern();
        }

        // Decode the super interface names.
        int ifaceCount = decoder.readUnsignedShort();
        if (ifaceCount == 0) {
            mInterfaceNames = Set.of();
        } else {
            var names = new String[ifaceCount];
            for (int i=0; i<ifaceCount; i++) {
                int ifaceIndex = decoder.readUnsignedShort();
                names[i] = cp.findConstant(ifaceIndex, C_Class.class).mValue.str().intern();
            }
            mInterfaceNames = Set.of(names);
        }

        // Skip the fields.
        for (int i = decoder.readUnsignedShort(); --i >= 0;) {
            // Skip access_flags, name_index, and descriptor_index.
            decoder.skipNBytes(2 + 2 + 2);
            decoder.skipAttributes();
        }

        Map<String, Object> instanceMethods = null, staticMethods = null;

        for (int count = decoder.readUnsignedShort(), i=0; i<count; i++) {
            int methodFlags = decoder.readUnsignedShort();
            int nameIndex = decoder.readUnsignedShort();
            int descIndex = decoder.readUnsignedShort();
            decoder.skipAttributes();

            if (!isAccessible(methodFlags)) {
                continue;
            }

            ConstantPool.C_UTF8 name = cp.findConstantUTF8(nameIndex);

            String nameStr = name.str();
            String descStr = cp.findConstantUTF8(descIndex).str();

            if (!Modifier.isStatic(methodFlags)) {
                instanceMethods = putMethod(instanceMethods, nameStr, descStr);
            } else {
                staticMethods = putMethod(staticMethods, nameStr, descStr);
            }
        }

        mInstanceMethods = instanceMethods == null ? Map.of() : instanceMethods;
        mStaticMethods = staticMethods == null ? Map.of() : staticMethods;

        boolean sealed = false;

        int attrsCount = decoder.readUnsignedShort();
        for (int i=0; i<attrsCount; i++) {
            int nameIndex = decoder.readUnsignedShort();
            long attrLength = decoder.readUnsignedInt();
            decoder.skipNBytes(attrLength);
            if (cp.findConstantUTF8(nameIndex).equals("PermittedSubclasses")) {
                sealed = true;
                break;
            }
        }

        mSealed = sealed;
    }

    /**
     * @param methods can be null initially
     * @return new or existing map
     */
    private static Map<String, Object> putMethod(Map<String, Object> methods,
                                                 String name, String desc)
    {
        name = name.intern();
        desc = desc.substring(1, desc.lastIndexOf(')')).intern();

        if (methods == null) {
            methods = new HashMap<>(4);
            methods.put(name, desc);
            return methods;
        }

        Object value = methods.get(name);
        if (value == null) {
            methods.put(name, desc);
            return methods;
        }

        if (value instanceof String s) {
            if (!s.equals(desc)) {
                methods.put(name, Set.of(s, desc));
            }
            return methods;
        }

        @SuppressWarnings("unchecked")
        var descSet = (Set<String>) value;

        if (descSet.size() == 2) {
            var newSet = new HashSet<String>(4);
            newSet.addAll(descSet);
            descSet = newSet;
            methods.put(name, descSet);
        }

        descSet.add(desc);

        return methods;
    }

    /**
     * @param consumer receives all accessible locally declared method name/desc pairs; the
     * consumer can return false to stop the iteration
     */
    void forAllDeclaredMethods(Predicate<Map.Entry<String, String>> consumer) {
        if (forAllMethods(mStaticMethods, consumer)) {
            forAllMethods(mInstanceMethods, consumer);
        }
    }

    /**
     * A class or interface is subtype safe if it cannot be subtyped by an external module, or
     * if subtyping doesn't gain access to denied methods.
     */
    boolean isSubtypeSafe(RuleSet rules) throws IOException, ClassFormatException {
        if (!isAccessible(mClassFlags) || Modifier.isFinal(mClassFlags) || mSealed) {
            return true;
        }

        if (Modifier.isInterface(mClassFlags)) {
            // Interfaces are subtype safe if they don't provide any instance methods. Static
            // interface methods aren't inherited, and they can only be invoked by specifying
            // the name of the interface they're defined in. This is enforced by the JVM.
            return forAllMethods(rules, false, true, method -> {
                return isObjectMethod(method.getKey(), method.getValue());
            });
        }

        Rules.ForClass forClass = rules.forClass(mPackageName, mClassName);

        boolean hasDeniedStatics = !forAllMethods(rules, true, false, method -> {
            return forClass.ruleForMethod(method.getKey(), method.getValue()).isAllowed();
        });

        if (hasDeniedStatics) {
            // Accessible denied static methods exist. Even if the class has no accessible
            // constructors, subclassing is still possible at the JVM level, providing access
            // to the denied static methods via inheritance.
            return false;
        }

        // If any accessible constructors are allowed, return false, because subclassing is
        // possible outside the module.

        return forAllConstructors(desc -> forClass.ruleForConstructor(desc).isDenied());
    }

    /**
     * @param consumer receives all accessible method name/desc pairs for methods which might
     * impact subtype safety; the consumer can return false to stop the iteration
     */
    private boolean forAllMethods(RuleSet rules, boolean staticMethods, boolean instanceMethods,
                                  Predicate<Map.Entry<String, String>> consumer)
        throws IOException, ClassFormatException
    {
        String superName = mSuperClassName;
        if (superName == null) {
            // Skip methods defined in java.lang.Object.
            return true;
        }

        if (staticMethods && !forAllMethods(mStaticMethods, consumer)) {
            return false;
        }

        if (instanceMethods && !forAllMethods(mInstanceMethods, consumer)) {
            return false;
        }

        // Now do the inherited methods.

        ClassInfo superInfo = find(superName, rules, mLayer);
        if (superInfo != null) {
            if (!superInfo.forAllMethods(rules, staticMethods, instanceMethods, consumer)) {
                return false;
            }
        }

        for (String ifaceName : mInterfaceNames) {
            ClassInfo ifaceInfo = find(ifaceName, rules, mLayer);
            if (ifaceInfo != null) {
                // Note that inherited statics are ignored, because static interface methods
                // aren't really inherited.
                if (!ifaceInfo.forAllMethods(rules, false, instanceMethods, consumer)) {
                    return false;
                }
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean forAllMethods(Map<String, Object> methods,
                                         Predicate<Map.Entry<String, String>> consumer)
    {
        Iterator<Map.Entry<String, Object>> it = methods.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry entry = it.next();
            var name = (String) entry.getKey();
            if (name.equals("<init>")) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String) {
                if (!consumer.test((Map.Entry<String, String>) entry)) {
                    return false;
                }
            } else {
                for (String desc : ((Set<String>) value)) {
                    if (!consumer.test(Map.entry(name, desc))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * @param consumer receives all accessible constructor descriptors; the consumer can return
     * false to stop the iteration
     */
    @SuppressWarnings("unchecked")
    private boolean forAllConstructors(Predicate<String> consumer) {
        Iterator<Map.Entry<String, Object>> it = mInstanceMethods.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry entry = it.next();
            var name = (String) entry.getKey();
            if (!name.equals("<init>")) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String desc) {
                if (!consumer.test(desc)) {
                    return false;
                }
            } else {
                for (String desc : ((Set<String>) value)) {
                    if (!consumer.test(desc)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
