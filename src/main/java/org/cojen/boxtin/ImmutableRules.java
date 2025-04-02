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
    public Checker apply(Module module, ClassLoader loader) {
        return new CachedChecker(module, loader) {
            @Override
            protected boolean checkConstructorAccess(MemberRef ctorRef) {
                return ImmutableRules.this.checkConstructorAccess(ctorRef);
            }

            @Override
            protected boolean checkMethodAccess(MemberRef methodRef) {
                return ImmutableRules.this.checkMethodAccess(methodRef);
            }

            @Override
            protected boolean checkFieldAccess(MemberRef fieldRef) {
                return ImmutableRules.this.checkFieldAccess(fieldRef);
            }
        };
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

        boolean checkMethodAccess(MemberRef methodRef) {
            Boolean allow;
            if (mVariants == null || (allow = mVariants.get(methodRef)) == null) {
                return mAllowByDefault;
            }
            return allow;
        }
    }
}
