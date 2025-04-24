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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

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

    /**
     * @param packages package names must have '/' characters as separators
     */
    RuleSet(Map<String, PackageScope> packages, Rule defaultRule) {
        if (packages != null && packages.isEmpty()) {
            packages = null;
        }
        mPackages = packages;
        mDefaultRule = defaultRule;
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
    public void printTo(Appendable a, String indent, String plusIndent) throws IOException {
        a.append(indent).append("rules").append(" {").append('\n');

        String scopeIndent = indent + plusIndent;

        printAllowOrDenyAll(a, scopeIndent, mDefaultRule).append('\n');

        if (mPackages != null) {
            String subScopeIndent = scopeIndent + plusIndent;

            for (Map.Entry<String, PackageScope> e : sortedEntries(mPackages)) {
                a.append('\n').append(scopeIndent).append("for ").append("package").append(' ');
                a.append(e.getKey().replace('/', '.'));
                a.append(" {").append('\n');

                e.getValue().printTo(a, subScopeIndent, plusIndent);

                a.append(scopeIndent).append('}').append('\n');
            }
        }

        a.append(indent).append('}').append('\n');
    }

    private static <V> Set<Map.Entry<String, V>> sortedEntries(Map<String, V> map) {
        return (map instanceof SortedMap ? map : new TreeMap<>(map)).entrySet();
    }

    private static String allowOrDeny(Rule rule) {
        return rule.toString().replace('_', ' ').toLowerCase();
    }

    private static Appendable printAllowOrDenyAll(Appendable a, String indent, Rule rule)
        throws IOException
    {
        return a.append(indent).append(allowOrDeny(rule)).append(" all");
    }

    static final class PackageScope {
        // Is null when empty.
        private final Map<String, ClassScope> mClasses;

        // Default is selected when no map entry is found.
        private final Rule mDefaultRule;

        PackageScope(Map<String, ClassScope> classes, Rule defaultRule) {
            if (classes != null && classes.isEmpty()) {
                classes = null;
            }
            mClasses = classes;
            mDefaultRule = defaultRule;
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

                for (Map.Entry<String, ClassScope> e : sortedEntries(mClasses)) {
                    a.append('\n').append(indent).append("for ").append("class").append(' ');
                    String name = e.getKey().replace('$', '.');
                    a.append(name).append(" {").append('\n');

                    e.getValue().printTo(a, scopeIndent, plusIndent);

                    a.append(indent).append('}').append('\n');
                }
            }
        }
    }

    static final class ClassScope implements Checker.ForClass {
        // Is null when empty.
        private final MethodScope mConstructors;

        // Default is selected when constructors is empty.
        private final Rule mDefaultConstructorRule;

        // Is null when empty.
        private final Map<String, MethodScope> mMethods;

        // Default is selected when no method map entry is found.
        private final Rule mDefaultMethodRule;

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

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ClassScope other
                && mDefaultConstructorRule == other.mDefaultConstructorRule
                && mDefaultMethodRule == other.mDefaultMethodRule
                && Objects.equals(mConstructors, other.mConstructors)
                && Objects.equals(mMethods, other.mMethods);
        }

        @Override
        public boolean isAnyConstructorDeniable() {
            return mConstructors != null || mDefaultConstructorRule != Rule.ALLOW;
        }

        @Override
        public boolean isConstructorAllowed(CharSequence descriptor) {
            MethodScope scope = mConstructors;
            if (scope == null) {
                return mDefaultConstructorRule == Rule.ALLOW;
            }
            return scope.isMethodAllowed(descriptor);
        }

        @Override
        public boolean isAnyMethodDeniable() {
            return mMethods != null || mDefaultMethodRule != Rule.ALLOW;
        }

        @Override
        public boolean isMethodAllowed(CharSequence name, CharSequence descriptor) {
            boolean allowed;
            MethodScope scope;
            if (mMethods == null || (scope = mMethods.get(name)) == null) {
                allowed = mDefaultMethodRule == Rule.ALLOW;
            } else {
                allowed = scope.isMethodAllowed(descriptor);
            } 
            return allowed || isAlwaysAllowed(name, descriptor);
        }


        @Override
        public boolean isCallerChecked() {
            return mConstructors != null || mMethods != null
                || mDefaultConstructorRule.isCallerChecked()
                || mDefaultMethodRule.isCallerChecked();
        }


        @Override
        public boolean isCallerConstructorChecked(CharSequence descriptor) {
            MethodScope scope = mConstructors;
            if (scope == null) {
                return mDefaultConstructorRule.isCallerChecked();
            }
            return scope.isCallerMethodChecked(descriptor);
        }

        @Override
        public boolean isCallerMethodChecked(CharSequence name, CharSequence descriptor) {
            boolean checked;
            MethodScope scope;
            if (mMethods == null || (scope = mMethods.get(name)) == null) {
                checked = mDefaultMethodRule.isCallerChecked();
            } else {
                checked = scope.isCallerMethodChecked(descriptor);
            }
            return checked && !isAlwaysAllowed(name, descriptor);
        }

        @Override
        public boolean isTargetChecked() {
            return mConstructors != null || mMethods != null
                || mDefaultConstructorRule.isTargetChecked()
                || mDefaultMethodRule.isTargetChecked();
        }

        @Override
        public boolean isTargetConstructorChecked(CharSequence descriptor) {
            MethodScope scope = mConstructors;
            if (scope == null) {
                return mDefaultConstructorRule.isTargetChecked();
            }
            return scope.isTargetMethodChecked(descriptor);
        }

        @Override
        public boolean isTargetMethodChecked(CharSequence name, CharSequence descriptor) {
            boolean checked;
            MethodScope scope;
            if (mMethods == null || (scope = mMethods.get(name)) == null) {
                checked = mDefaultMethodRule.isTargetChecked();
            } else {
                checked = scope.isTargetMethodChecked(descriptor);
            }
            return checked && !isAlwaysAllowed(name, descriptor);
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
                Set<Map.Entry<String, MethodScope>> entries = sortedEntries(mMethods);
                for (Map.Entry<String, MethodScope> e : entries) {
                    String name = e.getKey();
                    MethodScope scope = e.getValue();
                    a.append(indent).append(allowOrDeny(scope.mDefaultRule)).append(' ')
                        .append("method").append(' ').append(name).append('\n');
                    if (scope.mVariants != null) {
                        scope.printTo(a, indent + plusIndent);
                    }
                }
            }
        }

        private static boolean isAlwaysAllowed(CharSequence name, CharSequence descriptor) {
            // Public methods defined in the Object class are always allowed. Access cannot be
            // denied with a caller-side check, because a cast to Object will circumvent it. A
            // target-side check will work, but it's very odd to deny access to common methods.

            // Note that the equals method is called on the descriptor, and not the String
            // constants. This is because the equals method as implemented by
            // ConstantPool.C_UTF8 supports more type of objects, but the String equals method
            // only supports Strings. This is a violation of the symmetric property, but it
            // means that UTF8 constants don't need to be fully decoded into Strings.

            if (name.equals("hashCode")) {
                return descriptor.equals("()I");
            } else if (name.equals("equals")) {
                return descriptor.equals("(Ljava/lang/Object;)Z");
            } else if (name.equals("toString")) {
                return descriptor.equals("()Ljava/lang/String;");
            }

            return false;
        }
    }

    static final class MethodScope {
        // Is null when empty.
        private final Map<String, Rule> mVariants;

        // Default is selected when no map entry is found.
        private final Rule mDefaultRule;

        MethodScope(Map<String, Rule> variants, Rule defaultRule) {
            if (variants != null && variants.isEmpty()) {
                variants = null;
            }
            mVariants = variants;
            mDefaultRule = defaultRule;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof MethodScope other
                && mDefaultRule == other.mDefaultRule
                && Objects.equals(mVariants, other.mVariants);
        }

        boolean isMethodAllowed(CharSequence descriptor) {
            Rule rule;
            if (mVariants == null || (rule = mVariants.get(descriptor)) == null) {
                rule = mDefaultRule;
            }
            return rule == Rule.ALLOW;
        }

        boolean isCallerMethodChecked(CharSequence descriptor) {
            Rule rule;
            if (mVariants == null || (rule = mVariants.get(descriptor)) == null) {
                rule = mDefaultRule;
            }
            return rule.isCallerChecked();
        }

        boolean isTargetMethodChecked(CharSequence descriptor) {
            Rule rule;
            if (mVariants == null || (rule = mVariants.get(descriptor)) == null) {
                rule = mDefaultRule;
            }
            return rule.isTargetChecked();
        }

        void printTo(Appendable a, String indent) throws IOException {
            if (mVariants != null) {
                for (Map.Entry<String, Rule> e : sortedEntries(mVariants)) {
                    a.append(indent).append(allowOrDeny(e.getValue()));
                    a.append(" variant (");

                    String descriptor = e.getKey();
                    List<String> paramTypes = tryParseDescriptor(descriptor);
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

                    a.append(')').append('\n');
                }
            }
        }

        private List<String> tryParseDescriptor(String descriptor) {
            var paramTypes = new ArrayList<String>(4);

            for (int pos = 0; pos < descriptor.length(); ) {
                pos = addParamType(paramTypes, descriptor, pos);
                if (pos <= 0) {
                    return null;
                }
            }

            return paramTypes;
        }

        /**
         * @return updated pos; is 0 if parse failed
         */
        private static int addParamType(ArrayList<String> paramTypes,
                                        String descriptor, int pos)
        {
            char first = descriptor.charAt(pos);

            Class<?> type = null;
            String typeName = null;

            switch (first) {
                default -> {
                    return 0;
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
                    pos = addParamType(paramTypes, descriptor, pos + 1);
                    if (pos > 0) {
                        int ix = paramTypes.size() - 1;
                        paramTypes.set(ix, paramTypes.get(ix) + "[]");
                    }
                    return pos;
                }

                case 'L' -> {
                    pos++;
                    int end = descriptor.indexOf(';', pos);
                    if (end < 0) {
                        return 0;
                    }
                    typeName = descriptor.substring(pos, end).replace('/', '.');
                    pos = end;
                }
            }

            if (type != null) {
                assert typeName == null;
                typeName = type.getName();
            }

            paramTypes.add(typeName);

            return pos + 1;
        }
    }
}
