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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Objects;

import static java.lang.invoke.MethodHandleInfo.*;

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
     * custom method are the caller class (if specified), the instance (if applicable), and the
     * original method parameters. The return type must exactly match the original method's
     * return type. If the custom method type is incompatible, then a {@code LinkageError} is
     * thrown at runtime.
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
     * specified), the instance (if applicable), and the original method parameters. The return
     * type must be boolean. If the predicate format is incompatible, then a {@code
     * LinkageError} is thrown at runtime.
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

    static DenyAction dynamic() {
        return Dynamic.THE;
    }

    static DenyAction checkedDynamic() {
        return CheckedDynamic.THE;
    }

    private DenyAction() {
    }

    /**
     * @param returnType never a primitive type
     */
    abstract Object apply(Class<?> caller, Class<?> returnType, Object[] args) throws Throwable;

    boolean requiresCaller() {
        return false;
    }

    boolean isChecked() {
        return false;
    }

    abstract void validateReturnType(Method method) throws IllegalArgumentException;

    abstract void validateParameters(Executable exec) throws IllegalArgumentException;

    private static void validateHookParameters(Executable exec, MethodHandleInfo hook)
        throws IllegalArgumentException
    {
        Class<?>[] fromTypes = exec.getParameterTypes();

        MethodType toMT = hook.getMethodType();
        int toCount = toMT.parameterCount();

        boolean requireInstance = !Modifier.isStatic(exec.getModifiers());

        boolean failed = false;
        int fromIx = 0, toIx = 0;

        for (; fromIx < fromTypes.length && toIx < toCount; toIx++) {
            Class<?> to = toMT.parameterType(toIx);

            if (toIx == 0 && to == Class.class) {
                // The optional caller class parameter has been consumed.
                continue;
            }

            if (requireInstance) {
                if (!canConvert(exec.getDeclaringClass(), to)) {
                    failed = true;
                    break;
                }
                // The instance parameter has been consumed.
                requireInstance = false;
                continue;
            }

            if (!canConvert(fromTypes[fromIx], to)) {
                failed = true;
                break;
            }

            fromIx++;
        }

        if (failed) {
            throw new IllegalArgumentException("Cannot convert from parameter " + fromIx + " of `" +
                                               exec + "` to parameter " + toIx + " + of `" + 
                                               hook + '`');
        }

        if (fromIx != fromTypes.length || toIx != toCount) {
            throw new IllegalArgumentException("Mismatched parameters from `" +
                                               exec + "` to `" + hook + '`');
        }
    }

    private static boolean canConvert(Class<?> from, Class<?> to) {
        if (to.isAssignableFrom(from)) {
            return true;
        }
        // TODO: Permit widening and boxing of primitives.
        return false;
    }

    static MethodHandle resolveMethodHandle(MethodHandleInfo mhi, Class<?> caller) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        Class<?> clazz = mhi.getDeclaringClass();
        String name = mhi.getName();
        MethodType mt = mhi.getMethodType();

        try {
            return switch (mhi.getReferenceKind()) {
                default -> throw new SecurityException();
                case REF_getField -> lookup.findGetter(clazz, name, mt.returnType());
                case REF_getStatic -> lookup.findStaticGetter(clazz, name, mt.returnType());
                case REF_putField -> lookup.findSetter(clazz, name, mt.returnType());
                case REF_putStatic -> lookup.findStaticSetter(clazz, name, mt.returnType());
                case REF_invokeVirtual, REF_invokeInterface -> lookup.findVirtual(clazz, name, mt);
                case REF_invokeStatic -> lookup.findStatic(clazz, name, mt);
                case REF_invokeSpecial -> lookup.findSpecial(clazz, name, mt, caller);
                case REF_newInvokeSpecial -> lookup.findConstructor(clazz, mt);
            };
        } catch (SecurityException e) {
            throw e;
        } catch (java.lang.Exception e) {
            throw new SecurityException(e);
        }
    }

    static sealed class Exception extends DenyAction {
        final String className;

        private Exception(String className) {
            this.className = className.replace('.', '/').intern();
        }

        @Override
        Object apply(Class<?> caller, Class<?> returnType, Object[] args) throws Throwable {
            Throwable ex;
            try {
                String name = className.replace('/', '.');
                ex = (Throwable) Class.forName(name).getConstructor().newInstance();
            } catch (SecurityException e) {
                throw e;
            } catch (java.lang.Exception e) {
                throw new SecurityException(e);
            }

            throw ex;
        }

        @Override
        final void validateReturnType(Method method) {
            // Can always throw an exception.
        }

        @Override
        final void validateParameters(Executable exec) {
            // Can always throw an exception.
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
        Object apply(Class<?> caller, Class<?> returnType, Object[] args) {
            throw new SecurityException();
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
        Object apply(Class<?> caller, Class<?> returnType, Object[] args) throws Throwable {
            Throwable ex;
            try {
                String name = className.replace('/', '.');
                ex = (Throwable) Class.forName(name)
                    .getConstructor(String.class).newInstance(message);
            } catch (SecurityException e) {
                throw e;
            } catch (java.lang.Exception e) {
                throw new SecurityException(e);
            }

            throw ex;
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
        Object apply(Class<?> caller, Class<?> returnType, Object[] args) {
            return value;
        }

        @Override
        void validateReturnType(Method method) {
            // Can always return a value, even when the type is incompatible. See the
            // description in the DenyAction.value(Object) method.
        }

        @Override
        void validateParameters(Executable exec) {
            // Parameters aren't considered.
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
        Object apply(Class<?> caller, Class<?> returnType, Object[] args) throws Throwable {
            if (returnType.isPrimitive()) {
                if (returnType == int.class) {
                    return 0;
                } else if (returnType == long.class) {
                    return 0L;
                } else if (returnType == boolean.class) {
                    return false;
                } else if (returnType == void.class) {
                    return null;
                } else if (returnType == double.class) {
                    return 0.0d;
                } else if (returnType == byte.class) {
                    return (byte) 0;
                } else if (returnType == float.class) {
                    return 0.0f;
                } else if (returnType == char.class) {
                    return '\0';
                } else if (returnType == short.class) {
                    return (short) 0;
                } else {
                    throw new SecurityException();
                }
            }

            if (returnType.isArray()) {
                return Array.newInstance(returnType.getComponentType(), 0);
            }

            Method method;

            try {
                method = EmptyActions.class.getMethod(returnType.getName().replace('.', '_'));
            } catch (NoSuchMethodException e) {
                method = null;
            }

            if (method != null) {
                return method.invoke(null);
            }

            try {
                try {
                    return returnType.getConstructor().newInstance();
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            } catch (SecurityException e) {
                throw e;
            } catch (java.lang.Exception e) {
                throw new SecurityException(e);
            }
        }

        @Override
        void validateReturnType(Method method) throws IllegalArgumentException {
            // See the description in DenyAction.empty() regarding valid return types.

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
        void validateParameters(Executable exec) {
            // Parameters aren't considered.
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
        Object apply(Class<?> caller, Class<?> returnType, Object[] args) throws Throwable {
            return resolveMethodHandle(mhi, caller).invokeWithArguments(args);
        }

        @Override
        boolean requiresCaller() {
            return true;
        }

        @Override
        void validateReturnType(Method method) throws IllegalArgumentException {
            Class<?> from = method.getReturnType();
            Class<?> to = mhi.getMethodType().returnType();
            if (!canConvert(from, to)) {
                throw new IllegalArgumentException("Cannot convert method return type from " +
                                                   from + " to " + to);
            }
        }

        @Override
        void validateParameters(Executable exec) throws IllegalArgumentException {
            validateHookParameters(exec, mhi);
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

    static final class Checked extends DenyAction {
        final MethodHandleInfo predicate;
        final DenyAction action;

        private Checked(MethodHandleInfo predicate, DenyAction action) {
            this.predicate = predicate;
            this.action = action;
        }

        @Override
        Object apply(Class<?> caller, Class<?> returnType, Object[] args) throws Throwable {
            var allow = (boolean) resolveMethodHandle(predicate, caller).invokeWithArguments(args);
            // Returning the args object signals that the operation is actually allowed.
            return allow ? args : action.apply(caller, returnType, args);
        }

        @Override
        boolean requiresCaller() {
            return true;
        }

        @Override
        boolean isChecked() {
            return true;
        }

        @Override
        void validateReturnType(Method method) throws IllegalArgumentException {
            action.validateReturnType(method);
        }

        @Override
        void validateParameters(Executable exec) throws IllegalArgumentException {
            validateHookParameters(exec, predicate);
            action.validateParameters(exec);
        }

        @Override
        public int hashCode() {
            return 920768027 ^ predicate.hashCode() ^ action.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Checked other
                && predicate.equals(other.predicate) && action.equals(other.action);
        }

        @Override
        public String toString() {
            return "checked(" + predicate + ", " + action + ')';
        }
    }

    static sealed class Dynamic extends DenyAction {
        static final Dynamic THE = new Dynamic();

        private Dynamic() {
        }

        @Override
        final Object apply(Class<?> caller, Class<?> returnType, Object[] args) {
            // Should never be called.
            throw new SecurityException();
        }

        @Override
        final boolean requiresCaller() {
            return true;
        }

        @Override
        final void validateReturnType(Method method) {
            // Should never be called.
        }

        @Override
        final void validateParameters(Executable exec) {
            // Should never be called.
        }

        @Override
        public int hashCode() {
            return 114945825;
        }

        @Override
        public String toString() {
            return "dynamic";
        }
    }

    static final class CheckedDynamic extends Dynamic {
        static final CheckedDynamic THE = new CheckedDynamic();

        private CheckedDynamic() {
        }

        @Override
        boolean isChecked() {
            return true;
        }

        @Override
        public int hashCode() {
            return 1869195983;
        }
    }
}
