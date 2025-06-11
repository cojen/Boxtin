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

package org.cojen.boxtin.tt;

import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;

/**
 * Defines various target operations which should be denied.
 *
 * @author Brian S. O'Neill
 * @see TransformTest.Injector
 */
@Ignore
public class T_Operations {
    private static final Object x;

    static {
        // No transforms should be applied to the clinit method.
        x = T_Operations.class;
    }

    public T_Operations() {
    }

    public T_Operations(int x) {
    }

    public static void op1() {
    }

    public static void op2() {
        try {
            new java.io.FileInputStream("");
        } catch (Exception e) {
            throw new Error();
        }
    }

    public static int op3() {
        return 5;
    }

    public static Object op4(int a, String b) {
        a = a == 0 ? 1 : 2;
        b = b == null ? "hello" : b;
        var list = new ArrayList(a == 3 ? 4 : 4);
        return list;
    }

    public List op5() {
        try {
            return List.of(new java.io.FileInputStream(""));
        } catch (Exception e) {
            throw new Error();
        }
    }

    public static native int op6();

    public native int op7();

    public native int op8(int a, int b, long c, String d);
}
