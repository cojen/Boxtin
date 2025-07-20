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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

import static org.cojen.boxtin.Utils.*;

/**
 * Defines an immutable set of rules.
 *
 * @author Brian S. O'Neill
 */
final class RuleSet implements Rules {
    private final ModuleLayer mLayer;

    private final Map<String, PackageScope> mPackageScopes;

    // Default is selected when no map entry is found.
    private final Rule mDefaultRule;

    // Set of all named packages available in the ModuleLayer.
    private final Set<String> mModularPackages;

    // Maps method names to one or more ClassScope instances which have explicit denials.
    private final Map<String, Object> mDeniedMethodsIndex;

    private int mHashCode;

    /**
     * @param packageScopes package names must have '/' characters as separators
     */
    RuleSet(ModuleLayer layer, Map<String, PackageScope> packageScopes, Rule defaultRule) {
        mLayer = Objects.requireNonNull(layer);
        mPackageScopes = Objects.requireNonNull(packageScopes);
        mDefaultRule = Objects.requireNonNull(defaultRule);

        addModularPackages(layer, mModularPackages = new HashSet<String>());

        var index = new HashMap<String, Object>();

        for (PackageScope scope : packageScopes.values()) {
            scope.fillDeniedIndex(index);
        }

        mDeniedMethodsIndex = index;
    }

    private static void addModularPackages(ModuleLayer layer, Set<String> modularPackages) {
        for (Module mod : layer.modules()) {
            if (mod.isNamed()) {
                for (String pname : mod.getPackages()) {
                    modularPackages.add(pname.replace('.', '/').intern());
                }
            }
        }

        layer.parents().forEach(parentLayer -> addModularPackages(parentLayer, modularPackages));
    }

