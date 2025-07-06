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

import java.util.HashMap;
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
    // Is null when empty.
    private final Map<String, PackageScope> mPackages;

    // Default is selected when no map entry is found.
    private final Rule mDefaultRule;

    // Maps method names to one or more ClassScope instances which have denials.
    private final Map<String, Object> mDeniedMethodsIndex;

    private int mHashCode;

    /**
     * @param packages package names must have '/' characters as separators
     */
    RuleSet(Map<String, PackageScope> packages, Rule defaultRule) {
        if (packages != null && packages.isEmpty()) {
            packages = null;
        }
        mPackages = packages;
        mDefaultRule = defaultRule;

        var index = new HashMap<String, Object>();

        for (Map.Entry<String, PackageScope> e : packages.entrySet()) {
            e.getValue().fillDeniedIndex(index, e.getKey());
        }

        mDeniedMethodsIndex = index;
    }

    @Override
    public int hashCode() {
        int hash = mHashCode;
        if (hash == 0) {
            mHashCode = hash = Objects.hashCode(mPackages) * 31 + mDefaultRule.hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof RuleSet other
            && mDefaultRule == other.mDefaultRule
            && Objects.equals(mPackages, other.mPackages);
    }

    @Override
    public ForClass forClass(CharSequence packageName, CharSequence className) {
        PackageScope scope;
        if (mPackages == null || (scope = mPackages.get(packageName)) == null) {
            return mDefaultRule;
        }
        return scope.forClass(className);
    }

    @Override
    public ForClass forClass(Class<?> clazz) {
        String packageName = clazz.getPackageName();
        PackageScope scope;
        if (mPackages == null || (scope = mPackages.get(packageName.replace('.', '/'))) == null) {
            return mDefaultRule;
        }
        return scope.forClass(className(packageName, clazz));
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

    @Override
    public boolean printTo(Appendable a, String indent, String plusIndent) throws IOException {
        a.append(indent).append("rules").append(" {").append('\n');

        String scopeIndent = indent + plusIndent;

        printAllowOrDenyAll(a, scopeIndent, mDefaultRule).append('\n');

        if (mPackages != null) {
            String subScopeIndent = scopeIndent + plusIndent;

            for (Map.Entry<String, PackageScope> e : mPackages.entrySet()) {
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
        // Is null when empty.
        private final Map<String, ClassScope> mClasses;

        // Default is selected when no map entry is found.
        private final Rule mDefaultRule;

        private int mHashCode;

        PackageScope(Map<String, ClassScope> classes, Rule defaultRule) {
            if (classes != null && classes.isEmpty()) {
                classes = null;
            }
            mClasses = classes;
            mDefaultRule = defaultRule;
        }

        @Override
        public int hashCode() {
            int hash = mHashCode;
            if (hash == 0) {
                mHashCode = hash = Objects.hashCode(mClasses) * 31 + mDefaultRule.hashCode();
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof PackageScope other
                && mDefaultRule == other.mDefaultRule
                && Objects.equals(mClasses, other.mClasses);
        }

        ForClass forClass(CharSequence className) {
            ClassScope scope;
            if (mClasses == null || (scope = mClasses.get(className)) == null) {
                return mDefaultRule;
            }
            return scope;
        }

        void printTo(Appendable a, String indent, String plusIndent) throws IOException {
            printAllowOrDenyAll(a, indent, mDefaultRule).append('\n');

            if (mClasses != null) {
                String scopeIndent = indent + plusIndent;

                for (Map.Entry<String, ClassScope> e : mClasses.entrySet()) {
                    a.append('\n').append(indent).append("for ").append("class").append(' ');
                    String name = e.getKey().replace('$', '.');
                    a.append(name).append(" {").append('\n');

                    e.getValue().printTo(a, scopeIndent, plusIndent);

                    a.append(indent).append('}').append('\n');
                }
            }
        }

        private void fillDeniedIndex(Map<String, Object> index, String pkgName) {
            if (mClasses != null) for (Map.Entry<String, ClassScope> e : mClasses.entrySet()) {
                e.getValue().fillDeniedIndex(index, pkgName, e.getKey());
            }
        }
    }

    static final class ClassScope implements Rules.ForClass {
        // Is null when empty.
        private final MethodScope mConstructors;

        // Default is selected when constructors is empty.
        private final Rule mDefaultConstructorRule;

        // Is null when empty.
        private final Map<String, MethodScope> mMethods;

        // Default is selected when no method map entry is found.
        private final Rule mDefaultMethodRule;

        private int mHashCode;

        private String mPackageName, mClassName;

        ClassScope(MethodScope constructors, Rule defaultConstructorRule,
                   Map<String, MethodScope> methods, Rule defaultMethodRule)
        {
            mConstructors = constructors;
            mDefaultConstructorRule = defaultConstructorRule;
            if (methods != null && methods.isEmpty()) {
                methods = null;
            }
            mMethods = methods;
            mDefaultMethodRule = defaultMethodRule;
        }

        String fullName() {
            String pkgName = mPackageName;
            return pkgName.isEmpty() ? mClassName : (pkgName + '/' + mClassName);
        }

        @Override
        public int hashCode() {
            int hash = mHashCode;
            if (hash == 0) {
                hash = Objects.hashCode(mConstructors);
                hash = hash * 31 + mDefaultConstructorRule.hashCode();
                hash = hash * 31 + Objects.hashCode(mMethods);
                hash = hash * 31 + mDefaultMethodRule.hashCode();
                mHashCode = hash;
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ClassScope other
                && mDefaultConstructorRule == other.mDefaultConstructorRule
                && mDefaultMethodRule == other.mDefaultMethodRule
                && Objects.equals(mConstructors, other.mConstructors)
                && Objects.equals(mMethods, other.mMethods);
        }

        @Override
        public boolean isAllAllowed() {
            return mConstructors == null && mDefaultConstructorRule == Rule.allow()
                && mMethods == null && mDefaultMethodRule == Rule.allow();
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
            Rule rule;
            MethodScope scope;
            if (mMethods == null || (scope = mMethods.get(name)) == null) {
                rule = mDefaultMethodRule;
            } else {
                rule = scope.ruleForMethod(descriptor);
            }
            if (!rule.isAllowed() && isAlwaysAllowed(name, descriptor)) {
                rule = Rule.allow();
            }
            return rule;
        }

        void printTo(Appendable a, String indent, String plusIndent) throws IOException {
            if (mDefaultConstructorRule == mDefaultMethodRule &&
                mConstructors == null && mMethods == null)
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

            if (mMethods != null) {
                for (Map.Entry<String, MethodScope> e : mMethods.entrySet()) {
                    String name = e.getKey();
                    MethodScope scope = e.getValue();
                    a.append(indent).append(scope.mDefaultRule.toString()).append(' ')
                        .append("method").append(' ').append(name).append('\n');
                    if (scope.mVariants != null) {
                        scope.printTo(a, indent + plusIndent);
                    }
                }
            }
        }

        private static boolean isAlwaysAllowed(CharSequence name, CharSequence descriptor) {
            // The common hashCode, equals, and toString methods cannot be denied, even when
            // done so explicitly. This makes it easier to deny all methods in a class without
            // breaking these fundamental operations.

            // Note that the equals method is called on the descriptor, and not the String
            // constants. This is because the equals method as implemented by
            // ConstantPool.C_UTF8 supports more type of objects, but the String equals method
            // only supports Strings. This is a violation of the symmetric property, but it
            // means that UTF8 constants don't need to be fully decoded into Strings.

            if (name.equals("hashCode")) {
                return descriptor.equals("");
            } else if (name.equals("equals")) {
                return descriptor.equals("Ljava/lang/Object;");
            } else if (name.equals("toString")) {
                return descriptor.equals("");
            }

            return false;
        }

        @SuppressWarnings("unchecked")
        private void fillDeniedIndex(Map<String, Object> index, String pkgName, String className) {
            mPackageName = pkgName;
            mClassName = className;
            
            if (mMethods != null) for (Map.Entry<String, MethodScope> e : mMethods.entrySet()) {
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
        // Is null when empty.
        private final NavigableMap<CharSequence, Rule> mVariants;

        // Default is selected when no map entry is found.
        private final Rule mDefaultRule;

        private int mHashCode;

        MethodScope(NavigableMap<CharSequence, Rule> variants, Rule defaultRule) {
            if (variants != null && variants.isEmpty()) {
                variants = null;
            }
            mVariants = variants;
            mDefaultRule = defaultRule;
        }

        @Override
        public int hashCode() {
            int hash = mHashCode;
            if (hash == 0) {
                mHashCode = hash = Objects.hashCode(mVariants) * 31 + mDefaultRule.hashCode();
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof MethodScope other
                && mDefaultRule == other.mDefaultRule
                && Objects.equals(mVariants, other.mVariants);
        }

        boolean isAnyVariantDenied() {
            if (mDefaultRule.isDenied()) {
                return true;
            }

            if (mVariants != null) {
                for (Rule rule : mVariants.values()) {
                    if (rule.isDenied()) {
                        return true;
                    }
                }
            }

            return false;
        }

        Rule ruleForMethod(CharSequence descriptor) {
            Rule rule;
            if (mVariants == null || (rule = findRule(descriptor)) == null) {
                return mDefaultRule;
            }
            return rule;
        }

        private Rule findRule(CharSequence descriptor) {
            Map.Entry<CharSequence, Rule> e = mVariants.floorEntry(descriptor);
            return (e != null && startsWith(descriptor, e.getKey())) ? e.getValue() : null;
        }

        void printTo(Appendable a, String indent) throws IOException {
            if (mVariants != null) {
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
}
