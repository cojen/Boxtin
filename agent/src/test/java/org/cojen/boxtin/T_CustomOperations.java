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

import org.junit.Ignore;

/**
 * Defines various target operations which should be denied.
 *
 * @author Brian S. O'Neill
 * @see TransformTest.Injector
 */
@Ignore
public class T_CustomOperations {

    public T_CustomOperations() {
    }

    public static void op1(int i, boolean b, char c, double d) {
        throw null;
    }

    public static boolean op2(long v) {
        throw null;
    }

    public static long op3(float v) {
        throw null;
    }

    public static float op4(short v) {
        throw null;
    }

    public static double op5(byte v) {
        throw null;
    }

    public static String op6(int a, String b) {
        throw null;
    }

    public static String op7(String a, int b) {
        throw null;
    }

    public static int[] op8(String[] a, int[] b) {
        throw null;
    }

    public static native long op9(String a);

    public static native int op10(long a1, long a2, long a3, long a4, long a5, long a6,
                                  long a7, long a8, long a9, long a10, long a11, long a12,
                                  long a13, long a14, long a15, long a16, long a17, long a18,
                                  long a19, long a20, long a21, long a22, long a23, long a24);

    public static String op11(int a, String b) {
        if (a == 0) {
            var x = new int[] {
                0,1,2,3,4,5,6,7,8,9,
                0,1,2,3,4,5,6,7,8,9,
                0,1,2,3,4,5,6,7,8,9,
                0,1,2,3,4,5,6,7,8,9,
                0,1,2,3,4,5,6,7,8,9,
                0,1,2,3,4,5,6,7,8,9,
            };
            return x.toString();
        }

        throw null;
    }

    public static String op12(int a, String b) {
        if (a == 0) {
            System.out.println(new int[] {
                0,1,2,3,4,5,6,7,8,9,
            });
        }

        throw null;
    }

    public static String op13(double a1, double a2, double a3, double a4, double a5, double a6,
                              double a7, double a8, double a9, double a10, double a11, double a12,
                              long a13, long a14, long a15, long a16, long a17, long a18)
    {
        if (a1 == 0) {
            System.out.println(a2);
        }

        throw null;
    }

    public static String op14(int a, String b,
                              long a1, long a2, long a3, long a4, long a5, long a6,
                              long a7, long a8, long a9, long a10, long a11, long a12,
                              long a13, long a14, long a15, long a16, long a17, long a18,
                              long a19, long a20, long a21, long a22, long a23, long a24)
    {
        try {
            var x = new int[] {
                0,1,2,3,4,5,6,7,0,0,0
            };
            return x.toString();
        } catch (Exception e) {
            throw null;
        }
    }

    public static String op15(int a, String b) {
        try {
            System.out.println(new int[] {
                0,1,2,3,4,5,6,7,8,9,
                0,1,2,3,4,5,6,7,8,9,
                0,1,2,3,4,5,6,7,8,9,
            });
        } catch (Exception e) {
            throw new Error();
        }
        throw null;
    }

    public static String op16(int a) {
        int x = a + 2;
        int y = a == 0 ? 1 : 2;
        return "" + x + y;
    }
}
