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

import java.lang.annotation.Annotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import java.util.function.Consumer;

import static org.cojen.boxtin.Rule.*;
import static org.cojen.boxtin.Utils.*;

/**
 * Is used to build up a set of access rules, which can then be {@link #build built} into a
 * usable {@link Rules} instance.
 *
 * @author Brian S. O'Neill
 */
public final class RulesBuilder {
    // Can be null when empty.
    private Map<String, ModuleScope> mModules;

    // Default is selected when no map entry is found.
    private Rule mDefaultRule;

    private Rules mBuiltRules;

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
        return ruleForAll(denyAtTarget());
    }

    /**
     * Allow access to all packages, superseding all previous rules. This action is
     * recursive, allowing access to all packages, classes, constructors, etc.
     *
     * @return this
     */
    public RulesBuilder allowAll() {
        return ruleForAll(allow());
    }

    RulesBuilder ruleForAll(Rule rule) {
        modified();
        mModules = null;
        mDefaultRule = rule;
        return this;
    }

    /**
     * Define specific rules against the given module, which can supersede all previous rules.
     *
     * @param name fully qualified module name
     */
    public ModuleScope forModule(String name) {
        modified();
        Objects.requireNonNull(name);
        Map<String, ModuleScope> modules = mModules;
        if (modules == null) {
            mModules = modules = new HashMap<>();
        }
        return modules.computeIfAbsent(name, k -> {
            return new ModuleScope(this, name).ruleForAll(mDefaultRule);
        });
    }

    /**
     * Define specific rules against the given module, which can supersede all previous rules.
     */
    public ModuleScope forModule(Module module) {
        return forModule(module.getName());
    }

    /**
     * Validates that all classes are loadable, and that all class members are found. An
     * exception is thrown if validation fails.
     *
     * @param layer required
     * @return this
     * @throws NullPointerException if layer is null
     * @throws IllegalStateException if validation fails
     */
    public RulesBuilder validate(ModuleLayer layer) {
        return validate(layer, null);
    }

    /**
     * Validates that all classes are loadable, and that all class members are found. An
     * exception is thrown if validation fails.
     *
     * @param layer required
     * @param reporter pass non-null for reporting multiple validation failures
     * @return this
     * @throws NullPointerException if layer is null
     * @throws IllegalStateException if validation fails
     */
    public RulesBuilder validate(ModuleLayer layer, Consumer<String> reporter) {
        return validate(layer, reporter, false);
    }

    /**
     * Validates that all classes are loadable, and that all class members are found. This
     * variant also examines the entire class hierarchy, for validating caller-side rules. An
     * exception is thrown if validation fails.
     *
     * @param layer required
     * @param reporter pass non-null for reporting multiple validation failures
     * @return this
     * @throws NullPointerException if layer is null
     * @throws IllegalStateException if validation fails
     */
    public RulesBuilder validateDeep(ModuleLayer layer, Consumer<String> reporter) {
        return validate(layer, reporter, true);
    }

    private RulesBuilder validate(ModuleLayer layer, Consumer<String> reporter, boolean deep) {
        Objects.requireNonNull(layer);

        var actualReporter = new Consumer<String>() {
            String firstMessage;

            public void accept(String message) {
                if (reporter == null) {
                    throw new IllegalStateException(message);
                }
                if (firstMessage == null) {
                    firstMessage = message;
                }
                reporter.accept(message);
            }
        };

        if (mModules != null) {
            var packageNames = new HashSet<String>();

            for (ModuleScope ms : mModules.values()) {
                ms.preValidate(packageNames, actualReporter);
            }

            for (ModuleScope ms : mModules.values()) {
                ms.validate(layer, actualReporter);
            }

            if (deep) {
                try {
                    Map<String, ClassInfo> allClasses = ClassInfo.decodeModules(mModules.keySet());
                    Rules rules = build();
                    for (ClassInfo ci : allClasses.values()) {
                        ci.validate(rules, actualReporter);
                    }
                } catch (IOException e) {
                    actualReporter.accept(e.toString());
                }
            }
        }

        String message = actualReporter.firstMessage;
        if (message != null) {
            throw new IllegalStateException(message);
        }

        return this;
    }

    /**
     * Returns an immutable set of rules based on what's been defined so far.
     *
     * @throws IllegalStateException if a package is defined in multiple modules
     */
    public Rules build() {
        if (mBuiltRules != null) {
            return mBuiltRules;
        }

        Map<String, RuleSet.PackageScope> builtPackages;

        if (isEmpty(mModules)) {
            builtPackages = null;
        } else {
            builtPackages = new HashMap<>();
            for (ModuleScope ms  : mModules.values()) {
                ms.buildInto(builtPackages);
            }
        }

        return mBuiltRules = new RuleSet(builtPackages, mDefaultRule);
    }

    void modified() {
        mBuiltRules = null;
    }

    private static String nameFor(Class<?> clazz) {
        String name = clazz.getSimpleName();
        Class<?> enclosing = clazz.getEnclosingClass();
        return enclosing == null ? name : nameFor(enclosing) + '.' + name;
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
            char c = descriptor.charAt(pos);
            if (c == '(') {
                pos++;
                c = descriptor.charAt(pos);
            }
            if (c == ')') {
                break;
            }
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
                    type = Class.forName(name, false, loader);
                    pos = end;
                }
            }

            paramTypes.add(type);
            return pos + 1;
        }

        throw new NoSuchMethodException("Invalid descriptor: " + descriptor);
    }

    private static Constructor<?> tryFindConstructor(Class<?> clazz, Class<?>... paramTypes) {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (isAccessible(c) && Arrays.equals(c.getParameterTypes(), paramTypes)) {
                return c;
            }
        }

        return null;
    }

    private static Method tryFindMethod(boolean checkInherited,
                                        Class<?> clazz, String name, Class<?>... paramTypes)
    {
        for (Method m : clazz.getDeclaredMethods()) {
            if (isAccessible(m) && m.getName().equals(name) &&
                Arrays.equals(m.getParameterTypes(), paramTypes))
            {
                return m;
            }
        }

        if (!checkInherited) {
            return null;
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            Method m = tryFindMethod(true, superclass, name, paramTypes);
            if (m != null) {
                return m;
            }
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            Method m = tryFindMethod(true, iface, name, paramTypes);
            if (m != null) {
                return m;
            }
        }

        return null;
    }

    private static int forAllMethods(boolean checkInherited, boolean allowAbstract,
                                     Class<?> clazz, String name, Consumer<Method> consumer)
    {
        int count = 0;

        for (Method m : clazz.getDeclaredMethods()) {
            if ((allowAbstract || !Modifier.isAbstract(m.getModifiers())) &&
                (isAccessible(m) && m.getName().equals(name)))
            {
                count++;
                consumer.accept(m);
            }
        }

        if (checkInherited) {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null) {
                count += forAllMethods(true, allowAbstract, superclass, name, consumer);
            }
            for (Class<?> iface : clazz.getInterfaces()) {
                count += forAllMethods(true, allowAbstract, iface, name, consumer);
            }
        }

        return count;
    }

    /**
     * Builder of rules at the module level.
     */
    public static final class ModuleScope {
        private final RulesBuilder mParent;
        private final String mName;

        // Can be null when empty.
        private Map<String, PackageScope> mPackages;

        // Default is selected when no map entry is found.
        private Rule mDefaultRule;

        private ModuleScope(RulesBuilder parent, String name) {
            mParent = parent;
            mName = name;
        }

        /**
         * Deny access to all packages, superseding all previous rules. This action is
         * recursive, denying access to all classes, constructors, etc.
         *
         * @return this
         */
        public ModuleScope denyAll() {
            return ruleForAll(denyAtTarget());
        }

        /**
         * Deny access to all packages, superseding all previous rules. This action is
         * recursive, denying access to all classes, constructors, etc.
         *
         * @return this
         */
        public ModuleScope denyAll(DenyAction action) {
            return ruleForAll(denyAtTarget(action));
        }

        /**
         * Allow access to all packages, superseding all previous rules. This action is
         * recursive, allowing access to all classes, constructors, etc.
         *
         * @return this
         */
        public ModuleScope allowAll() {
            return ruleForAll(allow());
        }

        /**
         * @return this
         */
        ModuleScope ruleForAll(Rule rule) {
            modified();
            mPackages = null;
            mDefaultRule = rule;
            return this;
        }

        /**
         * Define specific rules against the given package, which can supersede all previous
         * rules.
         *
         * @param name fully qualified package name
         */
        public PackageScope forPackage(String name) {
            modified();
            final String dottedName = name.replace('/', '.');
            Map<String, PackageScope> packages = mPackages;
            if (packages == null) {
                mPackages = packages = new HashMap<>();
            }
            return packages.computeIfAbsent(dottedName, k -> {
                return new PackageScope(this, dottedName).ruleForAll(mDefaultRule);
            });
        }

        /**
         * Define specific rules against the given module, which can supersede all previous
         * rules.
         */
        public PackageScope forPackage(Package p) {
            return forPackage(p.getName());
        }

        /**
         * End the current rules for this module and return to the outermost scope. More rules
         * can be added to the scope later if desired.
         */
        public RulesBuilder end() {
            return mParent;
        }

        /**
         * End the current rules for this module and begin a new module scope. More rules can
         * be added to the scope later if desired.
         */
        public ModuleScope forModule(String name) {
            return end().forModule(name);
        }

        /**
         * End the current rules for this module and begin a new module scope. More rules can
         * be added to the scope later if desired.
         */
        public ModuleScope forModule(Module module) {
            return end().forModule(module);
        }

        void preValidate(Set<String> packageNames, Consumer<String> reporter) {
            for (String name : mPackages.keySet()) {
                if (!packageNames.add(name)) {
                    reporter.accept("Package is defined in multiple modules: " + name);
                }
            }
        }

        /**
         * Validates that all classes are loadable, and that all class members are found.
         *
         * @param layer required
         */
        void validate(ModuleLayer layer, Consumer<String> reporter) {
            Module module = layer.findModule(mName).orElse(null);

            if (module == null) {
                reporter.accept("Module isn't found: " + mName);
                return;
            }

            ClassLoader loader;
            try {
                loader = layer.findLoader(mName);
            } catch (IllegalArgumentException e) {
                reporter.accept("Module isn't found: " + mName);
                return;
            }

            Set<String> packages = module.getPackages();

            if (mPackages != null) {
                for (PackageScope ps : mPackages.values()) {
                    ps.validate(packages, loader, reporter);
                }
            }
        }

        void modified() {
            mParent.modified();
        }

        private void buildInto(Map<String, RuleSet.PackageScope> builtPackages) {
            for (Map.Entry<String, PackageScope> e : mPackages.entrySet()) {
                RuleSet.PackageScope scope = e.getValue().build(mDefaultRule);
                if (scope != null) {
                    String key = e.getKey().replace('.', '/').intern();
                    if (builtPackages.putIfAbsent(key, scope) != null) {
                        throw new IllegalStateException
                            ("Package is defined in multiple modules: " + e.getKey());
                    }
                }
            }
        }
    }

    /**
     * Builder of rules at the package level.
     */
    public static final class PackageScope {
        private final ModuleScope mParent;
        private final String mName;

        // Can be null when empty.
        private Map<String, ClassScope> mClasses;

        // Default is selected when no map entry is found.
        private Rule mDefaultRule;

        private PackageScope(ModuleScope parent, String name) {
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
            return ruleForAll(denyAtTarget());
        }

        /**
         * Deny access to all classes, superseding all previous rules. This action is
         * recursive, denying access to all classes, constructors, etc.
         *
         * @return this
         */
        public PackageScope denyAll(DenyAction action) {
            return ruleForAll(denyAtTarget(action));
        }

        /**
         * Allow access to all classes, superseding all previous rules. This action is
         * recursive, allowing access to all classes, constructors, etc.
         *
         * @return this
         */
        public PackageScope allowAll() {
            return ruleForAll(allow());
        }

        /**
         * @return this
         */
        PackageScope ruleForAll(Rule rule) {
            modified();
            mClasses = null;
            mDefaultRule = rule;
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
            modified();
            final String vmName = name.replace('.', '$');
            Map<String, ClassScope> classes = mClasses;
            if (classes == null) {
                mClasses = classes = new HashMap<>();
            }
            return classes.computeIfAbsent(vmName, k -> {
                return new ClassScope(this, vmName).ruleForAll(mDefaultRule);
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
        public ModuleScope end() {
            return mParent;
        }

        /**
         * End the current rules for this package and begin a new package scope. More rules can
         * be added to the scope later if desired.
         */
        public PackageScope forPackage(String name) {
            return end().forPackage(name);
        }

        /**
         * End the current rules for this package and begin a new package scope. More rules can
         * be added to the scope later if desired.
         */
        public PackageScope forPackage(Package p) {
            return end().forPackage(p);
        }

        /**
         * End the current rules for this package and module, and begin a new module scope.
         * More rules can be added to the scope later if desired.
         */
        public ModuleScope forModule(String name) {
            return end().forModule(name);
        }

        /**
         * End the current rules for this package and module, and begin a new module scope.
         * More rules can be added to the scope later if desired.
         */
        public ModuleScope forModule(Module module) {
            return end().forModule(module);
        }

        /**
         * Validates that all classes are loadable, and that all class members are found.
         */
        void validate(Set<String> packages, ClassLoader loader, Consumer<String> reporter) {
            if (!packages.contains(mName)) {
                reporter.accept("Package isn't found: " + mParent.mName + '/' + mName);
                return;
            }

            if (mClasses != null) {
                for (ClassScope cs : mClasses.values()) {
                    cs.validate(loader, reporter);
                }
            }
        }

        void modified() {
            mParent.modified();
        }

        /**
         * @return null if redundant
         */
        private RuleSet.PackageScope build(Rule parentRule) {
            if (isEmpty(mClasses) && parentRule.equals(mDefaultRule)) {
                return null;
            }

            Map<String, RuleSet.ClassScope> builtClasses;

            if (isEmpty(mClasses)) {
                builtClasses = null;
            } else {
                builtClasses = new HashMap<>();
                for (Map.Entry<String, ClassScope> e : mClasses.entrySet()) {
                    RuleSet.ClassScope scope = e.getValue().build(mDefaultRule);
                    if (scope != null) {
                        builtClasses.put(e.getKey().intern(), scope);
                    }
                }
            }

            return new RuleSet.PackageScope(builtClasses, mDefaultRule);
        }
    }

    /**
     * Builder of rules at the class level.
     */
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
            mDenyRule = denyAtTarget();
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
            mDenyRule = denyAtCaller();
            return this;
        }

        /**
         * Indicate that access checking code should be generated in the target class, which is
         * the default behavior. Checking in the target is less efficient, but the check isn't
         * affected by inheritance.
         *
         * @return this
         */
        public ClassScope targetCheck() {
            mDenyRule = denyAtTarget();
            return this;
        }

        /**
         * Deny access to all constructors and locally declared methods, superseding all
         * previous rules.
         *
         * @return this
         */
        public ClassScope denyAll() {
            return ruleForAll(mDenyRule);
        }

        /**
         * Deny access to all constructors and locally declared methods, superseding all
         * previous rules.
         *
         * @return this
         */
        public ClassScope denyAll(DenyAction action) {
            return ruleForAll(mDenyRule.withDenyAction(action));
        }

        /**
         * Deny access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllConstructors() {
            modified();
            mConstructors = null;
            mDefaultConstructorRule = mDenyRule;
            mVariantScope = mConstructors = new MethodScope().ruleForAll(mDenyRule);
            return this;
        }

        /**
         * Deny access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllConstructors(DenyAction action) {
            modified();
            mConstructors = null;
            Rule rule = mDenyRule.withDenyAction(action);
            mDefaultConstructorRule = rule;
            mVariantScope = mConstructors = new MethodScope().ruleForAll(rule);
            return this;
        }

        /**
         * Deny access to all locally declared methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllMethods() {
            modified();
            mMethods = null;
            mDefaultMethodRule = mDenyRule;
            mVariantScope = null;
            return this;
        }

        /**
         * Deny access to all locally declared methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllMethods(DenyAction action) {
            modified();
            mMethods = null;
            mDefaultMethodRule = mDenyRule.withDenyAction(action);
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
            mVariantScope = forMethod(name).ruleForAll(mDenyRule);
            return this;
        }

        /**
         * Deny access to all variants of the given method, superseding all previous rules.
         *
         * @return this
         * @throws IllegalArgumentException if not a valid method name
         */
        public ClassScope denyMethod(DenyAction action, String name) {
            mVariantScope = forMethod(name).ruleForAll(mDenyRule.withDenyAction(action));
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
            modified();
            if (mVariantScope == null) {
                throw new IllegalStateException("No current constructor or method");
            }
            if (mVariantScope.isAllAllowed()) {
                throw new IllegalStateException("All variants are explicitly allowed");
            }
            mVariantScope.ruleForVariant(allow(), descriptor);
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
         * Allow access to all constructors and locally declared methods, superseding all
         * previous rules.
         *
         * @return this
         */
        public ClassScope allowAll() {
            return ruleForAll(allow());
        }

        /**
         * @return this
         */
        ClassScope ruleForAll(Rule rule) {
            modified();
            mConstructors = null;
            mDefaultConstructorRule = rule;
            mMethods = null;
            mDefaultMethodRule = rule;
            mVariantScope = null;
            return this;
        }

        /**
         * Allow access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAllConstructors() {
            modified();
            mConstructors = null;
            mDefaultConstructorRule = allow();
            mVariantScope = mConstructors = new MethodScope().allowAll();
            return this;
        }

        /**
         * Allow access to all locally declared methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAllMethods() {
            modified();
            mMethods = null;
            mDefaultMethodRule = allow();
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
            modified();
            if (mVariantScope == null) {
                throw new IllegalStateException("No current constructor or method");
            }
            if (mVariantScope.isAllDenied()) {
                throw new IllegalStateException("All variants are explicitly denied");
            }
            mVariantScope.ruleForVariant(mDenyRule, descriptor);
            return this;
        }

        /**
         * Deny access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @param descriptor descriptor for the parameters, not including parenthesis or the
         * return type
         * @return this
         * @throws IllegalStateException if no current constructor or method
         */
        public ClassScope denyVariant(DenyAction action, String descriptor) {
            modified();
            if (mVariantScope == null) {
                throw new IllegalStateException("No current constructor or method");
            }
            mVariantScope.ruleForVariant(mDenyRule.withDenyAction(action), descriptor);
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
         * Deny access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @return this
         * @throws IllegalStateException if no current constructor or method, or if all
         * variants are explicitly denied
         */
        public ClassScope denyVariant(DenyAction action, Class<?>... paramTypes) {
            return denyVariant(action, partialDescriptorFor(paramTypes));
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
         * End the current rules for this class and begin a new class scope. More rules can
         * be added to the scope later if desired.
         */
        public ClassScope forClass(String name) {
            return end().forClass(name);
        }

        /**
         * End the current rules for this class and begin a new class scope. More rules can
         * be added to the scope later if desired.
         */
        public ClassScope forClass(Class<?> clazz) {
            return end().forClass(clazz);
        }

        /**
         * End the current rules for this class and package, and begin a new package scope.
         * More rules can be added to the scope later if desired.
         */
        public PackageScope forPackage(String name) {
            return end().forPackage(name);
        }

        /**
         * End the current rules for this class and package, and begin a new package scope.
         * More rules can be added to the scope later if desired.
         */
        public PackageScope forPackage(Package p) {
            return end().forPackage(p);
        }

        /**
         * End the current rules for this class, package and module, and begin a new module
         * scope. More rules can be added to the scope later if desired.
         */
        public ModuleScope forModule(String name) {
            return end().forModule(name);
        }

        /**
         * End the current rules for this class, package and module, and begin a new module
         * scope. More rules can be added to the scope later if desired.
         */
        public ModuleScope forModule(Module module) {
            return end().forModule(module);
        }

        /**
         * Validates all class members.
         */
        void validate(ClassLoader loader, Consumer<String> reporter) {
            String className = mName;
            String pkg = mParent.mName;
            if (!pkg.isEmpty()) {
                className = pkg.replace('/', '.') + '.' + className;
            }

            Class<?> clazz;
            try {
                clazz = Class.forName(className, false, loader);
            } catch (ClassNotFoundException e) {
                reporter.accept(e.toString());
                return;
            }

            if (mConstructors != null) {
                mConstructors.validateConstructor(loader, clazz, reporter);
            }

            if (mMethods != null) {
                for (Map.Entry<String, MethodScope> e : mMethods.entrySet()) {
                    e.getValue().validateMethod(loader, clazz, e.getKey(), reporter);
                }
            }
        }

        void modified() {
            mParent.modified();
        }

        /**
         * Caller must call allowAll or denyAll on the returned MethodScope.
         */
        private MethodScope forMethod(String name) {
            modified();
            checkMethodName(name);
            Map<String, MethodScope> methods = mMethods;
            if (methods == null) {
                mMethods = methods = new HashMap<>();
            }
            return methods.computeIfAbsent(name, k -> new MethodScope());
        }

        /**
         * @return null if redundant
         */
        private RuleSet.ClassScope build(Rule parentRule) {
            if (mConstructors == null && isEmpty(mMethods) &&
                parentRule.equals(mDefaultConstructorRule) && parentRule.equals(mDefaultMethodRule))
            {
                return null;
            }

            RuleSet.MethodScope builtConstructors;

            if (mConstructors == null) {
                builtConstructors = null;
            } else {
                builtConstructors = mConstructors.build(mDefaultConstructorRule);
            }

            Map<String, RuleSet.MethodScope> builtMethods;

            if (isEmpty(mMethods)) {
                builtMethods = null;
            } else {
                builtMethods = new HashMap<>();
                for (Map.Entry<String, MethodScope> e : mMethods.entrySet()) {
                    RuleSet.MethodScope scope = e.getValue().build(mDefaultMethodRule);
                    if (scope != null) {
                        builtMethods.put(e.getKey().intern(), scope);
                    }
                }
            }

            return new RuleSet.ClassScope(builtConstructors, mDefaultConstructorRule,
                                          builtMethods, mDefaultMethodRule); 
        }
    }

    private static final class MethodScope {
        // Can be null when empty.
        NavigableMap<CharSequence, Rule> mVariants;

        // Default is selected when no map entry is found.
        Rule mDefaultRule;

        private MethodScope() {
        }

        boolean isAllDenied() {
            return isEmpty(mVariants) && mDefaultRule.isDenied();
        }

        boolean isAllAllowed() {
            return isEmpty(mVariants) && mDefaultRule.isAllowed();
        }

        /**
         * Allow access to all variants, superseding all previous rules.
         *
         * @return this
         */
        MethodScope allowAll() {
            return ruleForAll(allow());
        }

        /**
         * Apply a rule to all variants, superseding all previous rules.
         *
         * @return this
         */
        MethodScope ruleForAll(Rule rule) {
            mVariants = null;
            mDefaultRule = rule;
            return this;
        }

        /**
         * @return this
         */
        MethodScope ruleForVariant(Rule rule, String descriptor) {
            if (descriptor.isEmpty()) {
                descriptor = "()";
            } else {
                descriptor = descriptor.replace('.', '/');
                if (descriptor.charAt(0) != '(' &&
                    descriptor.charAt(descriptor.length() - 1) != ')')
                {
                    descriptor = '(' + descriptor + ')';
                }
            }

            NavigableMap<CharSequence, Rule> variants = mVariants;

            if (variants == null) {
                if (rule.equals(mDefaultRule)) {
                    return this;
                }
                mVariants = variants = new TreeMap<>(CharSequence::compare);
            }

            if (rule.equals(mDefaultRule)) {
                variants.remove(descriptor);
            } else {
                variants.put(descriptor.intern(), rule);
            }

            return this;
        }

        void validateConstructor(ClassLoader loader, Class<?> clazz, Consumer<String> reporter) {
            if (isEmpty(mVariants)) {
                int count = 0;

                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    if (!isAccessible(ctor)) {
                        continue;
                    }
                    count++;
                    validateConstructor(loader, ctor, mDefaultRule, reporter);
                }

                if (count == 0) {
                    reporter.accept("Constructor not found: " + clazz);
                }

                return;
            }

            for (Map.Entry<CharSequence, Rule> e : mVariants.entrySet()) {
                Class<?>[] paramTypes;
                try {
                    paramTypes = paramTypesFor(loader, e.getKey().toString());
                } catch (ClassNotFoundException | NoSuchMethodException ex) {
                    reporter.accept(ex.toString());
                    continue;
                }

                Constructor ctor;
                try {
                    ctor = clazz.getConstructor(paramTypes);
                } catch (NoSuchMethodException ex) {
                    ctor = tryFindConstructor(clazz, paramTypes);
                    if (ctor == null) {
                        reporter.accept(ex.toString());
                        continue;
                    }
                }

                validateConstructor(loader, ctor, e.getValue(), reporter);
            }
        }

        private static void validateConstructor(ClassLoader loader, Constructor ctor, Rule rule,
                                                Consumer<String> reporter)
        {
            DenyAction action = rule.denyAction();
            if (action == null) {
                return;
            }

            try {
                action.validateDependencies(loader);
            } catch (ClassNotFoundException e) {
                reporter.accept(e.toString());
            }

            action.validateParameters(ctor);
        }

        void validateMethod(ClassLoader loader, Class<?> clazz, String name,
                            Consumer<String> reporter)
        {
            if (isEmpty(mVariants)) {
                Rule rule = mDefaultRule;

                // If target-side, don't examine inherited and abstract methods, because no
                // code modifications can be made against them.
                boolean forTarget = rule.isDeniedAtTarget();

                int count = forAllMethods(!forTarget, !forTarget, clazz, name, method -> {
                    validateMethod(loader, method, rule, reporter);
                });

                if (count == 0) {
                    reporter.accept("Method not found: " + clazz + "." + name);
                }

                return;
            }

            for (Map.Entry<CharSequence, Rule> e : mVariants.entrySet()) {
                Class<?>[] paramTypes;
                try {
                    paramTypes = paramTypesFor(loader, e.getKey().toString());
                } catch (ClassNotFoundException | NoSuchMethodException ex) {
                    reporter.accept(ex.toString());
                    continue;
                }

                Rule rule = e.getValue();

                Method method;
                try {
                    method = clazz.getMethod(name, paramTypes);
                } catch (NoSuchMethodException ex) {
                    method = tryFindMethod(!rule.isDeniedAtTarget(), clazz, name, paramTypes);
                    if (method == null) {
                        reporter.accept(ex.toString());
                        continue;
                    }
                }

                validateMethod(loader, method, rule, reporter);
            }
        }

        private static void validateMethod(ClassLoader loader, Method method, Rule rule,
                                           Consumer<String> reporter)
        {
            DenyAction action = rule.denyAction();
            if (action == null) {
                return;
            }

            if (rule.isDeniedAtTarget()) {
                if (Modifier.isAbstract(method.getModifiers())) {
                    reporter.accept("Target method is abstract: " + method);
                }
                for (Annotation ann : method.getDeclaredAnnotations()) {
                    if (ann.annotationType().getName()
                        .equals("jdk.internal.reflect.CallerSensitive"))
                    {
                        reporter.accept("Target method is CallerSensitive and should instead " +
                                        "be caller-side checked: " + method);
                    }
                }
            }

            try {
                action.validateDependencies(loader);
            } catch (ClassNotFoundException e) {
                reporter.accept(e.toString());
            }

            action.validateReturnType(method);
            action.validateParameters(method);
        }

        /**
         * @return null if redundant
         */
        private RuleSet.MethodScope build(Rule parentRule) {
            if (isEmpty(mVariants) && mDefaultRule.equals(parentRule)) {
                return null;
            }
            return new RuleSet.MethodScope(mVariants, mDefaultRule);
        }
    }
}
