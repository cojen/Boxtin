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

import java.net.URI;

import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import java.lang.reflect.Modifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.function.Consumer;

import static org.cojen.boxtin.ConstantPool.*;

/**
 * ClassInfo is used by deep validation.
 *
 * @author Brian S. O'Neill
 */
final class ClassInfo {
    /**
     * Returns a map of class names to ClassInfos, as discovered in all of the given modules,
     * excluding java.lang.Object.
     */
    static Map<String, ClassInfo> decodeModules(Iterable<String> moduleNames) throws IOException {
        var allClasses = new HashMap<String, ClassInfo>();

        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));

        for (String moduleName : moduleNames) {
            decodeModule(allClasses, fs, moduleName);
        }

        return allClasses;
    }

    private static void decodeModule(Map<String, ClassInfo> allClasses,
                                     FileSystem fs, String moduleName)
        throws IOException
    {
        Path base = fs.getPath("modules", moduleName);

        Files.walk(base).forEach(path -> {
            if (!Files.isDirectory(path) && path.getFileName().toString().endsWith(".class")) {
                try {
                    byte[] classFile = Files.readAllBytes(path);

                    String className;
                    {
                        String name = path.subpath(2, path.getNameCount()).toString();
                        className = name.substring(0, name.length() - 6);
                    }

                    ClassInfo ci = findClassInfo(allClasses, className);

                    if (ci != null) {
                        ci.decode(allClasses, moduleName, className, classFile);
                    }
                } catch (IOException e) {
                    throw Utils.rethrow(e);
                }
            }
        });
    }

    /**
     * @return null if the class is excluded
     */
    private static ClassInfo findClassInfo(Map<String, ClassInfo> allClasses, String className) {
        if (className.equals("java/lang/Object")) {
            return null;
        }
        return allClasses.computeIfAbsent(className, k -> new ClassInfo(className));
    }

    private final String mPackageName, mClassName;

    private String mModuleName;

    // Set of immediate superclasses and interfaces. Is null if none.
    private Set<ClassInfo> mSuperClasses;

    // Maps names of non-static accessible methods to descriptor sets. Is null if none. The
    // descriptors are just the method parameters, not including parenthesis or the return type.
    private Map<String, Object> mMethods;

    private boolean mValidated;

    /**
     * @param className the fully qualified class name, using '/' separators.
     */
    ClassInfo(String className) {
        String packageName;
        {
            int ix = className.lastIndexOf('/');
            if (ix < 0) {
                packageName = "";
            } else {
                packageName = className.substring(0, ix);
                className = className.substring(ix + 1);
            }
        }

        mPackageName = packageName;
        mClassName = className;
    }

    /**
     * Validates that method rules are consistent when inherited, excluding target denied
     * methods. Once validate is called, future calls have no effect.
     */
    void validate(Rules rules, Consumer<String> reporter) {
        if (mValidated) {
            return;
        }

        mValidated = true;

        if (mSuperClasses != null) {
            for (ClassInfo sup : mSuperClasses) {
                sup.validate(rules, reporter);
            }
        }

        if (mMethods == null) {
            return;
        }

        Rules.ForClass forClass = rules.forClass(mPackageName, mClassName);

        for (Map.Entry<String, Object> e : mMethods.entrySet()) {
            String name = e.getKey();
            Object value = e.getValue();

            if (value instanceof String desc) {
                validateMethod(rules, forClass, name, desc, reporter);
            } else {
                @SuppressWarnings("unchecked")
                var descSet = (Set<String>) value;
                for (String desc : descSet) {
                    validateMethod(rules, forClass, name, desc, reporter);
                }
            }
        }
    }

    private void validateMethod(Rules rules, Rules.ForClass forClass, String name, String desc,
                                Consumer<String> reporter)
    {
        Rule rule = forClass.ruleForMethod(name, desc);
        if (!rule.isDeniedAtTarget() && mSuperClasses != null) {
            for (ClassInfo sup : mSuperClasses) {
                sup.validateInherited(this, rules, rule, name, desc, reporter);
            }
        }
    }

    private void validateInherited(ClassInfo base,
                                   Rules rules, Rule expect, String name, String desc,
                                   Consumer<String> reporter)
    {
        if (isMethodDefined(name, desc)) {
            Rule rule = rules.forClass(mPackageName, mClassName).ruleForMethod(name, desc);
            if (!rule.isDeniedAtTarget() && !rule.equals(expect)) {
                reporter.accept("Rule defined at " + base + '.' + name + '(' + desc + ") is " +
                                expect + ", but rule inherited from " + this + " is " + rule);
            }
        } else if (mSuperClasses != null) {
            for (ClassInfo sup : mSuperClasses) {
                sup.validateInherited(base, rules, expect, name, desc, reporter);
            }
        }
    }

    private boolean isMethodDefined(String name, String desc) {
        Object value;
        if (mMethods == null || (value = mMethods.get(name)) == null) {
            return false;
        }
        if (value instanceof String s) {
            return s.equals(desc);
        }
        @SuppressWarnings("unchecked")
        var descSet = (Set<String>) value;
        return descSet.contains(desc);
    }

    /**
     * Decodes this class and stores an entry into the map.
     */
    private void decode(Map<String, ClassInfo> allClasses, String moduleName,
                        String className, byte[] classFile)
        throws IOException
    {
        if (mModuleName != null) {
            // Has already been decoded.
            return;
        }

        mModuleName = Objects.requireNonNull(moduleName);

        var decoder = new BufferDecoder(classFile);

        if (decoder.readInt() != 0xCAFEBABE) {
            return;
        }

        // Skip minor/major version.
        decoder.readInt();

        var cp = ConstantPool.decode(decoder);

        int classAccessFlags = decoder.readUnsignedShort();

        int thisClassIndex = decoder.readUnsignedShort();

        if (!cp.findConstant(thisClassIndex, C_Class.class).mValue.equals(className)) {
            return;
        }

        int superClassIndex = decoder.readUnsignedShort();

        if (superClassIndex != 0) {
            decodeSuper(allClasses, cp.findConstant(superClassIndex, C_Class.class));
        }

        // Decode the super interfaces.
        for (int count = decoder.readUnsignedShort(), i=0; i<count; i++) {
            int ifaceIndex = decoder.readUnsignedShort();
            decodeSuper(allClasses, cp.findConstant(ifaceIndex, C_Class.class));
        }

        // Skip the fields.
        for (int i = decoder.readUnsignedShort(); --i >= 0;) {
            // Skip access_flags, name_index, and descriptor_index.
            decoder.skipNBytes(2 + 2 + 2);
            decoder.skipAttributes();
        }

        // Decode the accessible non-static methods.

        if (!Utils.isAccessible(classAccessFlags)) {
            return;
        }

        Map<String, Object> methods = mMethods;

        for (int count = decoder.readUnsignedShort(), i=0; i<count; i++) {
            int accessFlags = decoder.readUnsignedShort();
            int nameIndex = decoder.readUnsignedShort();
            int descIndex = decoder.readUnsignedShort();
            decoder.skipAttributes();

            if (!Utils.isAccessible(accessFlags) || Modifier.isStatic(accessFlags)) {
                continue;
            }

            ConstantPool.C_UTF8 name = cp.findConstantUTF8(nameIndex);

            if (name.isConstructor()) {
                continue;
            }

            String nameStr = name.str().intern();

            String descStr = cp.findConstantUTF8(descIndex).str();
            descStr = descStr.substring(1, descStr.lastIndexOf(')')).intern();

            if (methods == null) {
                mMethods = methods = new HashMap<>();
                methods.put(nameStr, descStr);
                continue;
            }

            Object value = methods.get(nameStr);

            if (value == null) {
                methods.put(nameStr, descStr);
                continue;
            }

            if (value instanceof String s) {
                if (!s.equals(descStr)) {
                    methods.put(nameStr, Set.of(s, descStr));
                }
                continue;
            }

            @SuppressWarnings("unchecked")
            var descSet = (Set<String>) value;

            if (descSet.size() == 2) {
                var newSet = new HashSet<String>(4);
                newSet.addAll(descSet);
                descSet = newSet;
                methods.put(nameStr, descSet);
            }

            descSet.add(descStr);
        }
    }

    private void decodeSuper(Map<String, ClassInfo> allClasses, C_Class sup) {
        ClassInfo supInfo = findClassInfo(allClasses, sup.mValue.str());

        if (supInfo == null) {
            return;
        }

        Set<ClassInfo> superClasses = mSuperClasses;

        if (superClasses == null) {
            mSuperClasses = Set.of(supInfo);
        } else {
            if (superClasses.size() == 1) {
                var newSet = new HashSet<ClassInfo>(4);
                newSet.addAll(superClasses);
                mSuperClasses = superClasses = newSet;
            }
            superClasses.add(supInfo);
        }
    }

    @Override
    public int hashCode() {
        return mPackageName.hashCode() * 31 + mClassName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ClassInfo ci
            && mPackageName.equals(ci.mPackageName) && mClassName.equals(ci.mClassName);
    }

    @Override
    public String toString() {
        return mPackageName.replace('/', '.') + '.' + mClassName.replace('/', '.');
    }
}
