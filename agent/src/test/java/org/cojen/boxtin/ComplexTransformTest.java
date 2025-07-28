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

    @Test
    public void monitorEnter() throws Exception {
        if (runTransformed()) {
            return;
        }

        synchronized (this) {
            try {
                new ProcessBuilder();
                fail();
            } catch (SecurityException ex) {
                // Expected.
            }
        }
    }

    @Test
    public void wide() throws Exception {
        if (runTransformed()) {
            return;
        }

        // NOTE: No conditional branches should exist between here and the ProcessBuilder test
        // at the end.

        long a00, a01, a02, a03, a04, a05, a06, a07, a08, a09;
        long a10, a11, a12, a13, a14, a15, a16, a17, a18, a19;
        long a20, a21, a22, a23, a24, a25, a26, a27, a28, a29;
        long a30, a31, a32, a33, a34, a35, a36, a37, a38, a39;
        long a40, a41, a42, a43, a44, a45, a46, a47, a48, a49;
        long a50, a51, a52, a53, a54, a55, a56, a57, a58, a59;
        long a60, a61, a62, a63, a64, a65, a66, a67, a68, a69;
        long a70, a71, a72, a73, a74, a75, a76, a77, a78, a79;
        long a80, a81, a82, a83, a84, a85, a86, a87, a88, a89;
        long a90, a91, a92, a93, a94, a95, a96, a97, a98, a99;

        long b00, b01, b02, b03, b04, b05, b06, b07, b08, b09;
        long b10, b11, b12, b13, b14, b15, b16, b17, b18, b19;
        long b20, b21, b22, b23, b24, b25, b26, b27, b28, b29;
        long b30, b31, b32, b33, b34, b35, b36, b37, b38, b39;
        long b40, b41, b42, b43, b44, b45, b46, b47, b48, b49;
        long b50, b51, b52, b53, b54, b55, b56, b57, b58, b59;
        long b60, b61, b62, b63, b64, b65, b66, b67, b68, b69;
        long b70, b71, b72, b73, b74, b75, b76, b77, b78, b79;
        long b80, b81, b82, b83, b84, b85, b86, b87, b88, b89;
        long b90, b91, b92, b93, b94, b95, b96, b97, b98, b99;

        int a = 0;
        a++;

        b99 = a;
        b98 = b99;

        float f = 0;
        b99 = (long) f;

        double d = 0;
        b99 = (long) d;

        Object x = null;
        Object y = x;

        try {
            new ProcessBuilder();
            fail();
        } catch (SecurityException ex) {
            // Expected.
        }
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
