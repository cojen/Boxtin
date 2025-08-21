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

import java.lang.module.ModuleDescriptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final ModuleLayer mLayer;

    // Can be null when empty.
    private Map<String, ModuleScope> mModules;

    // Default is selected when no map entry is found.
    private Rule mDefaultRule;

    /**
     * Construct an instance which uses the boot module layer for discovering modules,
     * packages, and classes. By default, access to all modules is denied.
     */
    public RulesBuilder() {
        this(ModuleLayer.boot());
    }

    /**
     * Construct an instance which uses the given module layer for discovering modules,
     * packages, and classes. By default, access to all modules is denied.
     */
    public RulesBuilder(ModuleLayer layer) {
        mLayer = Objects.requireNonNull(layer);
        denyAll();
    }

    /**
     * Returns the {@code ModuleLayer} used by this builder.
     */
    public ModuleLayer moduleLayer() {
        return mLayer;
    }

    /**
     * Applies all the rules from the given applier to this builder.
     *
     * @return this
     */
    public RulesBuilder applyRules(RulesApplier applier) {
        applier.applyRulesTo(this);
        return this;
    }

    /**
     * Applies only the deny rules from the given applier to this builder.
     *
     * @return this
     */
    public RulesBuilder applyDenyRules(RulesApplier applier) {
        var other = new RulesBuilder(mLayer).allowAll().applyRules(applier);

        if (other.mModules != null) {
            for (Map.Entry<String, ModuleScope> e : other.mModules.entrySet()) {
                forModule(e.getKey()).applyDenyRules(e.getValue());
            }
        }

        if (other.mDefaultRule.isDenied()) {
            mDefaultRule = other.mDefaultRule;
        }

        return this;
    }

    /**
     * Applies only the allow rules from the given applier to this builder.
     *
     * @return this
     */
    public RulesBuilder applyAllowRules(RulesApplier applier) {
        var other = new RulesBuilder(mLayer).denyAll().applyRules(applier);

        if (other.mModules != null) {
            for (Map.Entry<String, ModuleScope> e : other.mModules.entrySet()) {
                forModule(e.getKey()).applyAllowRules(e.getValue());
            }
        }

        if (other.mDefaultRule.isAllowed()) {
            mDefaultRule = other.mDefaultRule;
        }

        return this;
    }

    /**
     * Deny access to all modules, superseding all previous rules. This action is recursive,
     * denying access to all packages, classes, constructors, etc.
     *
     * @return this
     */
    public RulesBuilder denyAll() {
        return ruleForAll(deny());
    }

    /**
     * Allow access to all modules, superseding all previous rules. This action is recursive,
     * allowing access to all packages, classes, constructors, etc.
     *
     * @return this
     */
    public RulesBuilder allowAll() {
        return ruleForAll(allow());
    }

    RulesBuilder ruleForAll(Rule rule) {
        mModules = null;
        mDefaultRule = rule;
        return this;
    }

    /**
     * Define specific rules against the given module, which can supersede all previous rules.
     *
     * @param name fully qualified module name
     * @throws IllegalArgumentException if the module isn't found
     */
    public ModuleScope forModule(String name) {
        return forModule(name, null, null);
    }

    /**
     * Define specific rules against the given module, which can supersede all previous rules.
     *
     * @param name fully qualified module name
     * @param minVersion optional minimum {@link ModuleDescriptor.Version module version
     * number} to support (inclusive)
     * @param maxVersion optional maximum {@link ModuleDescriptor.Version module version
     * number} to support (exclusive)
     * @throws IllegalArgumentException if the module isn't found, or if the version is out of
     * bounds, or if the min/max versions provided aren't parseable
     */
    public ModuleScope forModule(String name, String minVersion, String maxVersion) {
        Objects.requireNonNull(name);
        Module module = mLayer.findModule(name).orElseThrow(IllegalArgumentException::new);

        if (minVersion != null || maxVersion != null) {
            ModuleDescriptor.Version version = module.getDescriptor().version().orElse(null);
            if (version != null) {
                // Only consider the "version number" component.
                version = parseVersion(version.toString());

                if (minVersion != null) {
                    ModuleDescriptor.Version minv = parseVersion(minVersion);
                    if (version.compareTo(minv) < 0) {
                        throw new IllegalArgumentException
                            ("Version number for module " + name + " is too low: " +
                             version + " < " + minv);
                    }
                }

                if (maxVersion != null) {
                    ModuleDescriptor.Version maxv = parseVersion(maxVersion);
                    if (version.compareTo(maxv) >= 0) {
                        throw new IllegalArgumentException
                            ("Version number for module " + name + " is too high: " +
                             version + " >= " + maxv);
                    }
                }
            }
        }

        Map<String, ModuleScope> modules = mModules;
        if (modules == null) {
            mModules = modules = new LinkedHashMap<>();
        }

        return modules.computeIfAbsent(name, k -> {
            return new ModuleScope(this, module).ruleForAll(mDefaultRule);
        });
    }

    private static ModuleDescriptor.Version parseVersion(String str) {
        int ix = str.indexOf('-');
        if (ix > 0) {
            int ix2 = str.indexOf('+', ix);
            if (ix2 >= 0) {
                ix = Math.min(ix, ix2);
            }
        }
        if (ix >= 0) {
            str = str.substring(0, ix);
        }

        return ModuleDescriptor.Version.parse(str);
    }

    /**
     * Validates that all classes are loadable, and that all class members are found. An
     * exception is thrown if validation fails.
     *
     * @return this
     * @throws IllegalStateException if validation fails
     */
    public RulesBuilder validate() {
        return validate(null);
    }

    /**
     * Validates that all classes are loadable, and that all class members are found. An
     * exception is thrown if validation fails.
     *
     * @param reporter pass non-null for reporting multiple validation failures
     * @return this
     * @throws IllegalStateException if validation fails
     */
    public RulesBuilder validate(Consumer<String> reporter) {
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
                ms.validate(actualReporter);
            }
        }

        String message = actualReporter.firstMessage;
        if (message != null) {
            throw new IllegalStateException(message);
        }

        return this;
    }

    /**
     * @param executable is null if not applicable
     */
    private static void validateRuleAction(Rule rule, ClassLoader loader, Executable executable,
                                           Consumer<String> reporter)
    {
        DenyAction action = rule.denyAction();
        if (action != null) {
            try {
                action.validate(loader, executable);
            } catch (Exception e) {
                reporter.accept(e.toString());
            }
        }        
    }

    /**
     * Returns an immutable set of rules based on what's been defined so far.
     *
     * @throws IllegalStateException if a package is defined in multiple modules
     */
    public Rules build() {
        Map<String, RuleSet.PackageScope> packageScopes;

        if (isEmpty(mModules)) {
            packageScopes = Map.of();
        } else {
            Map<String, Module> packageToModule = PackageToModule.packageMapFor(mLayer);
            packageScopes = new LinkedHashMap<>();
            for (ModuleScope ms : mModules.values()) {
                ms.buildIntoPackageScopes(packageScopes, packageToModule);
            }
        }

        return new RuleSet(mLayer, packageScopes, mDefaultRule);
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

    private static int forAllConstructors(Class<?> clazz, Consumer<Constructor<?>> consumer) {
        int count = 0;

        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (isAccessible(c)) {
                count++;
                consumer.accept(c);
            }
        }

        return count;
    }

    private static int forAllMethods(Class<?> clazz, String name, Consumer<Method> consumer) {
        return forAllMethods(clazz, name, true, consumer);
    }

    private static int forAllMethods(Class<?> clazz, String name, boolean includeStatics,
                                     Consumer<Method> consumer)
    {
        int count = 0;

        for (Method m : clazz.getDeclaredMethods()) {
            if ((includeStatics || !Modifier.isStatic(m.getModifiers())) &&
                isAccessible(m) && m.getName().equals(name))
            {
                count++;
                consumer.accept(m);
            }
        }

        // Now do the inherited methods.

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            count += forAllMethods(superclass, name, true, consumer);
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            // Note that inherited statics are ignored, because static interface methods aren't
            // really inherited.
            count += forAllMethods(iface, name, false, consumer);
        }

        return count;
    }

    /**
     * Builder of rules at the module level.
     */
    public static final class ModuleScope {
        private final RulesBuilder mParent;
        private final Module mModule;

        // Can be null when empty.
        private Map<String, PackageScope> mPackages;

        // Default is selected when no map entry is found.
        private Rule mDefaultRule;

        private ModuleScope(RulesBuilder parent, Module module) {
            mParent = parent;
            mModule = module;
        }

        /**
         * Deny access to all packages, superseding all previous rules. This action is
         * recursive, denying access to all classes, constructors, etc.
         *
         * @return this
         */
        public ModuleScope denyAll() {
            return ruleForAll(deny());
        }

        /**
         * Deny access to all packages, superseding all previous rules. This action is
         * recursive, denying access to all classes, constructors, etc.
         *
         * @return this
         */
        public ModuleScope denyAll(DenyAction action) {
            return ruleForAll(deny(action));
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
            mPackages = null;
            mDefaultRule = rule;

            if (rule.isAllowed()) {
                // Need to expand all the packages, given that the module associations aren't
                // known when classes are transformed.

                for (String packageName : mModule.getPackages()) {
                    if (mModule.isExported(packageName)) {
                        forPackage(packageName);
                    }
                }
            }

            return this;
        }

        /**
         * Define specific rules against the given package, which can supersede all previous
         * rules.
         *
         * @param name fully qualified package name
         * @throws IllegalArgumentException if the package name isn't found
         */
        public PackageScope forPackage(String name) {
            if (!mModule.getPackages().contains(name)) {
                throw new IllegalArgumentException();
            }
            return doForPackage(name.replace('.', '/').intern());
        }

        private PackageScope doForPackage(String slashName) {
            Map<String, PackageScope> packages = mPackages;
            if (packages == null) {
                mPackages = packages = new LinkedHashMap<>();
            }
            return packages.computeIfAbsent(slashName, k -> {
                return new PackageScope(this, slashName).ruleForAll(mDefaultRule);
            });
        }

        /**
         * End the current rules for this module and return to the outermost scope. More rules
         * can be added to this scope later if desired.
         */
        public RulesBuilder end() {
            return mParent;
        }

        /**
         * End the current rules for this module and begin a new module scope. More rules can
         * be added to the scope later if desired.
         *
         * @param name fully qualified module name
         * @throws IllegalArgumentException if the module isn't found
         */
        public ModuleScope forModule(String name) {
            return ModuleScope.this.forModule(name, null, null);
        }

        /**
         * End the current rules for this module and begin a new module scope. More rules can
         * be added to the scope later if desired.
         *
         * @param name fully qualified module name
         * @param minVersion optional minimum {@link ModuleDescriptor.Version module version
         * number} to support (inclusive)
         * @param maxVersion optional maximum {@link ModuleDescriptor.Version module version
         * number} to support (exclusive)
         * @throws IllegalArgumentException if the module isn't found, or if the version is out of
         * bounds, or if the min/max versions provided aren't parseable
         */
        public ModuleScope forModule(String name, String minVersion, String maxVersion) {
            return end().forModule(name, minVersion, maxVersion);
        }

        void applyDenyRules(ModuleScope other) {
            if (other.mPackages != null) {
                for (Map.Entry<String, PackageScope> e : other.mPackages.entrySet()) {
                    doForPackage(e.getKey()).applyDenyRules(e.getValue());
                }
            }

            if (other.mDefaultRule.isDenied()) {
                mDefaultRule = other.mDefaultRule;
            }
        }

        void applyAllowRules(ModuleScope other) {
            if (other.mPackages != null) {
                for (Map.Entry<String, PackageScope> e : other.mPackages.entrySet()) {
                    doForPackage(e.getKey()).applyAllowRules(e.getValue());
                }
            }

            if (other.mDefaultRule.isAllowed()) {
                mDefaultRule = other.mDefaultRule;
            }
        }

        void preValidate(Set<String> packageNames, Consumer<String> reporter) {
            if (mPackages != null) {
                for (String name : mPackages.keySet()) {
                    if (!packageNames.add(name)) {
                        reporter.accept("Package is defined in multiple modules: " + name);
                    }
                }
            }
        }

        /**
         * Validates that all classes are loadable, and that all class members are found.
         */
        void validate(Consumer<String> reporter) {
            ClassLoader loader;
            try {
                loader = mParent.mLayer.findLoader(mModule.getName());
            } catch (IllegalArgumentException e) {
                reporter.accept("Module loader isn't found: " + mModule.getName());
                return;
            }

            validateRuleAction(mDefaultRule, loader, null, reporter);

            Set<String> packages = mModule.getPackages();

            if (mPackages != null) {
                for (PackageScope ps : mPackages.values()) {
                    ps.validate(packages, loader, reporter);
                }
            }
        }

        private void buildIntoPackageScopes(Map<String, RuleSet.PackageScope> packageScopes,
                                            Map<String, Module> packageToModule)
        {
            if (mPackages != null) {
                for (Map.Entry<String, PackageScope> e : mPackages.entrySet()) {
                    RuleSet.PackageScope scope = e.getValue().build(mModule, packageToModule);
                    packageScopes.put(scope.name(), scope);
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
            return ruleForAll(deny());
        }

        /**
         * Deny access to all classes, superseding all previous rules. This action is
         * recursive, denying access to all classes, constructors, etc.
         *
         * @return this
         */
        public PackageScope denyAll(DenyAction action) {
            return ruleForAll(deny(action));
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
            final String vmName = name.replace('.', '$');
            Map<String, ClassScope> classes = mClasses;
            if (classes == null) {
                mClasses = classes = new LinkedHashMap<>();
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
            if (!mName.replace('/', '.').equals(clazz.getPackageName())) {
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
         * End the current rules for this package and return to the module scope. More rules
         * can be added to this scope later if desired.
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
         * End the current rules for this package and module, and begin a new module scope.
         * More rules can be added to the scope later if desired.
         *
         * @param name fully qualified module name
         * @throws IllegalArgumentException if the module isn't found
         */
        public ModuleScope forModule(String name) {
            return PackageScope.this.forModule(name, null, null);
        }

        /**
         * End the current rules for this package and module, and begin a new module scope.
         * More rules can be added to the scope later if desired.
         *
         * @param name fully qualified module name
         * @param minVersion optional minimum {@link ModuleDescriptor.Version module version
         * number} to support (inclusive)
         * @param maxVersion optional maximum {@link ModuleDescriptor.Version module version
         * number} to support (exclusive)
         * @throws IllegalArgumentException if the module isn't found, or if the version is out of
         * bounds, or if the min/max versions provided aren't parseable
         */
        public ModuleScope forModule(String name, String minVersion, String maxVersion) {
            return end().forModule(name, minVersion, maxVersion);
        }

        void applyDenyRules(PackageScope other) {
            if (other.mClasses != null) {
                for (Map.Entry<String, ClassScope> e : other.mClasses.entrySet()) {
                    forClass(e.getKey()).applyDenyRules(e.getValue());
                }
            }

            if (other.mDefaultRule.isDenied()) {
                mDefaultRule = other.mDefaultRule;
            }
        }

        void applyAllowRules(PackageScope other) {
            if (other.mClasses != null) {
                for (Map.Entry<String, ClassScope> e : other.mClasses.entrySet()) {
                    forClass(e.getKey()).applyAllowRules(e.getValue());
                }
            }

            if (other.mDefaultRule.isAllowed()) {
                mDefaultRule = other.mDefaultRule;
            }
        }

        /**
         * Validates that all classes are loadable, and that all class members are found.
         */
        void validate(Set<String> packages, ClassLoader loader, Consumer<String> reporter) {
            validateRuleAction(mDefaultRule, loader, null, reporter);

            String dottedName = mName.replace('/', '.');

            if (!packages.contains(dottedName)) {
                reporter.accept("Package isn't found: " +
                                mParent.mModule.getName() + '/' + dottedName);
                return;
            }

            if (mClasses != null) {
                for (ClassScope cs : mClasses.values()) {
                    cs.validate(loader, reporter);
                }
            }
        }

        private RuleSet.PackageScope build(Module module, Map<String, Module> packageToModule) {
            Map<String, RuleSet.ClassScope> builtClasses;

            if (isEmpty(mClasses)) {
                builtClasses = Map.of();
            } else {
                builtClasses = new LinkedHashMap<>();
                for (Map.Entry<String, ClassScope> e : mClasses.entrySet()) {
                    RuleSet.ClassScope scope = e.getValue().build(this, packageToModule);
                    if (scope != null) {
                        builtClasses.put(e.getKey().intern(), scope);
                    }
                }
            }

            return new RuleSet.PackageScope(module, mName, builtClasses, mDefaultRule);
        }
    }

    /**
     * Builder of rules at the class level.
     */
    public static final class ClassScope {
        private final PackageScope mParent;
        private final String mName;

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
        }

        /**
         * Deny access to all constructors and locally declared methods, superseding all
         * previous rules.
         *
         * @return this
         */
        public ClassScope denyAll() {
            return ruleForAll(deny());
        }

        /**
         * Deny access to all constructors and locally declared methods, superseding all
         * previous rules.
         *
         * @return this
         */
        public ClassScope denyAll(DenyAction action) {
            return ruleForAll(deny(action));
        }

        /**
         * Deny access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllConstructors() {
            return denyAllConstructors(DenyAction.standard());
        }

        /**
         * Deny access to all constructors, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllConstructors(DenyAction action) {
            Rule rule = deny(action);
            mConstructors = null;
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
            return denyAllMethods(DenyAction.standard());
        }

        /**
         * Deny access to all locally declared methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope denyAllMethods(DenyAction action) {
            mDefaultMethodRule = deny(action);
            mMethods = null;
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
            return denyMethod(DenyAction.standard(), name);
        }

        /**
         * Deny access to all variants of the given method, superseding all previous rules.
         *
         * @return this
         * @throws IllegalArgumentException if not a valid method name
         */
        public ClassScope denyMethod(DenyAction action, String name) {
            Rule rule = deny(action);
            mVariantScope = forMethod(name).ruleForAll(rule);
            return this;
        }

        /**
         * Allow access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @param descriptor descriptor for the parameters, not including parenthesis or the
         * return type
         * @return this
         * @throws IllegalStateException if no current constructor or method
         */
        public ClassScope allowVariant(String descriptor) {
            if (mVariantScope == null) {
                throw new IllegalStateException("No current constructor or method");
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
            return allowVariant(paramDescriptorFor(paramTypes));
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
            mConstructors = null;
            mDefaultConstructorRule = allow();
            mVariantScope = mConstructors = new MethodScope().ruleForAll(allow());
            return this;
        }

        /**
         * Allow access to all locally declared methods, superseding all previous rules.
         *
         * @return this
         */
        public ClassScope allowAllMethods() {
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
            mVariantScope = forMethod(name).ruleForAll(allow());
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
        public ClassScope denyVariant(String descriptor) {
            return denyVariant(DenyAction.standard(), descriptor);
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
            Rule rule = deny(action);
            if (mVariantScope == null) {
                throw new IllegalStateException("No current constructor or method");
            }
            mVariantScope.ruleForVariant(rule, descriptor);
            return this;
        }

        /**
         * Deny access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @return this
         * @throws IllegalStateException if no current constructor or method
         */
        public ClassScope denyVariant(Class<?>... paramTypes) {
            return denyVariant(paramDescriptorFor(paramTypes));
        }

        /**
         * Deny access to a specific variant of the current constructor or method, superseding
         * all previous rules.
         *
         * @return this
         * @throws IllegalStateException if no current constructor or method
         */
        public ClassScope denyVariant(DenyAction action, Class<?>... paramTypes) {
            return denyVariant(action, paramDescriptorFor(paramTypes));
        }

        /**
         * End the current rules for this class and return to the package scope. More rules can
         * be added to this scope later if desired.
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
         * End the current rules for this class, package and module, and begin a new module
         * scope. More rules can be added to the scope later if desired.
         *
         * @param name fully qualified module name
         * @throws IllegalArgumentException if the module isn't found
         */
        public ModuleScope forModule(String name) {
            return ClassScope.this.forModule(name, null, null);
        }

        /**
         * End the current rules for this class, package and module, and begin a new module
         * scope. More rules can be added to the scope later if desired.
         *
         * @param name fully qualified module name
         * @param minVersion optional minimum {@link ModuleDescriptor.Version module version
         * number} to support (inclusive)
         * @param maxVersion optional maximum {@link ModuleDescriptor.Version module version
         * number} to support (exclusive)
         * @throws IllegalArgumentException if the module isn't found, or if the version is out of
         * bounds, or if the min/max versions provided aren't parseable
         */
        public ModuleScope forModule(String name, String minVersion, String maxVersion) {
            return end().forModule(name, minVersion, maxVersion);
        }

        void applyDenyRules(ClassScope other) {
            if (other.mConstructors != null) {
                if (mConstructors == null) {
                    mConstructors = new MethodScope().ruleForAll(mDefaultConstructorRule);
                }
                mConstructors.applyDenyRules(other.mConstructors);
            }

            if (other.mDefaultConstructorRule.isDenied()) {
                mDefaultConstructorRule = other.mDefaultConstructorRule;
            }

            if (other.mMethods != null) {
                for (Map.Entry<String, MethodScope> e : other.mMethods.entrySet()) {
                    forMethod(e.getKey()).applyDenyRules(e.getValue());
                }
            }

            if (other.mDefaultMethodRule.isDenied()) {
                mDefaultMethodRule = other.mDefaultMethodRule;
            }
        }

        void applyAllowRules(ClassScope other) {
            if (other.mConstructors != null) {
                if (mConstructors == null) {
                    mConstructors = new MethodScope().ruleForAll(mDefaultConstructorRule);
                }
                mConstructors.applyAllowRules(other.mConstructors);
            }

            if (other.mDefaultConstructorRule.isAllowed()) {
                mDefaultConstructorRule = other.mDefaultConstructorRule;
            }

            if (other.mMethods != null) {
                for (Map.Entry<String, MethodScope> e : other.mMethods.entrySet()) {
                    forMethod(e.getKey()).applyAllowRules(e.getValue());
                }
            }

            if (other.mDefaultMethodRule.isAllowed()) {
                mDefaultMethodRule = other.mDefaultMethodRule;
            }
        }

        /**
         * Validates all class members.
         */
        void validate(ClassLoader loader, Consumer<String> reporter) {
            validateRuleAction(mDefaultConstructorRule, loader, null, reporter);
            validateRuleAction(mDefaultMethodRule, loader, null, reporter);

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

        private MethodScope forMethod(String name) {
            checkMethodName(name);
            Map<String, MethodScope> methods = mMethods;
            if (methods == null) {
                mMethods = methods = new LinkedHashMap<>();
            }
            return methods.computeIfAbsent(name, k -> {
                return new MethodScope().ruleForAll(mDefaultMethodRule);
            });
        }

        /**
         * @return null if redundant
         */
        private RuleSet.ClassScope build(PackageScope packageScope,
                                         Map<String, Module> packageToModule)
        {
            Rule parentRule = packageScope.mDefaultRule;

            if (mConstructors == null && isEmpty(mMethods) &&
                parentRule.equals(mDefaultConstructorRule) &&
                parentRule.isAllowed() && mDefaultMethodRule.isAllowed())
            {
                return null;
            }

            String packageName = packageScope.mName;

            RuleSet.ConstructorScope builtConstructors;

            if (mConstructors == null) {
                builtConstructors = null;
            } else {
                builtConstructors = mConstructors.buildConstructor(mDefaultConstructorRule);
            }

            Map<String, MethodScope> methods = mMethods;

            // Add explicit deny rules for all denied method variants which actually exist.
            {
                methods = methods == null ? new HashMap<>() : new LinkedHashMap<>(methods);
                final var fmethods = methods;

                String fullClassName = fullName(packageName, mName);
                ClassInfo info = ClassInfo.find(fullClassName, packageName, packageToModule);

                if (info != null) info.forAllMethods(packageToModule, mdesc -> {
                    String name = mdesc.getKey();

                    MethodScope scope = fmethods.get(name);

                    if (scope == null) {
                        if (mDefaultMethodRule.isAllowed()) {
                            return true;
                        }
                        scope = new MethodScope().ruleForAll(mDefaultMethodRule);
                        fmethods.put(name, scope);
                    } else if (scope.mDefaultRule.isAllowed()) {
                        return true;
                    }

                    scope.explicitRuleForVariant(mdesc.getValue().paramDesc());

                    return true;
                });
            }

            Map<String, RuleSet.MethodScope> builtMethods;

            if (isEmpty(methods)) {
                builtMethods = Map.of();
            } else {
                builtMethods = new LinkedHashMap<>();
                for (Map.Entry<String, MethodScope> e : methods.entrySet()) {
                    RuleSet.MethodScope scope = e.getValue().buildMethod(mDefaultMethodRule);
                    if (scope != null) {
                        builtMethods.put(e.getKey().intern(), scope);
                    }
                }
            }

            return new RuleSet.ClassScope(packageName, mName,
                                          builtConstructors, mDefaultConstructorRule,
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
            descriptor = toPartialDescriptor(descriptor);
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

        void explicitRuleForVariant(String descriptor) {
            descriptor = toPartialDescriptor(descriptor);
            NavigableMap<CharSequence, Rule> variants = mVariants;

            if (variants == null) {
                mVariants = variants = new TreeMap<>(CharSequence::compare);
            } else if (variants.containsKey(descriptor)) {
                return;
            }

            variants.put(descriptor.intern(), mDefaultRule);
        }

        /**
         * Converts a param descriptor (no parens) to a partial descriptor which has parens.
         */
        private static String toPartialDescriptor(String descriptor) {
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
            return descriptor;
        }

        void applyDenyRules(MethodScope other) {
            if (other.mVariants != null) {
                for (Map.Entry<CharSequence, Rule> e : other.mVariants.entrySet()) {
                    Rule rule = e.getValue();
                    if (rule.isDenied()) {
                        ruleForVariant(rule, e.getKey().toString());
                    }
                }
            }

            if (other.mDefaultRule.isDenied()) {
                mDefaultRule = other.mDefaultRule;
            }
        }

        void applyAllowRules(MethodScope other) {
            if (other.mVariants != null) {
                for (Map.Entry<CharSequence, Rule> e : other.mVariants.entrySet()) {
                    Rule rule = e.getValue();
                    if (rule.isAllowed()) {
                        ruleForVariant(rule, e.getKey().toString());
                    }
                }
            }

            if (other.mDefaultRule.isAllowed()) {
                mDefaultRule = other.mDefaultRule;
            }
        }

        void validateConstructor(ClassLoader loader, Class<?> clazz, Consumer<String> reporter) {
            int count;

            if (isEmpty(mVariants)) {
                Rule rule = mDefaultRule;
                count = forAllConstructors(clazz, ctor -> {
                    validateRuleAction(rule, loader, ctor, reporter);
                });
            } else {
                var toFind = new HashMap<>(mVariants);

                count = forAllConstructors(clazz, ctor -> {
                    String desc = partialDescriptorFor(ctor.getParameterTypes());
                    Rule rule = toFind.remove(desc);
                    if (rule == null) {
                        rule = mDefaultRule;
                    }
                    validateRuleAction(rule, loader, ctor, reporter);
                });

                if (count != 0 && !toFind.isEmpty()) {
                    for (CharSequence desc : toFind.keySet()) {
                        reporter.accept("Constructor isn't found: " + clazz.getName() + desc);
                    }
                }
            }

            if (count == 0) {
                reporter.accept("Constructor isn't found: " + clazz);
            }
        }

        void validateMethod(ClassLoader loader, Class<?> clazz,
                            String name, Consumer<String> reporter)
        {
            int count;

            if (isEmpty(mVariants)) {
                Rule rule = mDefaultRule;
                count = forAllMethods(clazz, name, method -> {
                    validateRuleAction(rule, loader, method, reporter);
                });
            } else {
                var toFind = new HashMap<>(mVariants);

                count = forAllMethods(clazz, name, method -> {
                    String desc = partialDescriptorFor(method.getParameterTypes());
                    Rule rule = toFind.remove(desc);
                    if (rule == null) {
                        rule = mDefaultRule;
                    }
                    validateRuleAction(rule, loader, method, reporter);
                });

                if (count != 0 && !toFind.isEmpty()) {
                    for (CharSequence desc : toFind.keySet()) {
                        reporter.accept("Method isn't found: " + clazz.getName() +
                                        "." + name + desc);
                    }
                }
            }

            if (count == 0) {
                reporter.accept("Method isn't found: " + clazz.getName() + "." + name);
            }
        }

        /**
         * @return null if redundant
         */
        private RuleSet.ConstructorScope buildConstructor(Rule parentRule) {
            NavigableMap<CharSequence, Rule> variants = mVariants;
            if (isEmpty(variants) && mDefaultRule.equals(parentRule)) {
                return null;
            }
            if (variants == null) {
                variants = Collections.emptyNavigableMap();
            }
            return new RuleSet.ConstructorScope(variants, mDefaultRule);
        }

        /**
         * This method should only be called on instances which have all denied variants
         * explicitly specified. Those which are unspecified will be allowed.
         *
         * @return null if redundant
         */
        private RuleSet.MethodScope buildMethod(Rule parentRule) {
            NavigableMap<CharSequence, Rule> variants = mVariants;
            // Note that an explicit deny rule is never considered redundant, even if the rule
            // is the same as the parent rule. This is because RuleSet.denialsForMethod
            // requires explicit denials, and so they cannot be dropped.
            if (mDefaultRule.isAllowed() && parentRule.isAllowed() && isEmpty(variants)) {
                return null;
            }
            if (variants == null) {
                variants = Collections.emptyNavigableMap();
            }
            return new RuleSet.MethodScope(variants);
        }
    }
}
