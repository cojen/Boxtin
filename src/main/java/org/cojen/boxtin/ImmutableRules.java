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

import static org.cojen.boxtin.Utils.isEmpty;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class ImmutableRules implements Rules {
    // Can be null when empty.
    private final MemberRefPackageMap<PackageScope> mPackages;

    // Default is selected when no map entry is found.
    private final boolean mAllowByDefault;

    private final SoftCache<Module, Checker> mCache = new SoftCache<>();

    /**
     * @param packages can be null when empty
     */
    static Rules build(MemberRefPackageMap<PackageScope> packages, boolean allowByDefault) {
        // TODO: use a shared instance if isEmpty(packages)
        return new ImmutableRules(packages, allowByDefault);
    }

    private ImmutableRules(MemberRefPackageMap<PackageScope> packages, boolean allowByDefault) {
        mPackages = packages;
        mAllowByDefault = allowByDefault;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ImmutableRules other
            && mAllowByDefault == other.mAllowByDefault
            && Objects.equals(mPackages, other.mPackages);
    }

    @Override
    public Checker checkerFor(Module module) {
        Checker checker = mCache.get(module);

        if (checker == null) {
            synchronized (mCache) {
                checker = mCache.get(module);

                if (checker == null) {
                    checker = new Checker(module) {
                        @Override
                        boolean checkConstructorAccess(MemberRef ctorRef) {
                            return ImmutableRules.this.checkConstructorAccess(ctorRef);
                        }

                        @Override
                        boolean checkMethodAccess(MemberRef methodRef) {
                            return ImmutableRules.this.checkMethodAccess(methodRef);
                        }

                        @Override
                        boolean checkFieldAccess(MemberRef fieldRef) {
                            return ImmutableRules.this.checkFieldAccess(fieldRef);
                        }
                    };
                }

                mCache.put(module, checker);
            }
        }

        return checker;
    }

    @Override
    public void printTo(Appendable a, String indent, String plusIndent) throws IOException {
        a.append(indent).append("rules").append(" {").append('\n');

        String scopeIndent = indent + plusIndent;

        printAllowOrDenyAll(a, scopeIndent, mAllowByDefault).append(';').append('\n');

        var decoder = new UTFDecoder();

        if (!isEmpty(mPackages)) {
            String subScopeIndent = scopeIndent + plusIndent;

            for (Map.Entry<byte[], PackageScope> e : mPackages.sortEntries()) {
                a.append('\n').append(scopeIndent).append("for ").append("package").append(' ');
                a.append(decoder.decode(e.getKey()).replace('/', '.'));
                a.append(" {").append('\n');

                e.getValue().printTo(a, subScopeIndent, plusIndent, decoder);

                a.append(scopeIndent).append('}').append('\n');
            }
        }

        a.append(indent).append('}').append('\n');
    }

    private static String allowOrDeny(boolean allow) {
        return allow ? "allow" : "deny";
    }

    private static Appendable printAllowOrDenyAll(Appendable a, String indent, boolean allow)
        throws IOException
    {
        return a.append(indent).append(allowOrDeny(allow)).append(" all");
    }
 
    boolean checkConstructorAccess(MemberRef ctorRef) {
        return checkMethodAccess(ctorRef);
    }

    boolean checkMethodAccess(MemberRef methodRef) {
        PackageScope scope;
        if (mPackages == null || (scope = mPackages.get(methodRef)) == null) {
            return mAllowByDefault;
        }
        return scope.checkMethodAccess(methodRef);
    }

    boolean checkFieldAccess(MemberRef fieldRef) {
        PackageScope scope;
        if (mPackages == null || (scope = mPackages.get(fieldRef)) == null) {
            return mAllowByDefault;
        }
        return scope.checkFieldAccess(fieldRef);
    }

    /**
     * Returns true if the given map is null or empty;
     */
    private static boolean isEmpty(ImmutableLookupMap map) {
        return map == null || map.isEmpty();
    }

    static final class PackageScope {
        /**
         * @param classes can be null when empty
         */
        static PackageScope build(MemberRefPlainClassMap<ClassScope> classes,
                                  boolean allowByDefault)
        {
            // TODO: use a shared instance if isEmpty(classes)
            return new PackageScope(classes, allowByDefault);
        }

        // Can be null when empty.
        private final MemberRefPlainClassMap<ClassScope> mClasses;

        // Default is selected when no map entry is found.
        private final boolean mAllowByDefault;

        private PackageScope(MemberRefPlainClassMap<ClassScope> classes, boolean allowByDefault) {
            mClasses = classes;
            mAllowByDefault = allowByDefault;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof PackageScope other
                && mAllowByDefault == other.mAllowByDefault
                && Objects.equals(mClasses, other.mClasses);
        }

        void printTo(Appendable a, String indent, String plusIndent, UTFDecoder decoder)
            throws IOException
        {
            printAllowOrDenyAll(a, indent, mAllowByDefault).append(';').append('\n');

            if (!isEmpty(mClasses)) {
                String scopeIndent = indent + plusIndent;

                for (Map.Entry<byte[], ClassScope> e : mClasses.sortEntries()) {
                    a.append('\n').append(indent).append("for ").append("class").append(' ');
                    a.append(decoder.decode(e.getKey())).append(" {").append('\n');

                    e.getValue().printTo(a, scopeIndent, plusIndent, decoder);

                    a.append(indent).append('}').append('\n');
                }
            }
        }

        boolean checkMethodAccess(MemberRef methodRef) {
            ClassScope scope;
            if (mClasses == null || (scope = mClasses.get(methodRef)) == null) {
                return mAllowByDefault;
            }
            return scope.checkMethodAccess(methodRef);
        }

        boolean checkFieldAccess(MemberRef fieldRef) {
            ClassScope scope;
            if (mClasses == null || (scope = mClasses.get(fieldRef)) == null) {
                return mAllowByDefault;
            }
            return scope.checkFieldAccess(fieldRef);
        }
    }

    static final class ClassScope {
        /**
         * @param methods can be null when empty
         * @param fields can be null when empty
         */
        static ClassScope build(MemberRefNameMap<MethodScope> methods,
                                boolean allowMethodsByDefault,
                                boolean allowConstructorsByDefault,
                                MemberRefNameMap<Boolean> fields,
                                boolean allowFieldsByDefault)
        {
            // TODO: use a shared instance if isEmpty(methods) && isEmpty(fields)
            return new ClassScope(methods, allowMethodsByDefault, allowConstructorsByDefault,
                                  fields, allowFieldsByDefault);
        }

        // Can be null when empty.
        private final MemberRefNameMap<MethodScope> mMethods;

        // Default is selected when no method map entry is found.
        private final boolean mAllowMethodsByDefault;

        // Default is selected when no constructor method map entry is found.
        private final boolean mAllowConstructorsByDefault;

        // Can be null when empty.
        private final MemberRefNameMap<Boolean> mFields;

        // Default is selected when no field map entry is found.
        private final boolean mAllowFieldsByDefault;

        private ClassScope(MemberRefNameMap<MethodScope> methods,
                           boolean allowMethodsByDefault, boolean allowConstructorsByDefault,
                           MemberRefNameMap<Boolean> fields, boolean allowFieldsByDefault)
        {
            mMethods = methods;
            mAllowMethodsByDefault = allowMethodsByDefault;
            mAllowConstructorsByDefault = allowConstructorsByDefault;
            mFields = fields;
            mAllowFieldsByDefault = allowFieldsByDefault;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ClassScope other
                && mAllowMethodsByDefault == other.mAllowMethodsByDefault
                && mAllowConstructorsByDefault == other.mAllowConstructorsByDefault
                && mAllowFieldsByDefault == other.mAllowFieldsByDefault
                && Objects.equals(mMethods, other.mMethods)
                && Objects.equals(mFields, other.mFields);
        }

        void printTo(Appendable a, String indent, String plusIndent, UTFDecoder decoder)
            throws IOException
        {
            if ((mAllowConstructorsByDefault == mAllowMethodsByDefault)
                && (mAllowMethodsByDefault == mAllowFieldsByDefault))
            {
                printAllowOrDenyAll(a, indent, mAllowConstructorsByDefault)
                    .append(';').append('\n');
            } else {
                printAllowOrDenyAll(a, indent, mAllowConstructorsByDefault)
                    .append(" constructors").append(';').append('\n');
                printAllowOrDenyAll(a, indent, mAllowMethodsByDefault)
                    .append(" methods").append(';').append('\n');
                printAllowOrDenyAll(a, indent, mAllowFieldsByDefault)
                    .append(" fields").append(';').append('\n');
            }

            if (!isEmpty(mMethods)) {
                String scopeIndent = indent + plusIndent;

                List<Map.Entry<byte[], MethodScope>> entries = mMethods.sortEntries();

                for (Map.Entry<byte[], MethodScope> e : entries) {
                    if (Utils.isConstructor(e.getKey())) {
                        printMethod(a, indent, plusIndent, decoder, scopeIndent, e, true);
                    }
                }

                for (Map.Entry<byte[], MethodScope> e : entries) {
                    if (!Utils.isConstructor(e.getKey())) {
                        printMethod(a, indent, plusIndent, decoder, scopeIndent, e, false);
                    }
                }
            }

            if (!isEmpty(mFields)) {
                String scopeIndent = indent + plusIndent;

                for (Map.Entry<byte[], Boolean> e : mFields) {
                    a.append('\n').append(indent).append(allowOrDeny(e.getValue()));
                    a.append(" field ").append(decoder.decode(e.getKey())).append(';').append('\n');
                }
            }
        }

        private void printMethod(Appendable a, String indent, String plusIndent,
                                 UTFDecoder decoder, String scopeIndent,
                                 Map.Entry<byte[], MethodScope> entry, boolean forCtor)
            throws IOException
        {
            String type;
            if (forCtor) {
                type = "constructor";
            } else {
                type = "method";
            }

            MethodScope scope = entry.getValue();

            if (isEmpty(scope.mVariants)) {
                if (!forCtor) {
                    a.append('\n').append(indent);
                    a.append(allowOrDeny(scope.mAllowByDefault)).append(' ').append(type);
                    a.append(' ').append(decoder.decode(entry.getKey()));
                    a.append(';').append('\n');
                }
            } else {
                a.append('\n').append(indent).append("for ").append(type);
                if (!forCtor) {
                    a.append(' ').append(decoder.decode(entry.getKey()));
                }
                a.append(" {").append('\n');
                scope.printTo(a, scopeIndent, plusIndent, decoder);
                a.append(indent).append('}').append('\n');
            }
        }

        boolean checkMethodAccess(MemberRef methodRef) {
            MethodScope scope;
            if (mMethods == null || (scope = mMethods.get(methodRef)) == null) {
                return methodRef.isConstructor()
                    ? mAllowConstructorsByDefault : mAllowMethodsByDefault;
            }
            return scope.checkMethodAccess(methodRef);
        }

        boolean checkFieldAccess(MemberRef fieldRef) {
            Boolean allow;
            if (mFields == null || (allow = mFields.get(fieldRef)) == null) {
                return mAllowFieldsByDefault;
            }
            return allow;
        }
    }

    static final class MethodScope {
        private static MethodScope cEmptyT, cEmptyF;

        /**
         * @param variants can be null when empty
         */
        static MethodScope build(MemberRefDescriptorMap<Boolean> variants,
                                 boolean allowByDefault)
        {
            MethodScope scope;

            if (!isEmpty(variants)) {
                scope = new MethodScope(variants, allowByDefault);
            } else if (allowByDefault) {
                scope = cEmptyT;
                if (scope == null) {
                    cEmptyT = scope = new MethodScope(null, true);
                }
            } else {
                scope = cEmptyF;
                if (scope == null) {
                    cEmptyF = scope = new MethodScope(null, false);
                }
            }

            return scope;
        }

        // Can be null when empty.
        private final MemberRefDescriptorMap<Boolean> mVariants;

        // Default is selected when no map entry is found.
        private final boolean mAllowByDefault;

        private MethodScope(MemberRefDescriptorMap<Boolean> variants, boolean allowByDefault) {
            mVariants = variants;
            mAllowByDefault = allowByDefault;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof MethodScope other
                && mAllowByDefault == other.mAllowByDefault
                && Objects.equals(mVariants, other.mVariants);
        }

        void printTo(Appendable a, String indent, String plusIndent, UTFDecoder decoder)
            throws IOException
        {
            printAllowOrDenyAll(a, indent, mAllowByDefault).append(';').append('\n');

            if (!isEmpty(mVariants)) {
                for (Map.Entry<byte[], Boolean> e : mVariants.sortEntries()) {
                    a.append('\n').append(indent).append(allowOrDeny(e.getValue()));
                    a.append(" variant (");

                    String descriptor = decoder.decode(e.getKey());
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
                    int end  = descriptor.indexOf(';', pos);
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

        boolean checkMethodAccess(MemberRef methodRef) {
            Boolean allow;
            if (mVariants == null || (allow = mVariants.get(methodRef)) == null) {
                return mAllowByDefault;
            }
            return allow;
        }
    }
}
