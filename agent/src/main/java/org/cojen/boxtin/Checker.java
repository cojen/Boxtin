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
 * Checks if access to a class member is allowed or denied.
 *
 * @author Brian S. O'Neill
 */
public interface Checker {
    /**
     * @param packageName package name must have '/' characters as separators
     * @return non-null ForClass instance
     */
    public ForClass forClass(CharSequence packageName, CharSequence className);

    /**
     * @return non-null ForClass instance
     */
    public default ForClass forClass(Class<?> clazz) {
        String packageName = clazz.getPackageName();
        return forClass(packageName.replace('.', '/'), Utils.className(packageName, clazz));
    }

    /**
     * Checks access to constructors or methods, for a specific class.
     */
    public static interface ForClass {
        /**
         * Check if any constructor in the class can deny access.
         */
        public boolean isAnyConstructorDeniable();

        /**
         * Check if the constructor is fully allowed.
         */
        public boolean isConstructorAllowed(CharSequence descriptor);

        /**
         * Check if the constructor is fully allowed.
         */
        public default boolean isConstructorAllowed(Class<?>... paramTypes) {
            return isConstructorAllowed(Utils.fullDescriptorFor(void.class, paramTypes));
        }

        /**
         * Check if any method in the class can deny access.
         */
        public boolean isAnyMethodDeniable();

        /**
         * Check if the method is fully allowed.
         */
        public boolean isMethodAllowed(CharSequence name, CharSequence descriptor);

        /**
         * Check if the method is fully allowed.
         */
        public default boolean isMethodAllowed(Class<?> returnType,
                                               CharSequence name, Class<?>... paramTypes)
        {
            return isMethodAllowed(name, Utils.fullDescriptorFor(returnType, paramTypes));
        }

        /**
         * Returns true if this class is a caller, and it might need to perform checks.
         */
        public boolean isCallerChecked();

        /**
         * Returns true if this class is a caller, and it needs to check if it can call a
         * constructor.
         */
        public boolean isCallerConstructorChecked(CharSequence descriptor);

        /**
         * Returns true if this class is a caller, and it needs to check if it can a call a
         * method.
         */
        public boolean isCallerMethodChecked(CharSequence name, CharSequence descriptor);

        /**
         * Returns true if this class is a target, and it might need to perform checks.
         */
        public boolean isTargetChecked();

        /**
         * Returns true if this class is a target, and it needs to perform a check from within
         * its own constructor.
         */
        public boolean isTargetConstructorChecked(CharSequence descriptor);

        /**
         * Returns true if this class is a target, and it needs to perform a check from within
         * its own method.
         */
        public boolean isTargetMethodChecked(CharSequence name, CharSequence descriptor);
    }
}
