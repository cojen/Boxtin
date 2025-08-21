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

import java.io.ObjectInputStream;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import java.security.ProtectionDomain;
import java.security.Provider;

import java.util.concurrent.ForkJoinPool;

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
     * <li>Loading native code or calling restricted {@link java.lang.foreign FFM} operations (*)
     * <li>Using reflection to bypass any rules
     * <li>Reading resources from other {@link ClassLoader ClassLoaders} or {@link Module Modules}
     * <li>Reading sensitive system properties or changing the system properties
     * <li>Altering shared settings (current locale, time zone, etc.)
     * <li>Creating {@link ObjectInputStream ObjectInputStreams}
     * <li>Defining new {@code Modules}
     * <li>Exiting the current process
     * <li>Changing sensitive thread settings (priority, etc. **)
     * <li>Using the {@code spi} packages in the {@code java.base} module
     * <li>Altering {@link Provider} properties
     * <li>Closing or shutting down {@link ForkJoinPool ForkJoinPools}
     * <li>Loading classes into {@link ProtectionDomain ProtectionDomains}
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
     * <p>** A few thread settings can be changed if the thread hasn't started yet: name,
     * daemon status, the context {@code ClassLoader}, and the thread's own uncaught exception
     * handler.
     */
    public static RulesApplier java_base() {
        return new JavaBaseApplier();
    }

    /**
     * Returns an applier which allows reflection operations, but they are checked to ensure
     * that the corresponding constructor or method is allowed by the other rules. These rules
     * applied automatically when the {@link #java_base java_base} rules are applied.
     *
     * <p>Access is checked when {@link Constructor} and {@link Method} instances are acquired,
     * and not when they're invoked. Custom deny rules perform a check at that time, possibly
     * resulting in an exception being thrown. For methods which return an array, a filtering
     * step is applied which removes elements which cannot be accessed.
     *
     * <p>The following {@code Class} methods have custom deny actions applied:
     *
     * <p>
     * <ul>
     * <li>{@link Class#getConstructor getConstructor} - can throw a {@link NoSuchMethodException}
     * <li>{@link Class#getConstructors getConstructors} - can filter the results
     * <li>{@link Class#getDeclaredConstructor getDeclaredConstructor} - can throw a {@link NoSuchMethodException}
     * <li>{@link Class#getDeclaredConstructors getDeclaredConstructors} - can filter the results
     * <li>{@link Class#getDeclaredMethod getDeclaredMethod} - can throw a {@link NoSuchMethodException}
     * <li>{@link Class#getDeclaredMethods getDeclaredMethods} - can filter the results
     * <li>{@link Class#getEnclosingConstructor getEnclosingConstructor} - can throw a {@link NoSuchMethodError}
     * <li>{@link Class#getEnclosingMethod getEnclosingMethod} - can throw a {@link NoSuchMethodError}
     * <li>{@link Class#getMethod getMethod} - can throw a {@link NoSuchMethodException}
     * <li>{@link Class#getMethods getMethods} - can filter the results
     * <li>{@link Class#getRecordComponents getRecordComponents} - can filter the results
     * </ul>
     *
     * <p>Methods which return {@link MethodHandle} instances are checked using the same
     * strategy as for reflection. Custom deny actions are defined for the following {@code
     * Lookup} methods, which can throw a {@link NoSuchMethodException}:
     *
     * <p>
     * <ul>
     * <li>{@link MethodHandles.Lookup#bind bind}
     * <li>{@link MethodHandles.Lookup#findConstructor findConstructor}
     * <li>{@link MethodHandles.Lookup#findSpecial findSpecial}
     * <li>{@link MethodHandles.Lookup#findStatic findStatic}
     * <li>{@link MethodHandles.Lookup#findVirtual findVirtual}
     * </ul>
     *
     * <p>Methods defined by {@link AccessibleObject} which enable access to class members are
     * denied. Calling {@link AccessibleObject#setAccessible setAccessible} causes an {@link
     * InaccessibleObjectException} to be thrown, except when the caller module is the same as
     * the target module. Calling {@link AccessibleObject#trySetAccessible trySetAccessible}
     * does nothing, and instead the caller gets a result of {@code false}.
     *
     * <p>Calling {@link Proxy#newProxyInstance Proxy.newProxyInstance} throws a {@link
     * SecurityException} if any of the given interfaces have any denied operations. Without
     * this check, an {@link InvocationHandler} could get access to a denied {@code Method},
     * bypassing the other reflection checks, and thus allowing method calls on other
     * instances.
     */
    public static RulesApplier checkReflection() {
        return new ReflectionApplier();
    }
}
