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

import java.util.Collections;
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

        for (PackageScope scope : packageScopes.values()) {
            try {
                scope.addExplicitDenials(this, layer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        {
            var index = new HashMap<String, Object>();

            for (PackageScope scope : packageScopes.values()) {
                scope.fillDeniedIndex(index);
            }

            mDeniedMethodsIndex = index;
        }
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

    /**
     * Returns the module name which provides the given package, or else returns null if
     * unknown.
     *
     * @param packageName package name must have '/' characters as separators
     */
    String moduleForPackage(CharSequence packageName) {
        PackageScope scope = mPackageScopes.get(packageName);
        return scope == null ? null : scope.mModuleName;
    }

    @Override
    public boolean printTo(Appendable a, String indent, String plusIndent) throws IOException {
        a.append(indent).append("rules").append(" {").append('\n');

        String scopeIndent = indent + plusIndent;

        printAllowOrDenyAll(a, scopeIndent, mDefaultRule).append('\n');

        if (!mPackageScopes.isEmpty()) {
            String subScopeIndent = scopeIndent + plusIndent;

            for (Map.Entry<String, PackageScope> e : mPackageScopes.entrySet()) {
                a.append('\n').append(scopeIndent).append("for ").append("package").append(' ');
                a.append(e.getKey().replace('/', '.'));
                a.append(" {").append('\n');

                e.getValue().printTo(a, subScopeIndent, plusIndent);

                a.append(scopeIndent).append('}').append('\n');
            }
        }

        a.append(indent).append('}').append('\n');

        return true;
    }

    private static Appendable printAllowOrDenyAll(Appendable a, String indent, Rule rule)
        throws IOException
    {
        return a.append(indent).append(rule.toString()).append(" all");
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

        void printTo(Appendable a, String indent, String plusIndent) throws IOException {
            printAllowOrDenyAll(a, indent, mDefaultRule).append('\n');

            if (!mClassScopes.isEmpty()) {
                String scopeIndent = indent + plusIndent;

                for (Map.Entry<String, ClassScope> e : mClassScopes.entrySet()) {
                    a.append('\n').append(indent).append("for ").append("class").append(' ');
                    String name = e.getKey().replace('$', '.');
                    a.append(name).append(" {").append('\n');

                    e.getValue().printTo(a, scopeIndent, plusIndent);

                    a.append(indent).append('}').append('\n');
                }
            }
        }

        /**
         * Adds explicit denials for class methods if necessary. It's necessary when the
         * default method rule for the class is to deny access, and the class isn't subtype
         * safe. See ClassInfo.isSubtypeSafe.
         */
        void addExplicitDenials(RuleSet rules, ModuleLayer layer)
            throws IOException, ClassFormatException
        {
            Module module = layer.findModule(mModuleName).orElse(null);

            if (module == null) {
                return;
            }

            for (ClassScope scope : mClassScopes.values()) {
                scope.addExplicitDenials(rules, module);
            }
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
        private final MethodScope mConstructors;

        // Default is selected when constructors is empty.
        private final Rule mDefaultConstructorRule;

        private Map<String, MethodScope> mMethodScopes;

        // Default is selected when no method map entry is found.
        private final Rule mDefaultMethodRule;

        private int mHashCode;

        /**
         * @param packageName must have '/' characters as separators 
         */
        ClassScope(String packageName, String className,
                   MethodScope constructors, Rule defaultConstructorRule,
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
            String pkgName = mPackageName;
            return pkgName.isEmpty() ? mClassName : (pkgName + '/' + mClassName);
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
            MethodScope scope = mConstructors;
            if (scope == null) {
                return mDefaultConstructorRule;
            }
            return scope.ruleForMethod(descriptor);
        }

        @Override
        public Rule ruleForMethod(CharSequence name, CharSequence descriptor) {
            MethodScope scope = mMethodScopes.get(name);
            Rule rule = scope == null ? mDefaultMethodRule : scope.ruleForMethod(descriptor);
            if (!rule.isAllowed() && isObjectMethod(name, descriptor)) {
                rule = Rule.allow();
            }
            return rule;
        }

        void printTo(Appendable a, String indent, String plusIndent) throws IOException {
            if (mDefaultConstructorRule.equals(mDefaultMethodRule) &&
                mConstructors == null && mMethodScopes.isEmpty())
            {
                printAllowOrDenyAll(a, indent, mDefaultConstructorRule).append('\n');
                return;
            }

            printAllowOrDenyAll(a, indent, mDefaultConstructorRule)
                .append(" constructors").append('\n');

            if (mConstructors != null && mConstructors.mVariants != null) {
                mConstructors.printTo(a, indent + plusIndent);
            }

            printAllowOrDenyAll(a, indent, mDefaultMethodRule)
                .append(" methods").append('\n');

            for (Map.Entry<String, MethodScope> e : mMethodScopes.entrySet()) {
                String name = e.getKey();
                MethodScope scope = e.getValue();
                a.append(indent).append(scope.mDefaultRule.toString()).append(' ')
                    .append("method").append(' ').append(name).append('\n');
                if (scope.mVariants != null) {
                    scope.printTo(a, indent + plusIndent);
                }
            }
        }

        /**
         * Adds explicit denials for methods if necessary.
         */
        void addExplicitDenials(RuleSet rules, Module module)
            throws IOException, ClassFormatException
        {
            if (mDefaultMethodRule.isAllowed()) {
                return;
            }

            ClassInfo info = ClassInfo.find(fullName(), mPackageName, module);

            if (info == null || info.isSubtypeSafe(rules)) {
                return;
            }

            Map<String, MethodScope> newMethodScopes = new LinkedHashMap<>(mMethodScopes);

            info.forAllDeclaredMethods(method -> {
                newMethodScopes.computeIfAbsent(method.getKey(), name -> {
                    return new MethodScope(Collections.emptyNavigableMap(), mDefaultMethodRule);
                });
                return true;
            });

            mMethodScopes = newMethodScopes;
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

    static final class MethodScope {
        private final NavigableMap<CharSequence, Rule> mVariants;

        // Default is selected when no map entry is found.
        private final Rule mDefaultRule;

        private int mHashCode;

        MethodScope(NavigableMap<CharSequence, Rule> variants, Rule defaultRule) {
            mVariants = Objects.requireNonNull(variants);
            mDefaultRule = Objects.requireNonNull(defaultRule);
        }

        @Override
        public int hashCode() {
            int hash = mHashCode;
            if (hash == 0) {
                mHashCode = hash = mVariants.hashCode() * 31 + mDefaultRule.hashCode();
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof MethodScope other
                && mDefaultRule.equals(other.mDefaultRule)
                && mVariants.equals(other.mVariants);
        }

        boolean isAnyVariantDenied() {
            if (mDefaultRule.isDenied()) {
                return true;
            }

            for (Rule rule : mVariants.values()) {
                if (rule.isDenied()) {
                    return true;
                }
            }

            return false;
        }

        Rule ruleForMethod(CharSequence descriptor) {
            Rule rule = findRule(descriptor);
            return rule == null ? mDefaultRule : rule;
        }

        private Rule findRule(CharSequence descriptor) {
            Map.Entry<CharSequence, Rule> e = mVariants.floorEntry(descriptor);
            return (e != null && startsWith(descriptor, e.getKey())) ? e.getValue() : null;
        }

        void printTo(Appendable a, String indent) throws IOException {
            for (Map.Entry<CharSequence, Rule> e : mVariants.entrySet()) {
                a.append(indent).append(e.getValue().toString());
                a.append(" variant ");

                String descriptor = e.getKey().toString();
                List<String> paramTypes = tryParseParameters(descriptor);
                if (paramTypes == null) {
                    a.append(descriptor);
                } else {
                    int num = 0;
                    for (String type : paramTypes) {
                        if (num++ > 0) {
                            a.append(", ");
                        }
                        a.append(type);
                    }
                }

                a.append('\n');
            }
        }
    }
}
