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
                .allowAllConstructors()
                .denyMethod(custom(void.class, "c_op1",
                                   int.class, boolean.class, char.class, double.class), "op1")
                .denyMethod(custom(boolean.class, "c_op2", long.class), "op2")
                .denyMethod(custom(long.class, "c_op3", float.class), "op3")
                .denyMethod(custom(float.class, "c_op4", short.class), "op4")
                .denyMethod(custom(double.class, "c_op5", byte.class), "op5")
                .denyMethod(custom(String.class, "c_op6", int.class, String.class), "op6")
                .denyMethod(custom(String.class, "c_op7", String.class, int.class), "op7")
                .denyMethod(custom(int[].class, "c_op8", String[].class, int[].class), "op8")
                .denyMethod(custom(long.class, "c_op9", String.class), "op9")
                .denyMethod(custom(int.class, "c_op10",
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class)
                            , "op10")
                .denyMethod(custom(String.class, "c_op11", int.class, String.class), "op11")
                .denyMethod(custom(String.class, "c_op12", int.class, String.class), "op12")
                .denyMethod(custom(String.class, "c_op13",
                                   double.class, double.class, double.class,
                                   double.class, double.class, double.class,
                                   double.class, double.class, double.class,
                                   double.class, double.class, double.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class)
                            , "op13")
                .denyMethod(custom(String.class, "c_op14",
                                   int.class, String.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class,
                                   long.class, long.class, long.class)
                            , "op14")
                .denyMethod(custom(String.class, "c_op15", int.class, String.class), "op15")
                .denyMethod(custom(String.class, "c_op16", int.class), "op16")
                .denyMethod(custom(Object.class, "c_op17",
                                   T_CustomOperations.class, int.class), "op17")
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

    public static long c_op9(String a) {
        return a.length();
    }

    public static int c_op10(long a1, long a2, long a3, long a4, long a5, long a6,
                             long a7, long a8, long a9, long a10, long a11, long a12,
                             long a13, long a14, long a15, long a16, long a17, long a18,
                             long a19, long a20, long a21, long a22, long a23, long a24)
    {
        return 42;
    }

    public static String c_op11(int a, String b) {
        return "" + a + b;
    }

    public static String c_op12(int a, String b) {
        return "" + b + a;
    }

    public static String c_op13(double a1, double a2, double a3, double a4, double a5, double a6,
                                double a7, double a8, double a9, double a10, double a11, double a12,
                                long a13, long a14, long a15, long a16, long a17, long a18)
    {
        return "" + (a1 + a2 + a3);
    }

    public static String c_op14(int a, String b,
                                long a1, long a2, long a3, long a4, long a5, long a6,
                                long a7, long a8, long a9, long a10, long a11, long a12,
                                long a13, long a14, long a15, long a16, long a17, long a18,
                                long a19, long a20, long a21, long a22, long a23, long a24)
    {
        return "" + (a + b + a1);
    }

    public static String c_op15(int a, String b) {
        return "x" + b + a;
    }

    public static String c_op16(int a) {
        return "q" + a;
    }

    public static Object c_op17(T_CustomOperations obj, int a) {
        return obj;
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
        assertEquals(5, T_CustomOperations.op9("hello"));
        assertEquals(42, T_CustomOperations.op10(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
                                                 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24));
    }

    @Test
    public void complex() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        assertEquals("9hello", T_CustomOperations.op11(9, "hello"));
        assertEquals("hello9", T_CustomOperations.op12(9, "hello"));
        assertEquals("6.0", T_CustomOperations.op13
                     (1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals("1x3", T_CustomOperations.op14
                     (1, "x", 3, 4, 5, 6, 7, 8, 3, 4, 5, 6, 7, 8,
                      3, 4, 5, 6, 7, 8, 3, 4, 5, 6, 7, 8));
        assertEquals("xhello9", T_CustomOperations.op15(9, "hello"));
        assertEquals("q9", T_CustomOperations.op16(9));
    }

    @Test
    public void instance() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        var obj = new T_CustomOperations();
        assertSame(obj, obj.op17(1));
    }
}
