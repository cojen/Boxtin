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
public class T_DynamicOperations {

    public T_DynamicOperations() {
    }

    public static String op1() {
        return "op1";
    }

    public static String op2(int a) {
        return "op2";
    }

    public static String op3(int a, String b) {
        return "op3";
    }

    public static int op4(long a, String b) {
        return 123;
    }

    public static int[] op5(long a, float b, double c) {
        return new int[10];
    }

    public static void op6(byte a, char c, short d, boolean x) {
    }

    public static byte op7() {
        return 0;
    }

    public static char op8() {
        return 0;
    }

    public static double op9() {
        return 0;
    }

    public static float op10() {
        return 0;
    }

    public static long op11() {
        return 0;
    }

    public static short op12() {
        return 0;
    }

    public static boolean op13() {
        return false;
    }

    public String[] op14() {
        return new String[10];
    }

    public int op15(Integer x) {
        return x;
    }

    public int op16(Integer x, int y) {
        return x + y;
    }
}
