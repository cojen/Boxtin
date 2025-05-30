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
 * 
 *
 * @author Brian S. O'Neill
 */
public class DynamicTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(DynamicTransformTest.class.getName());
    }

    private static final Rules RULES_1, RULES_2;

    static {
        try {
            var b1 = new RulesBuilder();

            b1.forModule("xxx").forPackage("org.cojen.boxtin")
                .forClass("T_DynamicOperations")
                .allowAllConstructors()
                .denyVariant(DenyAction.empty(), int.class)
                .denyMethod(DenyAction.value("denied1"), "op1")
                .denyMethod(DenyAction.value("denied2"), "op2")
                .denyMethod(DenyAction.value("denied3"), "op3")
                .denyMethod(DenyAction.value(-100), "op4")
                .denyMethod(DenyAction.empty(), "op5")
                .denyMethod(DenyAction.exception(ArithmeticException.class), "op6")
                .denyMethod(DenyAction.empty(), "op7")
                .denyMethod(DenyAction.empty(), "op8")
                .denyMethod(DenyAction.empty(), "op9")
                .denyMethod(DenyAction.empty(), "op10")
                .denyMethod(DenyAction.empty(), "op11")
                .denyMethod(DenyAction.empty(), "op12")
                .denyMethod(DenyAction.empty(), "op13")
                .denyMethod(DenyAction.empty(), "op14")
                .denyMethod(DenyAction.value(1), "op15")
                .denyMethod(DenyAction.value(2), "op16")
                ;

            RULES_1 = b1.build();

            var b2 = new RulesBuilder();

            DenyAction exAction = DenyAction.exception(IllegalStateException.class);

            b2.forModule("xxx").forPackage("org.cojen.boxtin")
                .forClass("T_DynamicOperations")
                .denyMethod(exAction, "op1")
                .denyMethod(exAction, "op2")
                .denyMethod(exAction, "op3")
                .denyMethod(exAction, "op4")
                .denyMethod(exAction, "op5")
                .denyMethod(exAction, "op6")
                .denyMethod(exAction, "op7")
                .denyMethod(exAction, "op8")
                .denyMethod(exAction, "op9")
                .denyMethod(exAction, "op10")
                .denyMethod(exAction, "op11")
                .denyMethod(exAction, "op12")
                .denyMethod(exAction, "op13")
                .denyMethod(exAction, "op14")
                .denyMethod(exAction, "op15")
                .denyMethod(exAction, "op16")
                ;

            RULES_2 = b2.build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    public void dynamic() throws Exception {
        if (runWith(RULES_1, RULES_2)) {
            return;
        }

        assertEquals("denied1", T_DynamicOperations.op1());
        assertEquals("denied2", T_DynamicOperations.op2(1));
        assertEquals("denied3", T_DynamicOperations.op3(1, "x"));
        assertEquals(-100, T_DynamicOperations.op4(1, "x"));
        assertEquals(0, T_DynamicOperations.op5(1, 2.0f, 3.0d).length);

        try {
            T_DynamicOperations.op6((byte) 0, '\0', (short) 0, false);
            fail();
        } catch (ArithmeticException e) {
        }

        assertEquals((byte) 0, T_DynamicOperations.op7());
        assertEquals((char) 0, T_DynamicOperations.op8());
        assertTrue(0.0d == T_DynamicOperations.op9());
        assertTrue(0.0f == T_DynamicOperations.op10());
        assertEquals(0L, T_DynamicOperations.op11());
        assertEquals((short) 0, T_DynamicOperations.op12());
        assertEquals(false, T_DynamicOperations.op13());

        assertEquals(0, new T_DynamicOperations().op14().length);
        assertEquals(1, new T_DynamicOperations().op15(null));
        assertEquals(2, new T_DynamicOperations().op16(null, 3));
    }

    @Test
    public void deniedCtor() throws Exception {
        if (runWith(RULES_1, RULES_2)) {
            return;
        }

        try {
            new T_DynamicOperations(123);
            fail();
        } catch (SecurityException e) {
        }
    }
}
