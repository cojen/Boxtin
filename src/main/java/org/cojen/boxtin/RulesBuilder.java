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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class RulesBuilder {
    // FIXME: Note that when building the final rules, there might still be some redundancies.
    // If denyAllMethods is followed by denyMethod(name), a MethodScope is still created. If a
    // MethodScope has no variant rules and its allowByDefault rule is the same as the parent,
    // then it's not needed.

    // Can be null when empty.
    private Map<String, PackageScope> mPackages;

    // Default is selected when no map entry is found.
    private boolean mAllowByDefault;

    public RulesBuilder() {
        denyAll();
    }

    /**
     * Deny access to all packages, superseding all previous rules. This action is
     * recursive, denying access to all packages, classes, constructors, etc.
     *
     * @return this
     */
    public RulesBuilder denyAll() {
        mPackages = null;
        mAllowByDefault = false;
        return this;
    }

    /**
     * Allow access to all packages, superseding all previous rules. This action is
     * recursive, allowing access to all packages, classes, constructors, etc.
     *
     * @return this
     */
    public RulesBuilder allowAll() {
        mPackages = null;
        mAllowByDefault = true;
        return this;
    }

    /**
     * Define specific rules against the given package, which can supersede all previous rules.
     *
     * @param name fully qualified package name
     */
    public PackageScope forPackage(String name) {
        final String vmName = name.replace('.', '/');
        Map<String, PackageScope> packages = mPackages;
        if (packages == null) {
            mPackages = packages = new HashMap<>();
        }
        return packages.computeIfAbsent(vmName, k -> {
            var scope = new PackageScope(this, vmName);
            return mAllowByDefault ? scope.allowAll() : scope.denyAll();
        });
    }

    /**
     * Define specific rules against the given package, which can supersede all previous rules.
     */
    public PackageScope forPackage(Package p) {
        return forPackage(p.getName());
    }

    /**
     * Validates that all classes are loadable, and that all class members are found. An
     * exception is thrown if validation fails.
     *
     * @param loader required
     * @return this
     */
    public RulesBuilder validate(ClassLoader loader)
        throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException
    {
        Objects.requireNonNull(loader);
        if (mPackages != null) {
            for (PackageScope ps : mPackages.values()) {
                ps.validate(loader);
            }
        }
        return this;
    }

    /**
     * Returns a immutable set of rules based on what's been defined so far.
     */
    public Rules build() {
        return ImmutableRules.build(buildPackageMap(), mAllowByDefault);
    }

    private MemberRefPackageMap<ImmutableRules.PackageScope> buildPackageMap() {
        if (mPackages == null || mPackages.isEmpty()) {
            return null;
        }

        return new MemberRefPackageMap<>
            (mPackages.size(),
             mPackages.entrySet().stream().map((Map.Entry<String, PackageScope> e) -> {
                 PackageScope scope = e.getValue();
                 return Map.entry(e.getKey(), ImmutableRules.PackageScope.build
                                  (scope.buildClassMap(), scope.mAllowByDefault));
             }));
    }

    private static String nameFor(Class<?> clazz) {
        String name = clazz.getSimpleName();
        Class<?> enclosing = clazz.getEnclosingClass();
        return name == null ? name : nameFor(enclosing) + '.' + name;
    }

    private static void checkMethodName(String name) {
        if (name.indexOf('<') >= 0 || name.indexOf('>') >= 0) {
            throw new IllegalArgumentException("Invalid method name");
        }
    }

    private static Class<?>[] paramTypesFor(ClassLoader loader, String descriptor)
        throws ClassNotFoundException, NoSuchMethodException
    {
        if (descriptor.isEmpty()) {
            return new Class<?>[0];
        }

        var paramTypes = new ArrayList<Class<?>>();

        for (int pos = 0; pos < descriptor.length(); ) {
            pos = addParamType(paramTypes, loader, descriptor, pos);
        }

        return paramTypes.toArray(Class<?>[]::new);
    }

    /**
     * @return update pos
     */
    private static int addParamType(ArrayList<Class<?>> paramTypes,
                                    ClassLoader loader, String descriptor, int pos)
        throws ClassNotFoundException, NoSuchMethodException
    {
        char first = descriptor.charAt(pos);

        parse: {
            Class<?> type;

            switch (first) {
                default -> {
                    break parse;
                }

                case 'Z' -> type = boolean.class;
                case 'B' -> type = byte.class;
                case 'S' -> type = short.class;
                case 'C' -> type = char.class;
                case 'I' -> type = int.class;
                case 'F' -> type = float.class;
                case 'D' -> type = double.class;
                case 'J' -> type = long.class;
                case 'V' -> type = void.class;

                case '[' -> {
                    pos = addParamType(paramTypes, loader, descriptor, pos + 1);
                    int ix = paramTypes.size() - 1;
                    paramTypes.set(ix, paramTypes.get(ix).arrayType());
                    return pos;
                }

                case 'L' -> {
                    pos++;
                    int end  = descriptor.indexOf(';', pos);
                    if (end < 0) {
                        break parse;
                    }
                    String name = descriptor.substring(pos, end).replace('/', '.');
                    type = loader.loadClass(name);
                    pos = end;
                }
            }

            paramTypes.add(type);
            return pos + 1;
        }

        throw new NoSuchMethodException("Invalid descriptor: " + descriptor);
    }

    private static Constructor tryFindConstructor(final Class<?> clazz,
                                                  final Class<?>... paramTypes)
    {
        for (Constructor c : clazz.getDeclaredConstructors()) {
            if (Utils.isAccessible(c) && Arrays.equals(c.getParameterTypes(), paramTypes)) {
                return c;
            }
        }

        return null;
    }

    /**
     * Tries to find a method by name, ignoring the parameter types.
     */
    private static Method tryFindAnyMethod(Class<?> clazz, String name) {
        return tryFindMethod(clazz, name, (Class<?>[]) null);
    }

    private static Method tryFindMethod(final Class<?> clazz, final String name,
                                        final Class<?>... paramTypes)
    {
        for (Method m : clazz.getDeclaredMethods()) {
            if (Utils.isAccessible(m) && m.getName().equals(name)) {
                if (paramTypes == null || Arrays.equals(m.getParameterTypes(), paramTypes)) {
                    return m;
                }
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            Method m = tryFindMethod(superclass, name, paramTypes);
            if (m != null) {
                return m;
            }
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            Method m = tryFindMethod(iface, name, paramTypes);
            if (m != null) {
                return m;
            }
        }

        return null;
    }

    private static Field tryFindField(final Class<?> clazz, final String name) {
        for (Field f : clazz.getDeclaredFields()) {
            if (Utils.isAccessible(f) && f.getName().equals(name)) {
                return f;
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            Field f = tryFindField(superclass, name);
            if (f != null) {
                return f;
            }
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            Field f = tryFindField(iface, name);
            if (f != null) {
                return f;
            }
        }

        return null;
    }

    public static final class PackageScope {
        private final RulesBuilder mParent;
        private final String mName;

        // Can be null when empty.
        private Map<String, ClassScope> mClasses;

        // Default is selected when no map entry is found.
        private boolean mAllowByDefault;

        private PackageScope(RulesBuilder parent, String name) {
            mParent = parent;
            mName = name;
        }

        /**
         * Deny access to all classes, superseding all previous rules. This action is
         * recursive, denying access to all classes, constructors, etc.
         *
         * @return this
         */
        public PackageScope denyAll() {
            mClasses = null;
            mAllowByDefault = false;
            return this;
        }

        /**
         * Allow access to all classes, superseding all previous rules. This action is
         * recursive, allowing access to all classes, constructors, etc.
         *
         * @return this
         */
        public PackageScope allowAll() {
            mClasses = null;
            mAllowByDefault = true;
            return this;
        }

        /**
         * Define specific rules against the given class, which can supersede all previous
         * rules.
         *
         * @param name simple class name (not fully qualified, use dots for inner classes)
         * @return this
         */
        public ClassScope forClass(String name) {
            final String vmName = name.replace('.', '$');
            Map<String, ClassScope> classes = mClasses;
            if (classes == null) {
                mClasses = classes = new HashMap<>();
            }
            return classes.computeIfAbsent(vmName, k -> {
                var scope = new ClassScope(this, vmName);
                return mAllowByDefault ? scope.allowAll() : scope.denyAll();
            });
        }

        /**
         * Define specific rules against the given class, which can supersede all previous
         * rules.
         *
         * @return this
         * @param clazz class which must be in this package
         * @throws IllegalArgumentException if the given class isn't in this package, or if the
         * class is unsupported: anonymous, array, hidden, local, or primitive
         */
        public ClassScope forClass(Class<?> clazz) {
            if (!mName.equals(clazz.getPackageName())) {
                throw new IllegalArgumentException("Wrong package");
            }
            if (clazz.isAnonymousClass() || clazz.isArray() || clazz.isHidden() ||
                clazz.isLocalClass() || clazz.isPrimitive())
            {
                throw new IllegalArgumentException("Unsupported type");
            }
            return forClass(nameFor(clazz));
        }

        /**
         * End the current rules for this package and return to the outermost scope. More rules
         * can be added to the scope later if desired.
         */
        public RulesBuilder end() {
            return mParent;
        }

        /**
         * Validates that all classes are loadable, and that all class members are found.
         *
         * @param loader required
         * @throws IllegalStateException if validation fails
         */
        public void validate(ClassLoader loader) 
            throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException
        {
            Objects.requireNonNull(loader);
            if (mClasses != null) {
                for (ClassScope cs : mClasses.values()) {
                    cs.validate(loader);
                }
            }
        }

        private MemberRefPlainClassMap<ImmutableRules.ClassScope> buildClassMap() {
            if (mClasses == null || mClasses.isEmpty()) {
                return null;
            }

            return new MemberRefPlainClassMap<>
                (mClasses.size(),
                 mClasses.entrySet().stream().map((Map.Entry<String, ClassScope> e) -> {
                     ClassScope scope = e.getValue();
                     return Map.entry(e.getKey(), ImmutableRules.ClassScope.build
                                      (scope.buildMethodMap(), scope.mAllowMethodsByDefault,
                                       scope.mAllowConstructorsByDefault,
                                       scope.buildFieldMap(), scope.mAllowFieldsByDefault));
                 }));
        }
    }

    public static final class ClassScope {
        private final PackageScope mParent;
        private final String mName;

        // Can be null when empty.
        private Map<String, MethodScope> mMethods;

        // Default is selected when no method map entry is found.
        private boolean mAllowMethodsByDefault;

        // Default is selected when no constructor method map entry is found.
        private boolean mAllowConstructorsByDefault;

        // Can be null when empty.
        private Map<String, Boolean> mFields;

        // Default is selected when no field map entry is found.
        private boolean mAllowFieldsByDefault;

        // Is set when a variant rule can be specified.
        private MethodScope mVariantScope;

        private ClassScope(PackageScope parent, String name) {
            mParent = parent;
            mName = name;
        }

        /**
         * Deny access to all constructors, methods, and fields, superseding all previous
         * rules.
         *
         * @return this
         */
        public ClassScope denyAll() {
            mAllowConstructorsByDefault = false;
            return denyAllMethods().denyAllFields();
        }

        /**
         * Deny access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllConstructors() {
            mVariantScope = forConstructor().denyAll();
            mAllowConstructorsByDefault = false;
            return this;
        }

        /**
         * Deny access to all methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllMethods() {
            removeAllMethodScopes();
            mAllowMethodsByDefault = false;
            return this;
        }

        /**
         * Deny access to all variants of the given method, superseding all previous rules.
         *
         * @return this
         * @throws IllegalArgumentException if not a valid method name
         */
        public ClassScope denyMethod(String name) {
            checkMethodName(name);
            mVariantScope = forMethod(name).denyAll();
            return this;
        }

        /**
         * Allow access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @param descriptor descriptor for the parameters, not including parenthesis or the
         * return type
         * @return this
         * @throws IllegalStateException if no current constructor or method, or if all
         * variants are explicitly allowed
         */
        public ClassScope allowVariant(String descriptor) {
            if (mVariantScope == null) {
                throw new IllegalStateException("No current constructor or method");
            }
            if (mVariantScope.isAllAllowed()) {
                throw new IllegalStateException("All variants are explicitly allowed");
            }
            mVariantScope.allowVariant(descriptor.replace('.', '/'));
            return this;
        }

        /**
         * Allow access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @return this
         * @throws IllegalStateException if no current constructor or method, or if all
         * variants are explicitly allowed
         */
        public ClassScope allowVariant(Class<?>... paramTypes) {
            return allowVariant(Utils.partialDescriptorFor(paramTypes));
        }

        /**
         * Deny access to all fields, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllFields() {
            variantOff();
            mFields = null;
            mAllowFieldsByDefault = false;
            return this;
        }

        /**
         * Deny access to the given field, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyField(String name) {
            return fieldAction(name, false);
        }

        /**
         * Allow access to all constructors, methods, and fields, superseding all previous
         * rules.
         *
         * @return this
         */
        public ClassScope allowAll() {
            mAllowConstructorsByDefault = true;
            return allowAllMethods().allowAllFields();
        }

        /**
         * Allow access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAllConstructors() {
            mVariantScope = forConstructor().allowAll();
            mAllowConstructorsByDefault = true;
            return this;
        }

        /**
         * Allow access to all methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAllMethods() {
            removeAllMethodScopes();
            mAllowMethodsByDefault = true;
            return this;
        }

        /**
         * Allow access to all variants of the given method, superseding all previous rules.
         *
         * @return this
         * @throws IllegalArgumentException if not a valid method name
         */
        public ClassScope allowMethod(String name) {
            checkMethodName(name);
            mVariantScope = forMethod(name).allowAll();
            return this;
        }

        /**
         * Deny access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @param descriptor descriptor for the parameters, not including parenthesis or the
         * return type
         * @return this
         * @throws IllegalStateException if no current constructor or method, or if all
         * variants are explicitly denied
         */
        public ClassScope denyVariant(String descriptor) {
            if (mVariantScope == null) {
                throw new IllegalStateException("No current constructor or method");
            }
            if (mVariantScope.isAllDenied()) {
                throw new IllegalStateException("All variants are explicitly denied");
            }
            mVariantScope.denyVariant(descriptor.replace('.', '/'));
            return this;
        }

        /**
         * Deny access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @return this
         * @throws IllegalStateException if no current constructor or method, or if all
         * variants are explicitly denied
         */
        public ClassScope denyVariant(Class<?>... paramTypes) {
            return denyVariant(Utils.partialDescriptorFor(paramTypes));
        }

        /**
         * Allow access to all fields, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAllFields() {
            variantOff();
            mFields = null;
            mAllowFieldsByDefault = true;
            return this;
        }

        /**
         * Allow access to the given field, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowField(String name) {
            return fieldAction(name, true);
        }

        /**
         * End the current rules for this class and return to the package scope. More rules can
         * be added to the scope later if desired.
         */
        public PackageScope end() {
            variantOff();
            return mParent;
        }

        /**
         * Validates that all classes are loadable, and that all class members are found.
         *
         * @param loader required
         * @throws IllegalStateException if validation fails
         */
        public void validate(ClassLoader loader)
            throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException
        {
            Objects.requireNonNull(loader);

            String className = mName;
            String pkg = mParent.mName;
            if (!pkg.isEmpty()) {
                className = pkg.replace('/', '.') + '.' + className;
            }

            Class<?> clazz = loader.loadClass(className);

            if (mMethods != null) {
                for (Map.Entry<String, MethodScope> e : mMethods.entrySet()) {
                    String name = e.getKey();
                    MethodScope ms = e.getValue();
                    if (name.equals("<init>")) {
                        ms.validateConstuctor(loader, clazz);
                    } else {
                        ms.validateMethod(loader, clazz, name);
                    }
                }
            }

            if (mFields != null) {
                for (String name : mFields.keySet()) {
                    try {
                        clazz.getField(name);
                    } catch (NoSuchFieldException e) {
                        if (tryFindField(clazz, name) == null) {
                            throw e;
                        }
                    }
                }
            }
        }

        private MethodScope forMethod(String name) {
            Map<String, MethodScope> methods = mMethods;
            if (methods == null) {
                mMethods = methods = new HashMap<>();
            }
            return methods.computeIfAbsent(name, k -> {
                var scope = new MethodScope();
                return mAllowMethodsByDefault ? scope.allowAll() : scope.denyAll();
            });
        }

        private MethodScope forConstructor() {
            Map<String, MethodScope> methods = mMethods;
            if (methods == null) {
                mMethods = methods = new HashMap<>();
            }
            return methods.computeIfAbsent("<init>", k -> {
                var scope = new MethodScope();
                return mAllowConstructorsByDefault ? scope.allowAll() : scope.denyAll();
            });
        }

        /**
         * Removes all method scopes not named <init>.
         */
        private void removeAllMethodScopes() {
            variantOff();

            if (mMethods != null && !mMethods.isEmpty()) {
                Iterator<String> it = mMethods.keySet().iterator();
                while (it.hasNext()) {
                    String name = it.next();
                    if (!"<init>".equals(name)) {
                        it.remove();
                    }
                }
            }
        }

        private ClassScope fieldAction(String name, boolean allow) {
            variantOff();

            Objects.requireNonNull(name);

            Map<String, Boolean> fields = mFields;

            if (fields == null) {
                if (allow == mAllowFieldsByDefault) {
                    return this;
                }
                mFields = fields = new HashMap<>();
            }

            if (allow == mAllowFieldsByDefault) {
                fields.remove(name);
            } else {
                fields.put(name, allow);
            }

            return this;
        }

        private void variantOff() {
            mVariantScope = null;
        }

        private MemberRefNameMap<ImmutableRules.MethodScope> buildMethodMap() {
            if (mMethods == null || mMethods.isEmpty()) {
                return null;
            }

            return new MemberRefNameMap<>
                (mMethods.size(),
                 mMethods.entrySet().stream().map((Map.Entry<String, MethodScope> e) -> {
                     MethodScope scope = e.getValue();
                     return Map.entry(e.getKey(), ImmutableRules.MethodScope.build
                                      (scope.buildVariantMap(), scope.mAllowByDefault));
                 }));
        }

        private MemberRefNameMap<Boolean> buildFieldMap() {
            if (mFields == null || mFields.isEmpty()) {
                return null;
            }

            return new MemberRefNameMap<>(mFields.size(), mFields.entrySet().stream());
        }
    }

    private static final class MethodScope {
        // Can be null when empty.
        Map<String, Boolean> mVariants;

        // Default is selected when no map entry is found.
        boolean mAllowByDefault;

        private MethodScope() {
        }

        public boolean isAllDenied() {
            return !mAllowByDefault && hasNoVariants();
        }

        /**
         * Deny access to all variants, superseding all previous rules.
         *
         * @return this
         */
        public MethodScope denyAll() {
            mVariants = null;
            mAllowByDefault = false;
            return this;
        }

        public MethodScope denyVariant(String descriptor) {
            return variantAction(descriptor, false);
        }

        public boolean isAllAllowed() {
            return mAllowByDefault && hasNoVariants();
        }

        /**
         * Allow access to all variants, superseding all previous rules.
         *
         * @return this
         */
        public MethodScope allowAll() {
            mVariants = null;
            mAllowByDefault = true;
            return this;
        }

        public MethodScope allowVariant(String descriptor) {
            return variantAction(descriptor, true);
        }

        private MethodScope variantAction(String descriptor, boolean allow) {
            Objects.requireNonNull(descriptor);

            Map<String, Boolean> variants = mVariants;

            if (variants == null) {
                if (allow == mAllowByDefault) {
                    return this;
                }
                mVariants = variants = new HashMap<>();
            }

            if (allow == mAllowByDefault) {
                variants.remove(descriptor);
            } else {
                variants.put(descriptor, allow);
            }

            return this;
        }

        void validateConstuctor(ClassLoader loader, Class<?> clazz)
            throws ClassNotFoundException, NoSuchMethodException
        {
            if (hasNoVariants()) {
                // Assume that a constructor exists.
                return;
            }

            for (String descriptor : mVariants.keySet()) {
                Class<?>[] paramTypes = paramTypesFor(loader, descriptor);
                try {
                    clazz.getConstructor(paramTypes);
                } catch (NoSuchMethodException e) {
                    if (tryFindConstructor(clazz, paramTypes) == null) {
                        throw e;
                    }
                }
            }
        }

        void validateMethod(ClassLoader loader, Class<?> clazz, String name)
            throws ClassNotFoundException, NoSuchMethodException
        {
            if (hasNoVariants()) {
                if (tryFindAnyMethod(clazz, name) != null) {
                    return;
                }
                throw new NoSuchMethodException(clazz + "." + name);
            }

            for (String descriptor : mVariants.keySet()) {
                Class<?>[] paramTypes = paramTypesFor(loader, descriptor);
                try {
                    clazz.getMethod(name, paramTypes);
                } catch (NoSuchMethodException e) {
                    if (tryFindMethod(clazz, name, paramTypes) == null) {
                        throw e;
                    }
                }
            }
        }

        private boolean hasNoVariants() {
            return mVariants == null || mVariants.isEmpty();
        }

        private MemberRefDescriptorMap<Boolean> buildVariantMap() {
            if (mVariants == null || mVariants.isEmpty()) {
                return null;
            }

            return new MemberRefDescriptorMap<>(mVariants.size(), mVariants.entrySet().stream());
        }
    }
}
