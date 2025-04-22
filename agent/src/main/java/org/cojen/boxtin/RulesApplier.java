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
 * Defines and applies common sets of rules.
 *
 * @author Brian S. O'Neill
 */
public interface RulesApplier {
    /**
     * Applies a set of rules to the given builder.
     */
    public void applyRulesTo(RulesBuilder builder);

    /**
     * Returns an applier of rules for the java.base module, which denies operations which
     * could be considered harmful. This includes file I/O, network I/O, exiting the current
     * process, launching processes, etc.
     */
    public static RulesApplier java_base() {
        return new JavaBaseApplier();
    }
}
