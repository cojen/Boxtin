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

import java.util.Random;

import org.junit.*;
import static org.junit.Assert.*;

import org.cojen.boxtin.tt.T_Operations;

/**
 * Tests transformations against caller-side code which is complicated.
 *
 * @author Brian S. O'Neill
 */
public class ComplexTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ComplexTransformTest.class.getName());
    }

    private static final Rules RULES;

    static {
        var b = new RulesBuilder();

        b.forModule("xxx").forPackage("org.cojen.boxtin.tt").forClass("T_Operations")
            .callerCheck()
            .denyAllConstructors().allowVariant(int.class)
            .denyMethod(DenyAction.exception(Exception.class.getName(), "hello"), "op3")
            .denyMethod(DenyAction.empty(), "op5")
            ;

        RULES = b.build();
    }

    public ComplexTransformTest() {
    }

    @Test
    public void ctor() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            new T_Operations();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void wideInc() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            T_Operations.op1();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        for (int x = 0; x < 2000; x += 1000) {
            try {
                T_Operations.op4(x, "");
                fail();
            } catch (SecurityException e) {
                // Expected.
            }
        }
    }

    @Test
    public void newArray() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        var a = new int[10][10];

        try {
            T_Operations.op4(1, a.toString());
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void switches() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        var a = new Random().nextInt();

        int b;
        String c;

        switch (a) {
        case 0: case 1: case 2: case 3: b = 10;
        default: b = 20;
        }

        switch (a) {
        case 10: c = "a";
        case 20: c = "b";
        case 30: c = "c";
        default: c = "";
        }

        try {
            T_Operations.op4(b, c);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }
}
