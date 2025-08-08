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

import java.util.Map;

/**
 * Checks if access to a class member is allowed or denied, defined by a set of rules, built
 * using a {@link RulesBuilder}.
 *
 * @author Brian S. O'Neill
 */
public interface Rules {
    /**
     * Returns the {@code ModuleLayer} that these rules apply to, which is applicable to the
     * target classes.
     */
    public ModuleLayer moduleLayer();

    /**
     * Returns the rules for a specific target class, as specified by its package name and
     * class name.
     *
     * @param caller the module which contains the caller class
     * @param packageName package name must have '/' characters as separators
     * @param className non-qualified class name
     * @return a non-null {@code ForClass} instance
     */
    public ForClass forClass(Module caller, CharSequence packageName, CharSequence className);

    /**
     * Returns the rules for a specific target class.
     *
     * @param caller the module which contains the caller class
     * @param target the target class
     * @return a non-null {@code ForClass} instance
     */
    public default ForClass forClass(Module caller, Class<?> target) {
        String fullName = target.getName().replace('.', '/');
        String packageName = Utils.packageName(fullName);
        return forClass(caller, packageName, Utils.className(packageName, fullName));
    }

    /**
     * For the given method name and descriptor, return a map of classes which have an explicit
     * deny rule against a matching method. If none match, the returned map is empty.
     *
     * @param name method name
     * @param descriptor descriptor for the parameters, including parenthesis, but the return
     * type is optional
     * @return a non-null map of fully qualified class names to deny rules; '/' characters are
     * used as separators
     */
    public Map<String, Rule> denialsForMethod(CharSequence name, CharSequence descriptor);

    /**
     * Checks access to constructors or methods, for a specific class.
     */
    public static interface ForClass {
        /**
         * Returns true if all operations are allowed.
         */
        public boolean isAllAllowed();

        /**
         * Returns true if all operations are denied, including subtyping of this class.
         * Subtyping is usually allowed, except when all classes in a package are denied by
         * default, and no explicit rule is defined for this class.
         */
        public default boolean isAllDenied() {
            return false;
        }

        /**
         * Returns the rule for a specific constructor, as specified by its descriptor.
         *
         * @param descriptor descriptor for the parameters, including parenthesis, but the
         * return type is optional
         */
        public Rule ruleForConstructor(CharSequence descriptor);

        /**
         * Returns the rule for a specific constructor, as specified by its parameters.
         */
        public default Rule ruleForConstructor(Class<?>... paramTypes) {
            return ruleForConstructor(Utils.partialDescriptorFor(paramTypes));
        }

        /**
         * Returns the rule for a specific method, as specified by its name and descriptor.
         *
         * @param descriptor descriptor for the parameters, including parenthesis, but the
         * return type is optional
         */
        public Rule ruleForMethod(CharSequence name, CharSequence descriptor);

        /**
         * Returns the rule for a specific method, as specified by its name and parameters.
         */
        public default Rule ruleForMethod(CharSequence name, Class<?>... paramTypes) {
            return ruleForMethod(name, Utils.partialDescriptorFor(paramTypes));
        }
    }
}