    @Override
    public int hashCode() {
        int hash = mHashCode;
        if (hash == 0) {
            hash = mLayer.hashCode();
            hash = hash * 31 + mPackageScopes.hashCode();
            hash = hash * 31 + mDefaultRule.hashCode();
            mHashCode = hash;
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof RuleSet other
            && mLayer.equals(other.mLayer)
            && mDefaultRule.equals(other.mDefaultRule)
            && mPackageScopes.equals(other.mPackageScopes);
    }

    @Override
    public ForClass forClass(CharSequence packageName, CharSequence className) {
        if (!mModularPackages.contains(packageName)) {
            // Denial rules are only applicable to packages which are provided by named
            // modules. If the package isn't provided this way, then allow the operation.
            return Rule.allow();
        }
        PackageScope scope = mPackageScopes.get(packageName);
        return scope == null ? mDefaultRule : scope.forClass(className);
    }

    @Override
    public Map<String, Rule> denialsForMethod(CharSequence name, CharSequence descriptor) {
        Object value = mDeniedMethodsIndex.get(name);

        if (value == null) {
            return Map.of();
        }

        if (value instanceof ClassScope scope) {
            Rule rule = scope.ruleForMethod(name, descriptor);
            return rule.isAllowed() ? Map.of() : Map.of(scope.fullName(), rule);
        }

        @SuppressWarnings("unchecked")
        var set = (Set<ClassScope>) value;

        String firstName = null;
        Rule firstRule = null;
        Map<String, Rule> matches = null;

        for (ClassScope scope : set) {
            Rule rule = scope.ruleForMethod(name, descriptor);
            if (rule.isDenied()) {
                if (firstName == null) {
                    firstName = scope.fullName();
                    firstRule = rule;
                } else {
                    if (matches == null) {
                        matches = new LinkedHashMap<>();
                        matches.put(firstName, firstRule);
                    }
                    matches.put(scope.fullName(), rule);
                }
            }
        }

        if (matches != null) {
            return matches;
        }

        if (firstName != null) {
            return Map.of(firstName, firstRule);
        }

        return Map.of();
    }

    static final class PackageScope {
        // FIXME: If package is deny by default, then unspecified classes should disallow
        // subclassing. Do this by removing them from class file interfaces and superclass. Go
        // up a level for superclass, until an allowed one is found.

        private final String mModuleName, mPackageName;

        private final Map<String, ClassScope> mClassScopes;

        // Default is selected when no map entry is found.
        private final Rule mDefaultRule;

        private int mHashCode;

        /**
         * @param packageName must have '/' characters as separators 
         */
        PackageScope(String moduleName, String packageName,
                     Map<String, ClassScope> classScopes, Rule defaultRule)
        {
            mModuleName = Objects.requireNonNull(moduleName);
            mPackageName = Objects.requireNonNull(packageName);
            mClassScopes = Objects.requireNonNull(classScopes);
            mDefaultRule = Objects.requireNonNull(defaultRule);
        }

        String name() {
            return mPackageName;
        }

        @Override
        public int hashCode() {
            int hash = mHashCode;
            if (hash == 0) {
                hash = mModuleName.hashCode();
                hash = hash * 31 + mPackageName.hashCode();
                hash = hash * 31 + mClassScopes.hashCode();
                hash = hash * 31 + mDefaultRule.hashCode();
                mHashCode = hash;
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof PackageScope other
                && mModuleName.equals(other.mModuleName)
                && mPackageName.equals(other.mPackageName)
                && mDefaultRule.equals(other.mDefaultRule)
                && mClassScopes.equals(other.mClassScopes);
        }

        ForClass forClass(CharSequence className) {
            ClassScope scope = mClassScopes.get(className);
            return scope == null ? mDefaultRule : scope;
        }

        private void fillDeniedIndex(Map<String, Object> index) {
            for (ClassScope scope : mClassScopes.values()) {
                scope.fillDeniedIndex(index);
            }
        }
    }

    static final class ClassScope implements Rules.ForClass {
        private final String mPackageName, mClassName;

        // Is null when empty.
        private final ConstructorScope mConstructors;

        // Default is selected when constructors is empty.
        private final Rule mDefaultConstructorRule;

        private final Map<String, MethodScope> mMethodScopes;

        // Default is selected when no method map entry is found.
        private final Rule mDefaultMethodRule;

        private int mHashCode;

        /**
         * @param packageName must have '/' characters as separators 
         */
        ClassScope(String packageName, String className,
                   ConstructorScope constructors, Rule defaultConstructorRule,
                   Map<String, MethodScope> methodScopes, Rule defaultMethodRule)
        {
            mPackageName = Objects.requireNonNull(packageName);
            mClassName = Objects.requireNonNull(className);
            mConstructors = constructors;
            mDefaultConstructorRule = Objects.requireNonNull(defaultConstructorRule);
            mMethodScopes = Objects.requireNonNull(methodScopes);
            mDefaultMethodRule = Objects.requireNonNull(defaultMethodRule);
        }

        String name() {
            return mClassName;
        }

        String fullName() {
            return Utils.fullName(mPackageName, mClassName);
        }

        @Override
        public int hashCode() {
            int hash = mHashCode;
            if (hash == 0) {
                hash = mPackageName.hashCode();
                hash = hash * 31 + mClassName.hashCode();
                hash = hash * 31 + Objects.hashCode(mConstructors);
                hash = hash * 31 + mDefaultConstructorRule.hashCode();
                hash = hash * 31 + mMethodScopes.hashCode();
                hash = hash * 31 + mDefaultMethodRule.hashCode();
                mHashCode = hash;
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ClassScope other
                && mPackageName.equals(other.mPackageName)
                && mClassName.equals(other.mClassName)
                && mDefaultConstructorRule.equals(other.mDefaultConstructorRule)
                && mDefaultMethodRule.equals(other.mDefaultMethodRule)
                && Objects.equals(mConstructors, other.mConstructors)
                && mMethodScopes.equals(other.mMethodScopes);
        }

        @Override
        public boolean isAllAllowed() {
            return mConstructors == null && mDefaultConstructorRule.isAllowed()
                && mMethodScopes.isEmpty() && mDefaultMethodRule.isAllowed();
        }

        @Override
        public Rule ruleForConstructor(CharSequence descriptor) {
            ConstructorScope scope = mConstructors;
            if (scope == null) {
                return mDefaultConstructorRule;
            }
            return scope.ruleForVariant(descriptor);
        }

        @Override
        public Rule ruleForMethod(CharSequence name, CharSequence descriptor) {
            MethodScope scope = mMethodScopes.get(name);
            Rule rule = scope == null ? mDefaultMethodRule : scope.ruleForVariant(descriptor);
            if (!rule.isAllowed() && isObjectMethod(name, descriptor)) {
                rule = Rule.allow();
            }
            return rule;
        }

        @SuppressWarnings("unchecked")
        private void fillDeniedIndex(Map<String, Object> index) {
            for (Map.Entry<String, MethodScope> e : mMethodScopes.entrySet()) {
                if (e.getValue().isAnyVariantDenied()) {
                    String name = e.getKey();
                    Object value = index.get(name);
                    if (value == null) {
                        index.put(name, this);
                    } else {
                        Set<ClassScope> set;
                        if (value instanceof ClassScope scope) {
                            set = new LinkedHashSet<>();
                            set.add(scope);
                            index.put(name, set);
                        } else {
                            set = (Set<ClassScope>) value;
                        }
                        set.add(this);
                    }
                }
            }
        }
    }

    static abstract class ExecutableScope {
        protected final NavigableMap<CharSequence, Rule> mVariants;

        private int mHashCode;

        ExecutableScope(NavigableMap<CharSequence, Rule> variants) {
            mVariants = Objects.requireNonNull(variants);
        }

        @Override
        public int hashCode() {
            int hash = mHashCode;
            if (hash == 0) {
                hash = mVariants.hashCode() * 31 + defaultRule().hashCode();
                mHashCode = hash ^ 1395528771;
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ExecutableScope other
                && getClass() == other.getClass()
                && defaultRule().equals(other.defaultRule())
                && mVariants.equals(other.mVariants);
        }

        Rule ruleForVariant(CharSequence descriptor) {
            Rule rule = findRule(descriptor);
            return rule == null ? defaultRule() : rule;
        }

        private Rule findRule(CharSequence descriptor) {
            Map.Entry<CharSequence, Rule> e = mVariants.floorEntry(descriptor);
            return (e != null && startsWith(descriptor, e.getKey())) ? e.getValue() : null;
        }

        protected abstract Rule defaultRule();
    }

    static final class ConstructorScope extends ExecutableScope {
        // Default is selected when no map entry is found.
        private final Rule mDefaultRule;

        ConstructorScope(NavigableMap<CharSequence, Rule> variants, Rule defaultRule) {
            super(variants);
            mDefaultRule = Objects.requireNonNull(defaultRule);
        }

        @Override
        protected Rule defaultRule() {
            return mDefaultRule;
        }
    }

    static final class MethodScope extends ExecutableScope {
        MethodScope(NavigableMap<CharSequence, Rule> deniedVariants) {
            super(deniedVariants);
        }

        @Override
        protected Rule defaultRule() {
            return Rule.allow();
        }

        boolean isAnyVariantDenied() {
            return !mVariants.isEmpty();
        }
    }
}
