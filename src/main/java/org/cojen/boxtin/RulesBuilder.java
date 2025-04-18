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
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static org.cojen.boxtin.Rule.*;
import static org.cojen.boxtin.Utils.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class RulesBuilder {
    // Can be null when empty.
    private Map<String, PackageScope> mPackages;

    // Default is selected when no map entry is found.
    private Rule mDefaultRule;

    public RulesBuilder() {
        denyAll();
    }

    /**
     * Applies a set of rules to this builder.
     *
     * @return this
     */
    public RulesBuilder applyRules(RulesApplier applier) {
        applier.applyRulesTo(this);
        return this;
    }

    /**
     * Deny access to all packages, superseding all previous rules. This action is
     * recursive, denying access to all packages, classes, constructors, etc.
     *
     * @return this
     */
    public RulesBuilder denyAll() {
        mPackages = null;
        mDefaultRule = TARGET_DENY;
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
        mDefaultRule = ALLOW;
        return this;
    }

    /**
     * Define specific rules against the given package, which can supersede all previous rules.
     *
     * @param name fully qualified package name
     */
    public PackageScope forPackage(String name) {
        final String dottedName = name.replace('/', '.');
        Map<String, PackageScope> packages = mPackages;
        if (packages == null) {
            mPackages = packages = new HashMap<>();
        }
        return packages.computeIfAbsent(dottedName, k -> {
            var scope = new PackageScope(this, dottedName);
            return mDefaultRule == ALLOW ? scope.allowAll() : scope.denyAll(mDefaultRule);
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
        throws ClassNotFoundException, NoSuchMethodException
    {
        // FIXME: Validate inheritence when using caller checks. Also check
        // for @CallerSensitive methods, which must rely on caller checks.

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
        Map<String, ScopedRules.PackageScope> builtPackages;

        if (isEmpty(mPackages)) {
            builtPackages = null;
        } else {
            builtPackages = new HashMap<>();
            for (Map.Entry<String, PackageScope> e : mPackages.entrySet()) {
                ScopedRules.PackageScope scope = e.getValue().build(mDefaultRule);
                if (scope != null) {
                    builtPackages.put(e.getKey().replace('.', '/').intern(), scope);
                }
            }
        }

        return new ScopedRules(builtPackages, mDefaultRule);
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

        var paramTypes = new ArrayList<Class<?>>(4);

        for (int pos = 0; pos < descriptor.length(); ) {
            pos = addParamType(paramTypes, loader, descriptor, pos);
        }

        return paramTypes.toArray(Class<?>[]::new);
    }

    /**
     * @return updated pos
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
            if (isAccessible(c) && Arrays.equals(c.getParameterTypes(), paramTypes)) {
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
            if (isAccessible(m) && m.getName().equals(name)) {
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

    public static final class PackageScope {
        private final RulesBuilder mParent;
        private final String mName;

        // Can be null when empty.
        private Map<String, ClassScope> mClasses;

        // Default is selected when no map entry is found.
        private Rule mDefaultRule;

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
            return denyAll(TARGET_DENY);
        }

        /**
         * @param rule must be CALLER_DENY or TARGET_DENY
         * @return this
         */
        PackageScope denyAll(Rule rule) {
            mClasses = null;
            mDefaultRule = rule;
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
            mDefaultRule = ALLOW;
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
                return mDefaultRule == ALLOW ? scope.allowAll() : scope.denyAll(mDefaultRule);
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
            throws ClassNotFoundException, NoSuchMethodException
        {
            Objects.requireNonNull(loader);
            if (mClasses != null) {
                for (ClassScope cs : mClasses.values()) {
                    cs.validate(loader);
                }
            }
        }

        /**
         * @return null if redundant
         */
        private ScopedRules.PackageScope build(Rule parentRule) {
            if (isEmpty(mClasses) && parentRule == mDefaultRule) {
                return null;
            }

            Map<String, ScopedRules.ClassScope> builtClasses;

            if (isEmpty(mClasses)) {
                builtClasses = null;
            } else {
                builtClasses = new HashMap<>();
                for (Map.Entry<String, ClassScope> e : mClasses.entrySet()) {
                    ScopedRules.ClassScope scope = e.getValue().build(mDefaultRule);
                    if (scope != null) {
                        builtClasses.put(e.getKey().intern(), scope);
                    }
                }
            }

            return new ScopedRules.PackageScope(builtClasses, mDefaultRule);
        }
    }

    public static final class ClassScope {
        private final PackageScope mParent;
        private final String mName;

        // The current deny rule.
        private Rule mDenyRule;

        // Can be null when empty.
        private MethodScope mConstructors;

        // Default is selected when constructors is empty.
        private Rule mDefaultConstructorRule;

        // Can be null when empty.
        private Map<String, MethodScope> mMethods;

        // Default is selected when no method map entry is found.
        private Rule mDefaultMethodRule;

        // Is set when a variant rule can be specified.
        private MethodScope mVariantScope;

        private ClassScope(PackageScope parent, String name) {
            mParent = parent;
            mName = name;
            mDenyRule = TARGET_DENY;
        }

        /**
         * Indicate that access checking code should be generated in the caller class. By
         * default, access checks are performed in the target class. Checking in the caller is
         * more efficient, but it doesn't work reliably against a method which is inherited or
         * can be inherited.
         *
         * @return this
         */
        public ClassScope callerCheck() {
            mDenyRule = CALLER_DENY;
            return this;
        }

        /**
         * Indicate that access checking code should be generated in the target class, which is
         * the default behavior. Checking in the target is less efficient, but the check isn't
         * affected by inheritence.
         *
         * @return this
         */
        public ClassScope targetCheck() {
            mDenyRule = TARGET_DENY;
            return this;
        }

        /**
         * Deny access to all constructors, and methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAll() {
            return denyAll(mDenyRule);
        }

        /**
         * @param rule must be CALLER_DENY or TARGET_DENY
         * @return this
         */
        ClassScope denyAll(Rule rule) {
            mConstructors = null;
            mDefaultConstructorRule = rule;
            mMethods = null;
            mDefaultMethodRule = rule;
            mVariantScope = null;
            return this;
        }

        /**
         * Deny access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllConstructors() {
            mConstructors = null;
            mDefaultConstructorRule = mDenyRule;
            mVariantScope = mConstructors = new MethodScope().denyAll(mDenyRule);
            return this;
        }

        /**
         * Deny access to all methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllMethods() {
            mMethods = null;
            mDefaultMethodRule = mDenyRule;
            mVariantScope = null;
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
            mVariantScope = forMethod(name).denyAll(mDenyRule);
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
            mVariantScope.allowVariant(descriptor);
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
            return allowVariant(partialDescriptorFor(paramTypes));
        }

        /**
         * Allow access to all constructors, and methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAll() {
            mConstructors = null;
            mDefaultConstructorRule = ALLOW;
            mMethods = null;
            mDefaultMethodRule = ALLOW;
            mVariantScope = null;
            return this;
        }

        /**
         * Allow access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAllConstructors() {
            mConstructors = null;
            mDefaultConstructorRule = ALLOW;
            mVariantScope = mConstructors = new MethodScope().allowAll();
            return this;
        }

        /**
         * Allow access to all methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAllMethods() {
            mMethods = null;
            mDefaultMethodRule = ALLOW;
            mVariantScope = null;
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
            mVariantScope.denyVariant(mDenyRule, descriptor);
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
            return denyVariant(partialDescriptorFor(paramTypes));
        }

        /**
         * End the current rules for this class and return to the package scope. More rules can
         * be added to the scope later if desired.
         */
        public PackageScope end() {
            mVariantScope = null;
            return mParent;
        }

        /**
         * Validates that all classes are loadable, and that all class members are found.
         *
         * @param loader required
         * @throws IllegalStateException if validation fails
         */
        public void validate(ClassLoader loader)
            throws ClassNotFoundException, NoSuchMethodException
        {
            Objects.requireNonNull(loader);

            String className = mName;
            String pkg = mParent.mName;
            if (!pkg.isEmpty()) {
                className = pkg.replace('/', '.') + '.' + className;
            }

            Class<?> clazz = loader.loadClass(className);

            if (mConstructors != null) {
                mConstructors.validateConstuctor(loader, clazz);
            }

            if (mMethods != null) {
                for (Map.Entry<String, MethodScope> e : mMethods.entrySet()) {
                    e.getValue().validateMethod(loader, clazz, e.getKey());
                }
            }
        }

        /**
         * Caller must call allowAll or denyAll on the returned MethodScope.
         */
        private MethodScope forMethod(String name) {
            Map<String, MethodScope> methods = mMethods;
            if (methods == null) {
                mMethods = methods = new HashMap<>();
            }
            return methods.computeIfAbsent(name, _ -> new MethodScope());
        }

        /**
         * @return null if redundant
         */
        private ScopedRules.ClassScope build(Rule parentRule) {
            if (mConstructors == null && isEmpty(mMethods) &&
                parentRule == mDefaultConstructorRule && parentRule == mDefaultMethodRule)
            {
                return null;
            }

            ScopedRules.MethodScope builtConstructors;

            if (mConstructors == null) {
                builtConstructors = null;
            } else {
                builtConstructors = mConstructors.build(mDefaultConstructorRule);
            }

            Map<String, ScopedRules.MethodScope> builtMethods;

            if (isEmpty(mMethods)) {
                builtMethods = null;
            } else {
                builtMethods = new HashMap<>();
                for (Map.Entry<String, MethodScope> e : mMethods.entrySet()) {
                    ScopedRules.MethodScope scope = e.getValue().build(mDefaultMethodRule);
                    if (scope != null) {
                        builtMethods.put(e.getKey().intern(), scope);
                    }
                }
            }

            return new ScopedRules.ClassScope(builtConstructors, mDefaultConstructorRule,
                                              builtMethods, mDefaultMethodRule); 
        }
    }

    private static final class MethodScope {
        // Can be null when empty.
        Map<String, Rule> mVariants;

        // Default is selected when no map entry is found.
        Rule mDefaultRule;

        private MethodScope() {
        }

        boolean isAllDenied() {
            return isEmpty(mVariants)
                && (mDefaultRule == CALLER_DENY || mDefaultRule == TARGET_DENY);
        }

        /**
         * Deny access to all variants, superseding all previous rules.
         *
         * @param rule must be CALLER_DENY or TARGET_DENY
         * @return this
         */
        MethodScope denyAll(Rule rule) {
            mVariants = null;
            mDefaultRule = rule;
            return this;
        }

        /**
         * @param rule must be CALLER_DENY or TARGET_DENY
         * @return this
         */
         MethodScope denyVariant(Rule rule, String descriptor) {
            return variantAction(descriptor, rule);
        }

        boolean isAllAllowed() {
            return isEmpty(mVariants) && mDefaultRule == ALLOW;
        }

        /**
         * Allow access to all variants, superseding all previous rules.
         *
         * @return this
         */
        MethodScope allowAll() {
            mVariants = null;
            mDefaultRule = ALLOW;
            return this;
        }

        MethodScope allowVariant(String descriptor) {
            return variantAction(descriptor, ALLOW);
        }

        private MethodScope variantAction(String descriptor, Rule rule) {
            descriptor = descriptor.replace('.', '/');

            Map<String, Rule> variants = mVariants;

            if (variants == null) {
                if (rule == mDefaultRule) {
                    return this;
                }
                mVariants = variants = new HashMap<>();
            }

            if (rule == mDefaultRule) {
                variants.remove(descriptor);
            } else {
                variants.put(descriptor.intern(), rule);
            }

            return this;
        }

        void validateConstuctor(ClassLoader loader, Class<?> clazz)
            throws ClassNotFoundException, NoSuchMethodException
        {
            if (isEmpty(mVariants)) {
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
            if (isEmpty(mVariants)) {
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

        /**
         * @return null if redundant
         */
        private ScopedRules.MethodScope build(Rule parentRule) {
            if (isEmpty(mVariants) && mDefaultRule == parentRule) {
                return null;
            }
            return new ScopedRules.MethodScope(mVariants, mDefaultRule);
        }
    }
}
