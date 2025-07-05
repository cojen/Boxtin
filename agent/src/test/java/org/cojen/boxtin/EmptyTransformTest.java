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

    @Override
    protected RulesBuilder builder() {
        var b = new RulesBuilder();

        RulesBuilder.ClassScope forClass = b.forModule("org.cojen.boxtin")
            .forPackage("org.cojen.boxtin").forClass("EmptyOperations");

        for (int i=1; i<=NUM; i++) {
            forClass.denyMethod(DenyAction.empty(), "op" + i);
        }

        forClass.allowAllConstructors().denyVariant(DenyAction.empty());

        b.forModule("java.base").allowAll();

        return b;
    }

    @Test
    public void empty() throws Exception {
        if (runTransformed()) {
            return;
        }

        assertNotNull(EmptyOperations.op1());
        assertFalse(EmptyOperations.op2());
        assertFalse(EmptyOperations.op3());
        assertEquals((byte) 0, EmptyOperations.op4());
        assertEquals((byte) 0, (byte) EmptyOperations.op5());
        assertEquals('\0', EmptyOperations.op6());
        assertEquals('\0', (char) EmptyOperations.op7());
        assertEquals((short) 0, EmptyOperations.op8());
        assertEquals((short) 0, (short) EmptyOperations.op9());
        assertEquals(0, EmptyOperations.op10());
        assertEquals(0, (int) EmptyOperations.op11());
        assertEquals(0L, EmptyOperations.op12());
        assertEquals(0L, (long) EmptyOperations.op13());
        assertTrue(0.0f == EmptyOperations.op14());
        assertTrue(0.0f == (float) EmptyOperations.op15());
        assertTrue(0.0d == EmptyOperations.op16());
        assertTrue(0.0d == (double) EmptyOperations.op17());
        assertTrue(EmptyOperations.op18().isEmpty());
        assertFalse(EmptyOperations.op19().iterator().hasNext());
        assertTrue(EmptyOperations.op20().isEmpty());
        assertTrue(EmptyOperations.op21().isEmpty());
        assertTrue(EmptyOperations.op22().isEmpty());
        assertTrue(EmptyOperations.op23().isEmpty());
        assertTrue(EmptyOperations.op24().isEmpty());
        assertTrue(EmptyOperations.op25().toList().isEmpty());
        assertFalse(EmptyOperations.op26().hasMoreElements());
        assertFalse(EmptyOperations.op27().hasNext());
        assertTrue(EmptyOperations.op28().isEmpty());
        assertFalse(EmptyOperations.op29().hasNext());
        assertTrue(EmptyOperations.op30().isEmpty());
        assertTrue(EmptyOperations.op31().isEmpty());
        assertTrue(EmptyOperations.op32().isEmpty());
        assertTrue(EmptyOperations.op33().isEmpty());
        assertTrue(EmptyOperations.op34().isEmpty());
        assertTrue(EmptyOperations.op35().isEmpty());
        assertEquals(0L, EmptyOperations.op36().getExactSizeIfKnown());
        assertEquals(0L, EmptyOperations.op37().getExactSizeIfKnown());
        assertEquals(0L, EmptyOperations.op38().getExactSizeIfKnown());
        assertEquals(0L, EmptyOperations.op39().getExactSizeIfKnown());
        EmptyOperations.op40();
        assertEquals(0, EmptyOperations.op41().length);
        assertEquals(0, EmptyOperations.op42().length);
        assertEquals(0, EmptyOperations.op43().length);
        assertEquals(0, EmptyOperations.op44().length);
        assertEquals(0, EmptyOperations.op45().length);
        assertEquals(0, EmptyOperations.op46().length);
        assertEquals(0, EmptyOperations.op47().length);
        assertEquals(0, EmptyOperations.op48().length);
        assertEquals(0, EmptyOperations.op49().length);
        assertEquals(0, EmptyOperations.op50().length);
        assertEquals(0, EmptyOperations.op51().length);
    }

    @Test
    public void deniedCtor() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            new EmptyOperations();
            fail();
        } catch (SecurityException e) {
        }
    }
}
