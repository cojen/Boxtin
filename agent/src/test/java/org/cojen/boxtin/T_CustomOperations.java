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
}
