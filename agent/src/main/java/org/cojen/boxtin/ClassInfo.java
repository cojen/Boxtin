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
import java.io.UncheckedIOException;

import java.lang.reflect.Modifier;

import java.util.HashMap;
import java.util.HashSet;
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
     * @param packageToModule maps package names to modules
     * @param layer finds the module for providing the class data
     * @return null if not found
     */
    static ClassInfo find(String fullClassName, Map<String, Module> packageToModule)
        throws UncheckedIOException, ClassFormatException
    {
        return find(fullClassName, packageName(fullClassName), packageToModule);
    }

    /**
     * @param fullClassName name must have '/' characters as separators
     * @param packageToModule maps package names to modules
     * @param layer finds the module for providing the class data
     * @return null if not found
     */
    static ClassInfo find(String fullClassName, String packageName,
                          Map<String, Module> packageToModule)
        throws UncheckedIOException, ClassFormatException
    {
        Module module = packageToModule.get(packageName);
        return module == null ? null : find(fullClassName, module);
    }

    /**
     * @param module provides the class data
     * @return null if not found
     */
    static ClassInfo find(String fullClassName, Module module)
        throws UncheckedIOException, ClassFormatException
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
            try {
                byte[] classFile;
                String path = fullClassName + ".class";
                try (var in = module.getResourceAsStream(path)) {
                    if (in == null) {
                        return null;
                    }
                    classFile = in.readAllBytes();
                }
                info = new ClassInfo(fullClassName, classFile);
                infos.put(fullClassName.intern(), info);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return info;
    }

    private final String mSuperClassName;

    private final Set<String> mInterfaceNames;

    // Maps names of accessible methods to Desc instances or sets of Desc instances.
    private final Map<String, Object> mInstanceMethods, mStaticMethods;

    ClassInfo(String fullClassName, byte[] classFile) throws IOException, ClassFormatException {
        var decoder = new BufferDecoder(classFile);

        if (decoder.readInt() != 0xCAFEBABE) {
            throw new ClassFormatException(fullClassName);
        }

        // Skip minor/major version.
        decoder.readInt();

        var cp = ConstantPool.decode(decoder);

        int classFlags = decoder.readUnsignedShort();

        int thisClassIndex = decoder.readUnsignedShort();

        if (!cp.findConstant(thisClassIndex, C_Class.class).mValue.equals(fullClassName)) {
            throw new ClassFormatException(fullClassName);
        }

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
                instanceMethods = putMethod(instanceMethods, methodFlags, nameStr, descStr);
            } else {
                staticMethods = putMethod(staticMethods, methodFlags, nameStr, descStr);
            }
        }

        mInstanceMethods = instanceMethods == null ? Map.of() : instanceMethods;
        mStaticMethods = staticMethods == null ? Map.of() : staticMethods;
    }

    /**
     * @param methods can be null initially
     * @return new or existing map
     */
    private static Map<String, Object> putMethod(Map<String, Object> methods,
                                                 int accessFlags, String name, String descStr)
    {
        name = name.intern();

        int ix = descStr.lastIndexOf(')');
        Desc desc = new Desc(accessFlags, descStr.substring(1, ix).intern(),
                             descStr.substring(ix + 1).intern());

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

        if (value instanceof Desc d) {
            if (!d.equals(desc)) {
                methods.put(name, Set.of(d, desc));
            }
            return methods;
        }

        @SuppressWarnings("unchecked")
        var descSet = (Set<Desc>) value;

        if (descSet.size() == 2) {
            var newSet = new HashSet<Desc>(4);
            newSet.addAll(descSet);
            descSet = newSet;
            methods.put(name, descSet);
        }

        descSet.add(desc);

        return methods;
    }

    /**
     * @param paramDesc the parameter descriptor with no parens and no return type
     * @parens returnType the return type descriptor
     */
    public record Desc(int accessFlags, String paramDesc, String returnType) {
        public String fullDescriptor() {
            return '(' + paramDesc + ')' + returnType;
        }
    }

    /**
     * Iterates over all methods and passes to them to the given consumer as name/desc pairs,
     * including all inherited methods, but excluding methods declared in java.lang.Object.
     *
     * @param packageToModule maps package names to modules
     * @param consumer receives all accessible method name/desc pairs for methods, including
     * inherited ones; the consumer can return false to stop the iteration
     */
    boolean forAllMethods(Map<String, Module> packageToModule,
                          Predicate<Map.Entry<String, Desc>> consumer)
        throws UncheckedIOException, ClassFormatException
    {
        return forAllMethods(packageToModule, true, true, consumer);
    }

    /**
     * Iterates over all static methods and passes to them to the given consumer as name/desc
     * pairs, including all inherited methods, but excluding methods declared in
     * java.lang.Object.
     *
     * @param packageToModule maps package names to modules
     * @param consumer receives all accessible method name/desc pairs for methods, including
     * inherited ones; the consumer can return false to stop the iteration
     */
    boolean forAllStaticMethods(Map<String, Module> packageToModule,
                                Predicate<Map.Entry<String, Desc>> consumer)
        throws UncheckedIOException, ClassFormatException
    {
        return forAllMethods(packageToModule, true, false, consumer);
    }

    private boolean forAllMethods(Map<String, Module> packageToModule,
                                  boolean staticMethods, boolean instanceMethods,
                                  Predicate<Map.Entry<String, Desc>> consumer)
        throws UncheckedIOException, ClassFormatException
    {
        String superName = mSuperClassName;
        if (superName == null) {
            // Skip the methods defined in java.lang.Object.
            return true;
        }

        if (staticMethods && !doForAllMethods(mStaticMethods, consumer)) {
            return false;
        }

        if (instanceMethods && !doForAllMethods(mInstanceMethods, consumer)) {
            return false;
        }

        // Now do the inherited methods.

        ClassInfo superInfo = find(superName, packageToModule);
        if (superInfo != null) {
            if (!superInfo.forAllMethods
                (packageToModule, staticMethods, instanceMethods, consumer))
            {
                return false;
            }
        }

        for (String ifaceName : mInterfaceNames) {
            ClassInfo ifaceInfo = find(ifaceName, packageToModule);
            if (ifaceInfo != null) {
                // Note that inherited statics are ignored, because static interface methods
                // aren't really inherited.
                if (!ifaceInfo.forAllMethods(packageToModule, false, instanceMethods, consumer)) {
                    return false;
                }
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean doForAllMethods(Map<String, Object> methods,
                                           Predicate<Map.Entry<String, Desc>> consumer)
    {

        for (Map.Entry entry : methods.entrySet()) {
            var name = (String) entry.getKey();
            if (name.equals("<init>")) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Desc desc) {
                if (!isObjectMethod(name, desc.paramDesc()) &&
                    !consumer.test((Map.Entry<String, Desc>) entry))
                {
                    return false;
                }
            } else {
                for (Desc desc : ((Set<Desc>) value)) {
                    if (!isObjectMethod(name, desc.paramDesc()) &&
                        !consumer.test(Map.entry(name, desc)))
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
