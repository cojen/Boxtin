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
     * Returns rules which determine if a calling module is allowed to perform an operation.
     *
     * @param module the caller's module, which can be named or unnamed
     * @return a Rules instance, which can be null if all operations are allowed
     */
    public Rules rulesForCaller(Module module);

    // FIXME: The rulesForTarget result is generally supposed to be the "union" of all
    // denials, of all rule sets. Does this mean that the method should be renamed? Should
    // other methods be provided? Define utilities for combining rule sets?
    //
    // FIXME: The merge rules must be target centric. A CALLER_DENY rule is interpreted as
    // ALLOW. Only TARGET_DENY is kept as-is. Rules.mergeForTarget(...)?

    /**
     * Returns rules which determine if a target class has deniable operations.
     *
     * @return a Rules instance, which can be null if all operations are allowed
     */
    public Rules rulesForTarget();
}
