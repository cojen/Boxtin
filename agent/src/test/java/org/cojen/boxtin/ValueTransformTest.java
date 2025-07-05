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

    @Override
    protected RulesBuilder builder() {
        var b = new RulesBuilder();

        b.forModule("org.cojen.boxtin").forPackage("org.cojen.boxtin")
            .forClass("ValueOperations")
            .allowAllConstructors()
            .denyVariant(DenyAction.value(null))
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
            .denyMethod(DenyAction.value("hello"), "op28")
            .denyMethod(DenyAction.value(123), "op29")
            .denyMethod(DenyAction.value(Long.MAX_VALUE), "op30")
            .denyMethod(DenyAction.value(Double.MAX_VALUE), "op31")
            .denyMethod(DenyAction.value(Float.MAX_VALUE), "op32")
            .denyMethod(DenyAction.value(Short.MAX_VALUE), "op33")
            .denyMethod(DenyAction.value(Byte.MAX_VALUE), "op34")
            .denyMethod(DenyAction.value(Character.MAX_VALUE), "op35")
            .denyMethod(DenyAction.value(true), "op36")
            .denyMethod(DenyAction.value(345), "op37")
            .denyMethod(DenyAction.value(345), "op38")
            .denyMethod(DenyAction.value(345L), "op39")
            .denyMethod(DenyAction.value(345.0d), "op40")
            .denyMethod(DenyAction.value(345.0f), "op41")
            .denyMethod(DenyAction.value((byte) 12), "op42")
            .denyMethod(DenyAction.value((short) 1234), "op43")
            .denyMethod(DenyAction.value(false), "op44")
            .denyMethod(DenyAction.value('a'), "op45")
            ;

        b.forModule("java.base").allowAll();

        return b;
    }

    @Test
    public void values() throws Exception {
        if (runTransformed()) {
            return;
        }

        ValueOperations.op1();
        assertTrue(ValueOperations.op2());
        assertEquals('\0', ValueOperations.op3());
        assertEquals(5, ValueOperations.op4());
        assertEquals('a', ValueOperations.op5());
        assertEquals(-1, ValueOperations.op6());
        assertEquals(5, ValueOperations.op7());
        assertEquals(100, ValueOperations.op8());
        assertEquals(-1, ValueOperations.op9());
        assertEquals(5, ValueOperations.op10());
        assertEquals(1000, ValueOperations.op11());
        assertEquals(-1, ValueOperations.op12());
        assertEquals(5, ValueOperations.op13());
        assertEquals(100_000, ValueOperations.op14());
        assertEquals(0, ValueOperations.op15());
        assertEquals(1, ValueOperations.op16());
        assertEquals(999_999_999_999L, ValueOperations.op17());
        assertTrue(0.0f == ValueOperations.op18());
        assertTrue(1.0f == ValueOperations.op19());
        assertTrue(2.0f == ValueOperations.op20());
        assertTrue(3.14f == ValueOperations.op21());
        assertTrue(0.0d ==  ValueOperations.op22());
        assertTrue(1.0d ==  ValueOperations.op23());
        assertTrue(3.14159d == ValueOperations.op24());
        assertEquals("hello", ValueOperations.op25());
        assertNull(ValueOperations.op26());
        assertNull(ValueOperations.op27());
        assertEquals("hello", ValueOperations.op28());
        assertEquals(123, (int) ValueOperations.op29());
        assertEquals(Long.MAX_VALUE, (long) ValueOperations.op30());
        assertTrue(Double.MAX_VALUE == (double) ValueOperations.op31());
        assertTrue(Float.MAX_VALUE == (float) ValueOperations.op32());
        assertEquals(Short.MAX_VALUE, (short) ValueOperations.op33());
        assertEquals(Byte.MAX_VALUE, (byte) ValueOperations.op34());
        assertEquals(Character.MAX_VALUE, (char) ValueOperations.op35());
        assertTrue(ValueOperations.op36());
        assertEquals(345, ValueOperations.op37().intValue());
        assertNull(ValueOperations.op38());
        assertNull(ValueOperations.op39());
        assertNull(ValueOperations.op40());
        assertNull(ValueOperations.op41());
        assertNull(ValueOperations.op42());
        assertNull(ValueOperations.op43());
        assertNull(ValueOperations.op44());
        assertNull(ValueOperations.op45());
    }

    @Test
    public void deniedCtor() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            new ValueOperations();
            fail();
        } catch (SecurityException e) {
        }
    }
}
