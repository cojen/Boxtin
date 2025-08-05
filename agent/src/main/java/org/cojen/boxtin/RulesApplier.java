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
     * Returns an applier of rules for the java.base module, which denies operations that are
     * considered harmful. This consists of:
     *
     * <p>
     * <ul>
     * <li>Reading and writing arbitrary files, directly or indirectly
     * <li>Creating network sockets
     * <li>Opening URLs
     * <li>Starting processes
     * <li>Loading native code
     * <li>Using reflection to bypass any rules
     * <li>Reading resources from other ClassLoaders or Modules
     * <li>Accessing sensitive system properties
     * <li>Altering shared settings (current locale, time zone, etc.)
     * <li>Creating ObjectInputStreams
     * <li>Defining new Modules
     * <li>Exiting the current process
     * <li>Changing sensitive thread settings (priority, etc.)
     * <li>Calling restricted FFM operations
     * <li>Using the spi packages in the java.base module
     * <li>Altering Provider properties
     * <li>Closing or shutting down ForkJoinPools
     * <li>Loading classes into ProtectionDomains
     * </ul>
     *
     * <p>Altering system properties is allowed, but the changes are only visible to the module
     * that changed them. This restriction doesn't apply to modules which are permitted to
     * fully access system properties.
     *
     * <p>A few thread settings can be changed if the thread hasn't started yet: name, daemon
     * status, the context ClassLoader, and the thread's own uncaught exception handler.
     */
    public static RulesApplier java_base() {
        return new JavaBaseApplier();
    }
}
