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

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Map;
import java.util.Objects;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class DenyAction {
    /**
     * Returns the standard deny action, which throws a {@code SecurityException} without a
     * message.
     */
    public static DenyAction standard() {
        return Standard.THE;
    }

    /**
     * Returns a deny action which throws an exception without a message.
     */
    public static DenyAction exception(String className) {
        return exception(className, null);
    }

    /**
     * Returns a deny action which throws an exception with an optional message.
     */
    public static DenyAction exception(String className, String message) {
        if (message == null && className.equals(SecurityException.class.getName())) {
            return Standard.THE;
        }
        return message == null ? new Exception(className) : new WithMessage(className, message);
    }

    /**
     * Returns a deny action which throws an exception without a message.
     */
    public static DenyAction exception(Class<?> clazz) {
        return exception(clazz, null);
    }

    /**
     * Returns a deny action which throws an exception with an optional message.
     */
    public static DenyAction exception(Class<?> clazz, String message) {
        if (message == null && clazz == SecurityException.class) {
            return Standard.THE;
        }
        String className = clazz.getName();
        return message == null ? new Exception(className) : new WithMessage(className, message);
    }

    /**
     * Returns a deny action which returns a specific value, possibly null. If the value is
     * incompatible with the actual value type, then null, 0, false, or void is returned
     * instead.
     *
     * <p>Note: This action has no effect for constructors, and if configured as such, the
     * standard action is used instead.
     *
     * @throws IllegalArgumentException if the given value isn't null, a boxed primitive, or a
     * string
     */
    public static DenyAction value(Object value) {
        return value == null ? Value.NULL : new Value(checkValue(value));
    }

    /**
     * @param value must not be null
     */
    private static Object checkValue(Object value) {
        if (value instanceof String || value instanceof Boolean || value instanceof Character) {
            return value;
        } else if (value instanceof Number) {
            if (value instanceof Integer || value instanceof Long || value instanceof Double ||
                value instanceof Float || value instanceof Short || value instanceof Byte)
            {
                return value;
            }
        }

        throw new IllegalArgumentException();
    }

    /**
     * Returns a deny action which returns an empty instance. Types supported are arrays,
     * {@code String}, {@code Optional}, {@code Iterable}, {@code Collection}, {@code Stream},
     * and any of the empty variants supported by the {@code Collections} class. Otherwise, a
     * new instance is created using a no-arg constructor, possibly resulting in a {@code
     * LinkageError} at runtime. If the actual type is primitive, then 0, false or void is
     * returned instead.
     *
     * <p>Note: This action has no effect for constructors, and if configured as such, the
     * standard action is used instead.
     */
    public static DenyAction empty() {
        return Empty.THE;
    }

    /**
     * Returns a deny action which performs a custom operation. The parameters given to the
     * custom method are the caller class (if specified), the non-null instance (if
     * applicable), and the original method parameters. The return type must exactly match the
     * original method's return type. If the custom method type is incompatible, then a {@code
     * SecurityException} or {@code WrongMethodTypeException} can be thrown instead.
     *
     * <p>Note: This action has no effect for constructors, unless the custom operation throws
     * an exception. If it doesn't throw an exception, a {@code SecurityException} is thrown
     * instead.
     */
    public static DenyAction custom(MethodHandleInfo mhi) {
        return new Custom(Objects.requireNonNull(mhi));
    }

    /**
     * Returns a deny action which checks a predicate to determine if the operation should
     * actually be allowed. The parameters given to the predicate are the caller class (if
     * specified), the non-null instance (if applicable), and the original method parameters.
     * The return type must be boolean. If the predicate format is incompatible, then a {@code
     * SecurityException} or {@code WrongMethodTypeException} can be thrown instead.
     *
     * @param predicate the predicate checking method which returns true when the operation is
     * allowed
     * @param action the action to perform when the operation is denied
     * @throws IllegalArgumentException if the predicate doesn't return a boolean or if the
     * given action is checked
     */
    public static DenyAction checked(MethodHandleInfo predicate, DenyAction action) {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(action);
        if (predicate.getMethodType().returnType() != boolean.class || action instanceof Checked) {
            throw new IllegalArgumentException();
        }
        return new Checked(predicate, action);
    }

    private DenyAction() {
    }

    abstract void validate(ClassLoader loader, Executable executable)
        throws ClassNotFoundException, IllegalArgumentException;

    private static void validateHookParameters(Executable executable, MethodHandleInfo hook)
        throws IllegalArgumentException
    {
        Class<?>[] fromTypes = executable.getParameterTypes();

        MethodType toMT = hook.getMethodType();
        int toCount = toMT.parameterCount();

        boolean requireInstance = !Modifier.isStatic(executable.getModifiers());

        int fromIx = 0, toIx = 0;

        for (; toIx < toCount; toIx++) {
            Class<?> to = toMT.parameterType(toIx);

            if (toIx == 0 && to == Class.class) {
                // The optional caller class parameter has been consumed.
                continue;
            }

            if (requireInstance) {
                if (!canConvert(executable.getDeclaringClass(), to)) {
                    throw new IllegalArgumentException
                        ("Cannot convert instance to parameter " + toIx + " of `" + hook + '`');
                }
                // The instance parameter has been consumed.
                requireInstance = false;
                continue;
            }

            if (fromIx >= fromTypes.length) {
                break;
            }

            if (!canConvert(fromTypes[fromIx], to)) {
                throw new IllegalArgumentException
                    ("Cannot convert from parameter " + fromIx + " of `" +
                     executable + "` to parameter " + toIx + " of `" +  hook + '`');
            }

            fromIx++;
        }

        if (toIx < toCount) {
            throw new IllegalArgumentException("Too few parameters from `" +
                                               executable + "` to `" + hook + '`');
        }
    }

    private static boolean canConvert(Class<?> from, Class<?> to) {
        if (to.isAssignableFrom(from)) {
            return true;
        }
        // TODO: Permit widening and boxing of primitives.
        return false;
    }

    static sealed class Exception extends DenyAction {
        final String className;

        private Exception(String className) {
            this.className = className.replace('.', '/').intern();
        }


        @Override
        void validate(ClassLoader loader, Executable executable) throws ClassNotFoundException {
            Class.forName(className.replace('/', '.'), false, loader);
        }

        @Override
        public int hashCode() {
            return 597759314 ^ className.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Exception other
                && className.equals(other.className);
        }

        @Override
        public String toString() {
            return "exception(" + className + ')';
        }
    }

    static final class Standard extends Exception {
        static final Standard THE = new Standard();

        private Standard() {
            super(SecurityException.class.getName());
        }

        @Override
        void validate(ClassLoader loader, Executable executable) {
            // Nothing to check.
        }

        @Override
        public int hashCode() {
            return 50737076;
        }

        @Override
        public String toString() {
            return "standard";
        }
    }

    static final class WithMessage extends Exception {
        final String message;

        private WithMessage(String className, String message) {
            super(className);
            this.message = message;
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 31 + message.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof WithMessage other
                && message.equals(other.message) && super.equals(other);
        }

        @Override
        public String toString() {
            return "exception(" + className + ", " + message + ')';
        }
    }

    static final class Value extends DenyAction {
        private static final Value NULL = new Value(null);

        final Object value;

        private Value(Object value) {
            this.value = value;
        }

        @Override
        void validate(ClassLoader loader, Executable executable) {
            // Parameters aren't considered, and can always return a value, even when the type
            // is incompatible. See the description in the DenyAction.value(Object) method.
        }

        @Override
        public int hashCode() {
            return 434596572 ^ Objects.hashCode(value);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Value other && Objects.equals(value, other.value);
        }

        @Override
        public String toString() {
            return "value(" + value + ')';
        }
    }

    static final class Empty extends DenyAction {
        private static final Empty THE = new Empty();

        private Empty() {
        }

        @Override
        void validate(ClassLoader loader, Executable executable) {
            // See the description in DenyAction.empty() regarding valid return types.

            if (!(executable instanceof Method method)) {
                return;
            }

            Class<?> returnType = method.getReturnType();

            if (returnType.isPrimitive() || returnType.isArray() ||
                EmptyActions.isSupported(returnType))
            {
                return;
            }

            if (Modifier.isPublic(returnType.getModifiers())) {
                try {
                    if (Modifier.isPublic(returnType.getConstructor().getModifiers())) {
                        return;
                    }
                } catch (NoSuchMethodException e) {
                }
            }

            throw new IllegalArgumentException
                ("Empty value not supported for method's return type: " + method);
        }

        @Override
        public int hashCode() {
            return 1539211235;
        }

        @Override
        public String toString() {
            return "empty";
        }
    }

    static final class Custom extends DenyAction {
        final MethodHandleInfo mhi;

        private Custom(MethodHandleInfo mhi) {
            this.mhi = mhi;
        }

        @Override
        void validate(ClassLoader loader, Executable executable) {
            validateHookParameters(executable, mhi);
            
            if (executable instanceof Method method) {
                Class<?> from = method.getReturnType();
                Class<?> to = mhi.getMethodType().returnType();
                if (!canConvert(from, to)) {
                    throw new IllegalArgumentException("Cannot convert method return type from " +
                                                       from + " to " + to);
                }
            }
        }

        @Override
        public int hashCode() {
            return -1398046693 ^ mhi.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Custom other && mhi.equals(other.mhi);
        }

        @Override
        public String toString() {
            return "custom(" + mhi + ')';
        }
    }

    static final class Checked extends DenyAction {
        final MethodHandleInfo predicate;
        final DenyAction action;

        private Checked(MethodHandleInfo predicate, DenyAction action) {
            this.predicate = predicate;
            this.action = action;
        }

        @Override
        void validate(ClassLoader loader, Executable executable) throws ClassNotFoundException {
            validateHookParameters(executable, predicate);
            action.validate(loader, executable);
        }

        @Override
        public int hashCode() {
            return 920768027 ^ predicate.hashCode() ^ action.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Checked other
                && predicate.equals(other.predicate) && action.equals(other.action);
        }

        @Override
        public String toString() {
            return "checked(" + predicate + ", " + action + ')';
        }
    }

    /**
     * @see Rules#denialsForMethod
     */
    static final class Multi extends DenyAction {
        final Map<String, Rule> matches;

        /**
         * @param matches map of fully qualified class names to deny rules; '/' characters are
         * used as separators
         */
        Multi(Map<String, Rule> matches) {
            this.matches = matches;
        }

        @Override
        void validate(ClassLoader loader, Executable executable) throws ClassNotFoundException {
            for (Rule rule : matches.values()) {
                DenyAction action = rule.denyAction();
                if (action != null) {
                    action.validate(loader, executable);
                }
            }
        }

        @Override
        public int hashCode() {
            return matches.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Multi other && matches.equals(other.matches);
        }

        @Override
        public String toString() {
            return "multi(" + matches + ')';
        }
    }
}
