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

import java.util.List;
import java.util.Optional;

import java.util.function.Supplier;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class TargetTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(TargetTransformTest.class.getName());
    }

    private static final Rules RULES;

    static {
        var b = new RulesBuilder();

        b.forModule("xxx").forPackage("org.cojen.boxtin").forClass("T_Operations")
            .denyAllConstructors().allowVariant(int.class)
            .denyMethod(DenyAction.exception(Exception.class.getName(), "hello"), "op3")
            .denyMethod(DenyAction.empty(), "op5");

        RULES = b.build();
    }

    @Test
    public void new_() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            new T_Operations();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            Optional.empty().orElseGet(T_Operations::new);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void op1() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            T_Operations.op1();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void op2() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            T_Operations.op2();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void op3() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            T_Operations.op3();
            fail();
        } catch (Exception e) {
            // Expected.
            assertEquals("hello", e.getMessage());
        }
    }

    @Test
    public void op4() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            T_Operations.op4(1, "x");
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            trampoline4(T_Operations::op4, 1, "x");
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @FunctionalInterface
    public static interface Op4 {
        Object apply(int a, String b);
    }

    private static Object trampoline4(Op4 f, int a, String b) {
        return f.apply(a, b);
    }

    @Test
    public void op5() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        T_Operations ops = new T_Operations(123);

        assertTrue(ops.op5().isEmpty());

        assertTrue(Optional.<List>empty().orElseGet(ops::op5).isEmpty());
    }

    @Test
    public void op6() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            T_Operations.op6();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void op7() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            new T_Operations(123).op7();
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }
}
