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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class Utils {
    private static final VarHandle cShortArrayBEHandle;
    private static final VarHandle cIntArrayBEHandle;
    private static final VarHandle cLongArrayBEHandle;

    static {
        try {
            cShortArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (short[].class, ByteOrder.BIG_ENDIAN);
            cIntArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (int[].class, ByteOrder.BIG_ENDIAN);
            cLongArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (long[].class, ByteOrder.BIG_ENDIAN);
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

    static long decodeLongBE(byte[] b, int offset) {
        return (long) cLongArrayBEHandle.get(b, offset);
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
     * @return true if the given member is public or protected
     */
    static boolean isAccessible(int flags) {
        return (flags & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0;
    }

    /**
     * @return true if the given member is public or protected
     */
    static boolean isAccessible(Class<?> clazz) {
        return isAccessible(clazz.getModifiers());
    }

    /**
     * @return true if the given member is public or protected
     */
    static boolean isAccessible(Member m) {
        return isAccessible(m.getModifiers());
    }

    /**
     * Returns a method descriptor with parens and no return type.
     */
    static String partialDescriptorFor(Class<?>... paramTypes) {
        if (paramTypes.length == 0) {
            return "()";
        }
        return appendDescriptor(paramTypes).toString();
    }

    /**
     * Returns a method descriptor with parens and no return type.
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
        return appendDescriptor(paramTypes).append(returnType.descriptorString()).toString();
    }

    static StringBuilder appendDescriptor(Class<?>... paramTypes) {
        var b = new StringBuilder().append('(');
        for (Class<?> c : paramTypes) {
            b.append(c.descriptorString());
        }
        return b.append(')');
    }

    /**
     * @param descriptor method descriptor
     * @return null if parsing failed
     */
    static List<String> tryParseParameters(String descriptor) {
        var paramTypes = new ArrayList<String>(4);

        for (int pos = 0; pos < descriptor.length(); ) {
            pos = addParamType(paramTypes, descriptor, pos);
            if (pos <= 0) {
                return null;
            }
        }

        return paramTypes;
    }

    /**
     * @return updated pos; is 0 if parse failed
     */
    private static int addParamType(ArrayList<String> paramTypes, String descriptor, int pos) {
        char first = descriptor.charAt(pos);

        Class<?> type = null;
        String typeName = null;

        switch (first) {
            default -> {
                return 0;
            }
                    
            case 'Z' -> type = boolean.class;
            case 'B' -> type = byte.class;
            case 'S' -> type = short.class;
            case 'C' -> type = char.class;
            case 'I' -> type = int.class;
            case 'F' -> type = float.class;
            case 'D' -> type = double.class;
            case 'J' -> type = long.class;
            case 'V' -> type = void.class;

            case '[' -> {
                pos = addParamType(paramTypes, descriptor, pos + 1);
                if (pos > 0) {
                    int ix = paramTypes.size() - 1;
                    paramTypes.set(ix, paramTypes.get(ix) + "[]");
                }
                return pos;
            }

            case 'L' -> {
                pos++;
                int end = descriptor.indexOf(';', pos);
                if (end < 0) {
                    return 0;
                }
                typeName = descriptor.substring(pos, end).replace('/', '.');
                pos = end;
            }
        }

        if (type != null) {
            assert typeName == null;
            typeName = type.getName();
        }

        paramTypes.add(typeName);

        return pos + 1;
    }

    /**
     * Returns the class name with the package name stripped off.
     */
    static String className(String packageName, Class<?> clazz) {
        String className = clazz.getName();
        return packageName.isEmpty() ? className : className.substring(packageName.length() + 1);
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

    /**
     * Rethrows the given exception without the compiler complaining about it being checked or
     * not. Use as follows: {@code throw rethrow(e)}
     */
    public static RuntimeException rethrow(Throwable e) {
        throw Utils.<RuntimeException>castAndThrow(e);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException castAndThrow(Throwable e) throws T {
        throw (T) e;
    }
}
