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
     * Returns an applier which first applies the rules of this applier, and then it applies
     * the rules of the given {@code after} applier.
     */
    /*
    public default RulesApplier andThen(RulesApplier after) {
        Objects.requireNonNull(after);
        return builder -> {
            this.applyRulesTo(builder);
            after.applyRulesTo(builder);
        };
    }
    */

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
     * <li>Loading native code or calling restricted FFM operations (*)
     * <li>Using reflection to bypass any rules
     * <li>Reading resources from other ClassLoaders or Modules
     * <li>Accessing sensitive system properties (**)
     * <li>Altering shared settings (current locale, time zone, etc.)
     * <li>Creating ObjectInputStreams
     * <li>Defining new Modules
     * <li>Exiting the current process
     * <li>Changing sensitive thread settings (priority, etc. ***)
     * <li>Using the spi packages in the java.base module
     * <li>Altering Provider properties
     * <li>Closing or shutting down ForkJoinPools
     * <li>Loading classes into ProtectionDomains
     * </ul>
     *
     * <p>* Loading native code or calling restricted FFM operations is allowed when:
     * <ul>
     * <li>the {@code --enable-native-access} option is used
     * <li>the caller is a named module
     * <li>and the Java version is at least 22. (see also <a
     * href=https://openjdk.org/jeps/454>JEP 454</a>)
     * </ul>
     *
     * <p>** Altering system properties is allowed, but the changes are only visible to the
     * module that changed them. This restriction doesn't apply to modules which are permitted
     * to fully access system properties.
     *
     * <p>*** A few thread settings can be changed if the thread hasn't started yet: name,
     * daemon status, the context ClassLoader, and the thread's own uncaught exception handler.
     */
    public static RulesApplier java_base() {
        return new JavaBaseApplier();
    }

    /**
     * Returns an applier which allows reflection operations, but they are checked to ensure
     * that the corresponding constructor or method is allowed by the other rules. These rules
     * applied automatically when the {@link #java_base java_base} rules are applied.
     *
     * <p>Access is checked when {@code Constructor} and {@code Method} instances are acquired,
     * and not when they're invoked. Custom deny rules perform a check at that time, possibly
     * resulting in an exception being thrown. For methods which return an array (example:
     * {@code Class.getMethods()}), a filtering step is applied which removes elements which
     * cannot be accessed.
     *
     * <p>The following methods in {@code java.lang.Class} have custom deny actions applied:
     *
     * <p>
     * <ul>
     * <li>{@code getConstructor} - can throw a {@code NoSuchMethodException}
     * <li>{@code getConstructors} - can filter the results
     * <li>{@code getDeclaredConstructor} - can throw a {@code NoSuchMethodException}
     * <li>{@code getDeclaredConstructors} - can filter the results
     * <li>{@code getDeclaredMethod} - can throw a {@code NoSuchMethodException}
     * <li>{@code getDeclaredMethods} - can filter the results
     * <li>{@code getEnclosingConstructor} - can throw a {@code NoSuchMethodError}
     * <li>{@code getEnclosingMethod} - can throw a {@code NoSuchMethodError}
     * <li>{@code getMethod} - can throw a {@code NoSuchMethodException}
     * <li>{@code getMethods} - can filter the results
     * <li>{@code getRecordComponents} - can filter the results
     * </ul>
     *
     * <p>Methods which return {@code MethodHandle} instances are checked using the same
     * strategy as for reflection. Custom deny actions are defined for the following {@code
     * Lookup} methods, which can throw a {@code NoSuchMethodException}:
     *
     * <p>
     * <ul>
     * <li>{@code bind}
     * <li>{@code findConstructor}
     * <li>{@code findSpecial}
     * <li>{@code findStatic}
     * <li>{@code findVirtual}
     * </ul>
     *
     * <p>Methods defined by {@code AccessibleObject} which enable access to class members are
     * denied. Calling {@code setAccessible} causes an {@code InaccessibleObjectException} to
     * be thrown, except when the caller module is the same as the target module. Calling
     * {@code trySetAccessible} does nothing, and instead the caller gets a result of {@code
     * false}.
     */
    public static RulesApplier checkReflection() {
        return new ReflectionApplier();
    }
}
