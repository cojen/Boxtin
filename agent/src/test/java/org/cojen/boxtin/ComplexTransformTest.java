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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests various coding patterns to improve test coverage of bytecode processing.
 *
 * @author Brian S. O'Neill
 */
public class ComplexTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ComplexTransformTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        return new RulesBuilder().applyRules(RulesApplier.java_base());
    }

    private int f1, f2;
    private long f3, f4;
    private float f5, f6;
    private double f7, f8;
    private Object f9;

    @Test
    public void set1() throws Exception {
        if (runTransformed()) {
            return;
        }

        // NOTE: No conditional branches should exist between here and the ProcessBuilder test
        // at the end.

        // DUP_X1
        f1 = f2 = 1;

        // DUP_X2 form 1.
        {
            var array = new Long[1];
            long x = array[0] = 10L;
        }

        // DUP2 form 2.
        {
            long v = 1;
            f3 = v = v + 1;
        }

        // DUP2_X1 form 2.
        f3 = f4 = 1L;

        // DUP2_X2 form 2.
        {
            var array = new long[1];
            long x = array[0] = 10L;
        }

        f1 = -f2;
        f3 = -f4;
        f5 = -f6;
        f7 = -f8;
        f1 = ((byte) f2);
        f1 = ((char) f2);
        f1 = ((short) f2);

        f5 = f5 + f6;
        f5 = f5 - f6;
        f5 = f5 * f6;
        f5 = f5 / f6;
        f5 = f5 % f6;

        {
            var array = new float[1];
            f5 = array[0];
        }

        f7 = f7 + f8;
        f7 = f7 - f8;
        f7 = f7 * f8;
        f7 = f7 / f8;
        f7 = f7 % f8;

        {
            var array = new double[1];
            f7 = array[0];
        }

        f1 = (int) f3;
        f1 = (int) f5;
        f1 = (int) f7;

        f3 = (long) f1;
        f3 = (long) f5;
        f3 = (long) f7;

        f5 = (float) f1;
        f5 = (float) f3;
        f5 = (float) f7;

        f7 = (double) f1;
        f7 = (double) f3;
        f7 = (double) f5;

        m1(0, 0, null, 0, 0);

        m2(0, 0, 0, 0, 0, 0);

        {
            f9 = new boolean[1];
            f9 = new char[1];
            f9 = new float[1];
            f9 = new double[1];
            f9 = new byte[1];
            f9 = new short[1];
            f9 = new int[1];
            f9 = new long[1];
            f9 = new String[1][];
            f9 = new String[1][1];
        }

        f9 = this instanceof Object;

        f9 = (new String[1])[0];

        new ComplexTransformTest(1);

        try {
            new ProcessBuilder();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    private static long m1(int a, float b, Object c, long d, double e) {
        // NOTE: No conditional branches should exist between here and the ProcessBuilder test
        // at the end.

        a = 0;
        b = 0;
        c = null;
        d = 0;
        e = e;

        a++;

        try {
            new ProcessBuilder();
            fail();
        } catch (SecurityException ex) {
            // Expected.
        }

        return d;
    }

    private static double m2(long a, long b, long c, long d, float e, double f) {
        // NOTE: No conditional branches should exist between here and the ProcessBuilder test
        // at the end.

        double x = d + e + f;

        try {
            new ProcessBuilder();
            fail();
        } catch (SecurityException ex) {
            // Expected.
        }

        return x;
    }

    public ComplexTransformTest() {
    }

    private ComplexTransformTest(int x) {
        this();

        try {
            new ProcessBuilder();
            fail();
        } catch (SecurityException ex) {
            // Expected.
        }
    }
}
