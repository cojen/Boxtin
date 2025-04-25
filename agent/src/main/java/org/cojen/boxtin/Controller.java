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
 * Decides what set of operations should be allowed or denied based on the caller's {@code
 * Module}.
 *
 * @author Brian S. O'Neill
 */
public interface Controller {
    /**
     * Returns a Checker which is used to determine if a calling module is allowed to perform
     * an operation.
     *
     * @param module the caller's module
     * @return a checker instance, which can be null if all operations are allowed
     */
    public Checker checkerForCaller(Module module);

    /**
     * Returns a Checker which is used to determine if a calling module is allowed to perform
     * an operation. This variant is provided only for testing and debugging. A class name can
     * be spoofed, and so it shouldn't be used to determine what Checker to return.
     *
     * @param module the caller's module, which can be named or unnamed
     * @param className the caller's class name, using '/' separators for packages
     * @return a checker instance, which can be null if all operations are allowed
     */
    public default Checker checkerForCaller(Module module, String className) {
        return checkerForCaller(module);
    }

    /**
     * Returns a Checker which is used to determine if a calling module is allowed to perform
     * an operation. This variant is provided only for testing and debugging. A class name can
     * be spoofed, and so it shouldn't be used to determine what Checker to return.
     *
     * @param clazz the calling class, which can come from a named or unnamed module
     * @return a checker instance, which can be null if all operations are allowed
     */
    public default Checker checkerForCaller(Class<?> clazz) {
        return checkerForCaller(clazz.getModule());
    }

    // FIXME: The checkerForTarget result is generally supposed to be the "union" of all
    // denials, of all rule sets. Does this mean that the method should be renamed? Should
    // other methods be provided? Define utilities for combining rule sets?

    /**
     * Returns a Checker which is used to apply changes to classes which have deniable
     * operations.
     *
     * @param module the target's module
     * @return a checker instance, which can be null if all operations are allowed
     */
    public Checker checkerForTarget(Module module);

    /**
     * Returns a Checker which is used to apply changes to classes which have deniable
     * operations.
     *
     * @param module the target's module, which is named
     * @param className the target's class name, using '/' separators for packages
     * @return a checker instance, which can be null if all operations are allowed
     */
    public default Checker checkerForTarget(Module module, String className) {
        return checkerForTarget(module);
    }

    /**
     * Returns a Checker which is used to apply changes to classes which have deniable
     * operations.
     *
     * @param clazz the target class, which comes from a named module
     * @return a checker instance, which can be null if all operations are allowed
     */
    public default Checker checkerForTarget(Class<?> clazz) {
        return checkerForTarget(clazz.getModule());
    }
}
