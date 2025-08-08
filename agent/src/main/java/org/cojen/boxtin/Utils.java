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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.nio.ByteOrder;

import java.util.Map;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class Utils {
    private static final VarHandle cShortArrayBEHandle;
    private static final VarHandle cIntArrayBEHandle;

    static {
        try {
            cShortArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (short[].class, ByteOrder.BIG_ENDIAN);
            cIntArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (int[].class, ByteOrder.BIG_ENDIAN);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError();
        }
    }

    static int decodeUnsignedShortBE(byte[] b, int offset) {
        return ((short) cShortArrayBEHandle.get(b, offset)) & 0xffff;
    }

    static void encodeShortBE(byte[] b, int offset, int value) {
        cShortArrayBEHandle.set(b, offset, (short) value);
    }

    static int decodeIntBE(byte[] b, int offset) {
        return (int) cIntArrayBEHandle.get(b, offset);
    }

    static void encodeIntBE(byte[] b, int offset, int value) {
        cIntArrayBEHandle.set(b, offset, value);
    }

    public static int roundUpPower2(int i) {
        // Hacker's Delight figure 3-3.
        i--;
        i |= i >> 1;
        i |= i >> 2;
        i |= i >> 4;
        i |= i >> 8;
        return (i | (i >> 16)) + 1;
    }

    /**
     * Computes padding for TABLESWITCH and LOOKUPSWITCH.
     */
    static int switchPad(int offset) {
        return (4 - (offset & 3)) & 3;
    }

    /**
     * @return true if the given flags are public or protected
     */
    static boolean isAccessible(int flags) {
        return (flags & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0;
    }

    /**
     * @return true if the given member is public or protected
     */
    static boolean isAccessible(Member m) {
        return isAccessible(m.getModifiers());
    }

    /**
     * @param name method name
     * @param descriptor method descriptor with no parens and no return type
     * @return true if the method by the given name and descriptor is declared in Object
     */
    static boolean isObjectMethod(CharSequence name, CharSequence descriptor) {
        // Note that the equals method is called on the descriptor, and not the String
        // constants. This is because the equals method as implemented by ConstantPool.C_UTF8
        // supports more type of objects, but the String equals method only supports Strings.
        // This is a violation of the symmetric property, but it means that UTF8 constants
        // don't need to be fully decoded into Strings.

        return switch (name.charAt(0)) {
            default -> false;
            case 'c' -> name.equals("clone") && descriptor.equals("");
            case 'e' -> name.equals("equals") && descriptor.equals("Ljava/lang/Object;");
            case 'f' -> name.equals("finalize") && descriptor.equals("");
            case 'g' -> name.equals("getClass") && descriptor.equals("");
            case 'h' -> name.equals("hashCode") && descriptor.equals("");
            case 'n' -> (name.equals("notify") || name.equals("notifyAll"))
                && descriptor.equals("");
            case 't' -> name.equals("toString") && descriptor.equals("");
            case 'w' -> name.equals("wait")
                && (descriptor.equals("") || descriptor.equals("J") || descriptor.equals("JI"));
        };
    }

    /**
     * Returns a method parameter descriptor with no parens and no return type.
     */
    static String paramDescriptorFor(Class<?>... paramTypes) {
        if (paramTypes.length == 0) {
            return "";
        }
        var b = new StringBuilder();
        for (Class<?> c : paramTypes) {
            b.append(c.descriptorString());
        }
        return b.toString();
    }

    /**
     * Returns a method descriptor with parens but no return type.
     */
    static String partialDescriptorFor(Class<?>... paramTypes) {
        if (paramTypes.length == 0) {
            return "()";
        }
        var b = new StringBuilder().append('(');
        for (Class<?> c : paramTypes) {
            b.append(c.descriptorString());
        }
        return b.append(')').toString();
    }

    /**
     * Returns a method descriptor with parens but no return type.
     */
    static String partialDescriptorFor(MethodType mt) {
        int count = mt.parameterCount();
        if (count == 0) {
            return "()";
        }
        var b = new StringBuilder().append('(');
        for (int i=0; i<count; i++) {
            b.append(mt.parameterType(i).descriptorString());
        }
        return b.append(')').toString();
    }

    /**
     * Returns a method descriptor with parens and a return type.
     */
    static String fullDescriptorFor(Method m) {
        return fullDescriptorFor(m.getReturnType(), m.getParameterTypes());
    }

    /**
     * Returns a method descriptor with parens and a return type.
     */
    static String fullDescriptorFor(Class<?> returnType, Class<?>... paramTypes) {
        var b = new StringBuilder().append('(');
        for (Class<?> c : paramTypes) {
            b.append(c.descriptorString());
        }
        return b.append(')').append(returnType.descriptorString()).toString();
    }

    /**
     * @param className name must have '/' characters as separators
     */
    static String packageName(String className) {
        int ix = className.lastIndexOf('/');
        return ix <= 0 ? "" :className.substring(0, ix);
    }

    /**
     * Returns the class name with the package name stripped off.
     */
    static String className(String packageName, String className) {
        return packageName.isEmpty() ? className : className.substring(packageName.length() + 1);
    }

    /**
     * Returns a fully qualified name with a '/' separator. The given packageName must already
     * have '/' characters as separators.
     */
    static String fullName(String packageName, String className) {
        return packageName.isEmpty() ? className : (packageName + '/' + className);
    }

    static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Returns true if the first part of str is exactly equal to prefix.
     */
    static boolean startsWith(CharSequence str, CharSequence prefix) {
        if (str instanceof String s && prefix instanceof String p) {
            return s.startsWith(p);
        }
        int plen = prefix.length();
        if (str.length() <= plen) {
            return false;
        }
        for (int i=0; i<plen; i++) {
            if (str.charAt(i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    static RuntimeException rethrow(Throwable e) {
        throw Utils.<RuntimeException>castAndThrow(e);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException castAndThrow(Throwable e) throws T {
        throw (T) e;
    }
}
