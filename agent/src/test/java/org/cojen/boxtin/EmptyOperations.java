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

import java.util.*;

import java.util.stream.Stream;

import org.junit.Ignore;

/**
 * Defines various operations which should be denied.
 *
 * @author Brian S. O'Neill
 * @see EmptyTransformTest
 */
@Ignore
public class EmptyOperations {

    public EmptyOperations() {
    }

    public static EmptyOperations op1() {
        throw null;
    }

    public static boolean op2() {
        throw null;
    }

    public static Boolean op3() {
        throw null;
    }

    public static byte op4() {
        throw null;
    }

    public static Byte op5() {
        throw null;
    }

    public static char op6() {
        throw null;
    }

    public static Character op7() {
        throw null;
    }

    public static short op8() {
        throw null;
    }

    public static Short op9() {
        throw null;
    }

    public static int op10() {
        throw null;
    }

    public static Integer op11() {
        throw null;
    }

    public static long op12() {
        throw null;
    }

    public static Long op13() {
        throw null;
    }

    public static float op14() {
        throw null;
    }

    public static Float op15() {
        throw null;
    }

    public static double op16() {
        throw null;
    }

    public static Double op17() {
        throw null;
    }

    public static String op18() {
        throw null;
    }

    public static Iterable op19() {
        throw null;
    }

    public static Optional op20() {
        throw null;
    }

    public static OptionalDouble op21() {
        throw null;
    }

    public static OptionalInt op22() {
        throw null;
    }

    public static OptionalLong op23() {
        throw null;
    }

    public static Collection op24() {
        throw null;
    }

    public static Stream op25() {
        throw null;
    }

    public static Enumeration op26() {
        throw null;
    }

    public static Iterator op27() {
        throw null;
    }

    public static List op28() {
        throw null;
    }

    public static ListIterator op29() {
        throw null;
    }

    public static Map op30() {
        throw null;
    }

    public static NavigableMap op31() {
        throw null;
    }

    public static NavigableSet op32() {
        throw null;
    }

    public static Set op33() {
        throw null;
    }

    public static SortedMap op34() {
        throw null;
    }

    public static SortedSet op35() {
        throw null;
    }

    public static Spliterator op36() {
        throw null;
    }

    public static Spliterator.OfDouble op37() {
        throw null;
    }

    public static Spliterator.OfInt op38() {
        throw null;
    }

    public static Spliterator.OfLong op39() {
        throw null;
    }

    public static void op40() {
        throw null;
    }

    public static boolean[] op41() {
        throw null;
    }

    public static byte[] op42() {
        throw null;
    }

    public static char[] op43() {
        throw null;
    }

    public static short[] op44() {
        throw null;
    }

    public static int[] op45() {
        throw null;
    }

    public static long[] op46() {
        throw null;
    }

    public static float[] op47() {
        throw null;
    }

    public static double[] op48() {
        throw null;
    }

    public static String[] op49() {
        throw null;
    }

    public static String[][] op50() {
        throw null;
    }

    public static int[][] op51() {
        throw null;
    }
}
