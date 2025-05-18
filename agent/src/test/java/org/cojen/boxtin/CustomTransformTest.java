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
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class CustomTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(CustomTransformTest.class.getName());
    }

    private static final Rules RULES;

    static {
        try {
            var b = new RulesBuilder();

            b.forModule("xxx").forPackage("org.cojen.boxtin")
                .forClass("T_CustomOperations")
                .denyMethod(custom(void.class, "c_op1",
                                   int.class, boolean.class, char.class, double.class), "op1")
                .denyMethod(custom(boolean.class, "c_op2", long.class), "op2")
                .denyMethod(custom(long.class, "c_op3", float.class), "op3")
                .denyMethod(custom(float.class, "c_op4", short.class), "op4")
                .denyMethod(custom(double.class, "c_op5", byte.class), "op5")
                .denyMethod(custom(String.class, "c_op6", int.class, String.class), "op6")
                .denyMethod(custom(String.class, "c_op7", String.class, int.class), "op7")
                .denyMethod(custom(int[].class, "c_op8", String[].class, int[].class), "op8")
                ;

            RULES = b.build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static DenyAction custom(Class returnType, String name, Class... paramTypes)
        throws NoSuchMethodException, IllegalAccessException
    {
        return DenyAction.custom(find(returnType, name, paramTypes));
    }

    private static MethodHandleInfo find(Class returnType, String name, Class... paramTypes)
        throws NoSuchMethodException, IllegalAccessException
    {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mt = MethodType.methodType(returnType, paramTypes);
        return lookup.revealDirect(lookup.findStatic(CustomTransformTest.class, name, mt));
    }

    public static void c_op1(int i, boolean b, char c, double d) {
        if (i == 0) {
            throw new SecurityException();
        }
    }

    public static boolean c_op2(long v) {
        return v == 5;
    }

    public static long c_op3(float v) {
        return (long) v;
    }

    public static float c_op4(short v) {
        return (float) v;
    }

    public static double c_op5(byte v) {
        return (double) v;
    }

    public static String c_op6(int a, String b) {
        return "" + a + b;
    }

    public static String c_op7(String a, int b) {
        return "" + a + b;
    }

    public static int[] c_op8(String[] a, int[] b) {
        return new int[] {a.length + b.length};
    }

    @Test
    public void custom() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        T_CustomOperations.op1(1, true, 'a', 1.2);

        try {
            T_CustomOperations.op1(0, false, '\0', 0);
            fail();
        } catch (SecurityException e) {
        }

        assertTrue(T_CustomOperations.op2(5L));
        assertEquals(3L, T_CustomOperations.op3(3.0f));
        assertTrue(4.0f == T_CustomOperations.op4((short) 4));
        assertTrue(5.0d == T_CustomOperations.op5((byte) 5));
        assertEquals("1x", T_CustomOperations.op6(1, "x"));
        assertEquals("x1", T_CustomOperations.op7("x", 1));
        assertEquals(3, T_CustomOperations.op8(new String[1], new int[2])[0]);
    }
}
