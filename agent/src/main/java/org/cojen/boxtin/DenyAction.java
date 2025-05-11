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

import java.lang.constant.MethodTypeDesc;

import java.lang.invoke.MethodHandleInfo;

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
        } else if (value instanceof Number n) {
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
     * custom method is instance (if applicable), and the original method parameters. The
     * return type must exactly match the original method's return type. If the custom method
     * type is incompatible, then a {@code LinkageError} is thrown at runtime.
     *
     * <p>Note: This action has no effect for constructors, unless the custom operation throws
     * an exception. Otherwise, the standard action is used.
     */
    public static DenyAction custom(MethodHandleInfo mhi) {
        return new Custom(Objects.requireNonNull(mhi));
    }

    /**
     * @see DenyAction#select
     * /
    @FunctionalInterface
    public static interface Selector {
        /**
         * Returns a deny action based on the actual deniable operation.
         *
         * @param packageName the target package name
         * @param className the target class name, without a package qualifier
         * @param methodName the target method name; is null for constructors
         * @param descriptor the target method descriptor
         * @see MethodTypeDesc
         * /
        public DenyAction select(String packageName, String className,
                                 String methodName, String descriptor);
    }

    /**
     * Returns a deny action which selects the actual deny action based on the deniable
     * operation. If the selected action is actually a select action (or null), then the
     * standard action is used instead.
     * /
    public static DenyAction select(Selector selector) {
        return new Select(Objects.requireNonNull(selector));
    }
    */

    private DenyAction() {
    }

    static sealed class Exception extends DenyAction {
        final String className;

        private Exception(String className) {
            this.className = className.replace('.', '/').intern();
        }

        @Override
        public int hashCode() {
            return 597759314 ^ className.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Exception other && className.equals(other.className);
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
        public int hashCode() {
            return 50737076;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
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
            return obj instanceof WithMessage other && message.equals(other.message)
                && super.equals(other);
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
        public int hashCode() {
            return 434596572 ^ Objects.hashCode(value);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Value other && Objects.equals(value, other.value);
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
        public int hashCode() {
            return -1398046693 ^ mhi.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Custom other && mhi.equals(other.mhi);
        }

        @Override
        public String toString() {
            return "custom(" + mhi + ')';
        }
    }

    /*
    static final class Select extends DenyAction {
        final Selector selector;

        private Select(Selector selector) {
            this.selector = selector;
        }

        @Override
        public int hashCode() {
            return -2029867990 ^ selector.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Select other && selector.equals(other.selector);
        }

        @Override
        public String toString() {
            return "select(" + selector + ')';
        }
    }
    */
}
