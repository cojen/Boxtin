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
public class ValueTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ValueTransformTest.class.getName());
    }

    private static final Rules RULES;

    static {
        var b = new RulesBuilder();

        b.forModule("xxx").forPackage("org.cojen.boxtin")
            .forClass("T_ValueOperations")
            .denyMethod(DenyAction.value(null), "op1")
            .denyMethod(DenyAction.value(true), "op2")
            .denyMethod(DenyAction.value('\0'), "op3")
            .denyMethod(DenyAction.value(5), "op4")
            .denyMethod(DenyAction.value('a'), "op5")
            .denyMethod(DenyAction.value(-1), "op6")
            .denyMethod(DenyAction.value(5), "op7")
            .denyMethod(DenyAction.value(100), "op8")
            .denyMethod(DenyAction.value(-1), "op9")
            .denyMethod(DenyAction.value(5), "op10")
            .denyMethod(DenyAction.value(1000), "op11")
            .denyMethod(DenyAction.value(-1), "op12")
            .denyMethod(DenyAction.value(5), "op13")
            .denyMethod(DenyAction.value(100_000), "op14")
            .denyMethod(DenyAction.value(0), "op15")
            .denyMethod(DenyAction.value(1), "op16")
            .denyMethod(DenyAction.value(999_999_999_999L), "op17")
            .denyMethod(DenyAction.value(0), "op18")
            .denyMethod(DenyAction.value(1), "op19")
            .denyMethod(DenyAction.value(2.0f), "op20")
            .denyMethod(DenyAction.value(3.14f), "op21")
            .denyMethod(DenyAction.value(0.0d), "op22")
            .denyMethod(DenyAction.value(1), "op23")
            .denyMethod(DenyAction.value(3.14159), "op24")
            .denyMethod(DenyAction.value("hello"), "op25")
            .denyMethod(DenyAction.value(null), "op26")
            .denyMethod(DenyAction.value("hello"), "op27")
            ;

        RULES = b.build();
    }

    @Test
    public void values() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        T_ValueOperations.op1();
        assertTrue(T_ValueOperations.op2());
        assertEquals('\0', T_ValueOperations.op3());
        assertEquals(5, T_ValueOperations.op4());
        assertEquals('a', T_ValueOperations.op5());
        assertEquals(-1, T_ValueOperations.op6());
        assertEquals(5, T_ValueOperations.op7());
        assertEquals(100, T_ValueOperations.op8());
        assertEquals(-1, T_ValueOperations.op9());
        assertEquals(5, T_ValueOperations.op10());
        assertEquals(1000, T_ValueOperations.op11());
        assertEquals(-1, T_ValueOperations.op12());
        assertEquals(5, T_ValueOperations.op13());
        assertEquals(100_000, T_ValueOperations.op14());
        assertEquals(0, T_ValueOperations.op15());
        assertEquals(1, T_ValueOperations.op16());
        assertEquals(999_999_999_999L, T_ValueOperations.op17());
        assertTrue(0.0f == T_ValueOperations.op18());
        assertTrue(1.0f == T_ValueOperations.op19());
        assertTrue(2.0f == T_ValueOperations.op20());
        assertTrue(3.14f == T_ValueOperations.op21());
        assertTrue(0.0d ==  T_ValueOperations.op22());
        assertTrue(1.0d ==  T_ValueOperations.op23());
        assertTrue(3.14159d == T_ValueOperations.op24());
        assertEquals("hello", T_ValueOperations.op25());
        assertNull(T_ValueOperations.op26());
        assertNull(T_ValueOperations.op27());
    }
}
