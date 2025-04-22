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
import java.lang.invoke.VarHandle;

import java.lang.reflect.Member;
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
     * Returns a descriptor with no parens.
     */
    static String partialDescriptorFor(Class<?>... paramTypes) {
        if (paramTypes.length == 0) {
            return "";
        }
        if (paramTypes.length == 1) {
            return paramTypes[0].descriptorString();
        }
        var b = new StringBuilder();
        for (Class<?> c : paramTypes) {
            b.append(c.descriptorString());
        }
        return b.toString();
    }

    /**
     * Returns a descriptor with parens and a return type.
     */
    static String fullDescriptorFor(Class<?> returnType, Class<?>... paramTypes) {
        var b = new StringBuilder().append('(');
        for (Class<?> c : paramTypes) {
            b.append(c.descriptorString());
        }
        return b.append(')').append(returnType.descriptorString()).toString();
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
}
