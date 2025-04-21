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
     * an operation.
     *
     * @param module the caller's module
     * @param className the caller's class name, using '/' separators for packages
     * @return a checker instance, which can be null if all operations are allowed
     */
    public default Checker checkerForCaller(Module module, String className) {
        return checkerForCaller(module);
    }

    /**
     * Returns a Checker which is used to determine if a calling module is allowed to perform
     * an operation.
     *
     * @param clazz the calling class
     * @return a checker instance, which can be null if all operations are allowed
     */
    public default Checker checkerForCaller(Class<?> clazz) {
        return checkerForCaller(clazz.getModule());
    }

    /**
     * Returns a Checker which is used to apply changes to classes which have deniable
     * operations.
     *
     * @return a checker instance, which can be null if all operations are allowed
     */
    // FIXME: This is supposed to be the "union" of all denials, of all rule sets.
    public Checker checkerForTarget();
}
