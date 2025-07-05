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
import java.io.PrintStream;
import java.io.PrintWriter;

import java.util.Map;

/**
 * Checks if access to a class member is allowed or denied, defined by a set of rules, built
 * using a {@link RulesBuilder}.
 *
 * @author Brian S. O'Neill
 */
public interface Rules {
    /**
     * @param packageName package name must have '/' characters as separators
     * @return a non-null ForClass instance
     */
    public ForClass forClass(CharSequence packageName, CharSequence className);

    /**
     * @return a non-null ForClass instance
     */
    public default ForClass forClass(Class<?> clazz) {
        String packageName = clazz.getPackageName();
        return forClass(packageName.replace('.', '/'), Utils.className(packageName, clazz));
    }

    /**
     * For the given method name and descriptor, return a map of classes which have an explicit
     * deny rule against a matching method. If none match, the returned map is empty.
     *
     * @param name method name
     * @param descriptor descriptor for the parameters, not including parenthesis or the return
     * type
     * @return a non-null map of fully qualified class names to deny rules; '/' characters are
     * used as separators
     */
    public Map<String, Rule> denialsForMethod(CharSequence name, CharSequence descriptor);

    /**
     * Print a description of the rules to the given Appendable object.
     *
     * @param indent minimum indent for each line; pass an empty string for no indent
     * @param plusIndent additional indent when entering a scope
     * @return false if not supported
     */
    public boolean printTo(Appendable a, String indent, String plusIndent) throws IOException;

    /**
     * Print a description of the rules to the given PrintStream.
     */
    public default boolean printTo(PrintStream ps) {
        try {
            return printTo(ps, "", "  ");
        } catch (IOException e) {
            // Not expected.
            return false;
        }
    }

    /**
     * Print a description of the rules to the given PrintStream.
     */
    public default boolean printTo(PrintWriter pw) {
        try {
            return printTo(pw, "", "  ");
        } catch (IOException e) {
            // Not expected.
            return false;
        }
    }

    /**
     * Checks access to constructors or methods, for a specific class.
     */
    public static interface ForClass {
        /**
         * Returns true if all operations are allowed.
         */
        public boolean isAllAllowed();

        /**
         * @param descriptor descriptor for the parameters, not including parenthesis or the
         * return type
         */
        public Rule ruleForConstructor(CharSequence descriptor);

        public default Rule ruleForConstructor(Class<?>... paramTypes) {
            return ruleForConstructor(Utils.partialDescriptorFor(paramTypes));
        }

        /**
         * @param descriptor descriptor for the parameters, not including parenthesis or the
         * return type
         */
        public Rule ruleForMethod(CharSequence name, CharSequence descriptor);

        public default Rule ruleForMethod(Class<?> returnType,
                                          CharSequence name, Class<?>... paramTypes)
        {
            return ruleForMethod(name, Utils.partialDescriptorFor(paramTypes));
        }
    }
}
