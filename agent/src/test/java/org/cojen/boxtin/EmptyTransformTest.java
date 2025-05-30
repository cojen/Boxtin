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
public class EmptyTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(EmptyTransformTest.class.getName());
    }

    private static final int NUM = 51;

    private static final Rules RULES;

    static {
        var b = new RulesBuilder();

        var forClass = b.forModule("xxx").forPackage("org.cojen.boxtin")
            .forClass("T_EmptyOperations")
            .allowAllConstructors().denyVariant(DenyAction.empty());

        for (int i=1; i<=NUM; i++) {
            if ((i & 1) == 0) {
                forClass.callerCheck();
            } else {
                forClass.targetCheck();
            }
            forClass.denyMethod(DenyAction.empty(), "op" + i);
        }

        RULES = b.build();
    }

    @Test
    public void empty() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        assertNotNull(T_EmptyOperations.op1());
        assertFalse(T_EmptyOperations.op2());
        assertFalse(T_EmptyOperations.op3());
        assertEquals((byte) 0, T_EmptyOperations.op4());
        assertEquals((byte) 0, (byte) T_EmptyOperations.op5());
        assertEquals('\0', T_EmptyOperations.op6());
        assertEquals('\0', (char) T_EmptyOperations.op7());
        assertEquals((short) 0, T_EmptyOperations.op8());
        assertEquals((short) 0, (short) T_EmptyOperations.op9());
        assertEquals(0, T_EmptyOperations.op10());
        assertEquals(0, (int) T_EmptyOperations.op11());
        assertEquals(0L, T_EmptyOperations.op12());
        assertEquals(0L, (long) T_EmptyOperations.op13());
        assertTrue(0.0f == T_EmptyOperations.op14());
        assertTrue(0.0f == (float) T_EmptyOperations.op15());
        assertTrue(0.0d == T_EmptyOperations.op16());
        assertTrue(0.0d == (double) T_EmptyOperations.op17());
        assertTrue(T_EmptyOperations.op18().isEmpty());
        assertFalse(T_EmptyOperations.op19().iterator().hasNext());
        assertTrue(T_EmptyOperations.op20().isEmpty());
        assertTrue(T_EmptyOperations.op21().isEmpty());
        assertTrue(T_EmptyOperations.op22().isEmpty());
        assertTrue(T_EmptyOperations.op23().isEmpty());
        assertTrue(T_EmptyOperations.op24().isEmpty());
        assertTrue(T_EmptyOperations.op25().toList().isEmpty());
        assertFalse(T_EmptyOperations.op26().hasMoreElements());
        assertFalse(T_EmptyOperations.op27().hasNext());
        assertTrue(T_EmptyOperations.op28().isEmpty());
        assertFalse(T_EmptyOperations.op29().hasNext());
        assertTrue(T_EmptyOperations.op30().isEmpty());
        assertTrue(T_EmptyOperations.op31().isEmpty());
        assertTrue(T_EmptyOperations.op32().isEmpty());
        assertTrue(T_EmptyOperations.op33().isEmpty());
        assertTrue(T_EmptyOperations.op34().isEmpty());
        assertTrue(T_EmptyOperations.op35().isEmpty());
        assertEquals(0L, T_EmptyOperations.op36().getExactSizeIfKnown());
        assertEquals(0L, T_EmptyOperations.op37().getExactSizeIfKnown());
        assertEquals(0L, T_EmptyOperations.op38().getExactSizeIfKnown());
        assertEquals(0L, T_EmptyOperations.op39().getExactSizeIfKnown());
        T_EmptyOperations.op40();
        assertEquals(0, T_EmptyOperations.op41().length);
        assertEquals(0, T_EmptyOperations.op42().length);
        assertEquals(0, T_EmptyOperations.op43().length);
        assertEquals(0, T_EmptyOperations.op44().length);
        assertEquals(0, T_EmptyOperations.op45().length);
        assertEquals(0, T_EmptyOperations.op46().length);
        assertEquals(0, T_EmptyOperations.op47().length);
        assertEquals(0, T_EmptyOperations.op48().length);
        assertEquals(0, T_EmptyOperations.op49().length);
        assertEquals(0, T_EmptyOperations.op50().length);
        assertEquals(0, T_EmptyOperations.op51().length);
    }

    @Test
    public void deniedCtor() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        try {
            new T_EmptyOperations();
            fail();
        } catch (SecurityException e) {
        }
    }
}
