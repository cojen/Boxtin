/*
 *  Copyright 2026 Cojen.org
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

import java.lang.reflect.Method;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests that a denied method which returns an array of primitives can be given a "value" or
 * "empty" deny action. The "value" action used to generate code which pushed an int instead of
 * an array reference, which caused a VerifyError when the caller class was loaded.
 *
 * @author Claude
 */
public class ArrayReturnValueTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ArrayReturnValueTest.class.getName());
    }

    private static Rules rulesFor(DenyAction action) {
        return RegressionSupport.rules(b -> {
            var str = b.forModule("java.base").forPackage("java.lang").forClass("String");
            str.denyMethod(action, "getBytes");
            str.denyMethod(action, "toCharArray");
            b.forModule("java.base").forPackage("java.util").forClass("BitSet")
                .denyMethod(action, "toLongArray");
        });
    }

    private static Object call(Class<?> clazz, String name, Class<?>[] types, Object... args)
        throws Exception
    {
        Method m = clazz.getMethod(name, types);
        return m.invoke(null, args);
    }

    @Test
    public void valueNull() throws Throwable {
        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.ArrayReturnCaller.class, rulesFor(DenyAction.value(null)));

        assertNull(call(c, "bytes", new Class[] {String.class}, "hello"));
        assertNull(call(c, "chars", new Class[] {String.class}, "hello"));
        assertNull(call(c, "longs", new Class[0]));
    }

    @Test
    public void valueIncompatible() throws Throwable {
        // A String cannot be returned as an array, and so null must be returned instead.
        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.ArrayReturnCaller.class, rulesFor(DenyAction.value("x")));

        assertNull(call(c, "bytes", new Class[] {String.class}, "hello"));
        assertNull(call(c, "chars", new Class[] {String.class}, "hello"));
        assertNull(call(c, "longs", new Class[0]));
    }

    @Test
    public void valueNumber() throws Throwable {
        // A number cannot be returned as an array either.
        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.ArrayReturnCaller.class, rulesFor(DenyAction.value(5)));

        assertNull(call(c, "bytes", new Class[] {String.class}, "hello"));
        assertNull(call(c, "longs", new Class[0]));
    }

    @Test
    public void empty() throws Throwable {
        // This is a control, which has always worked.
        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.ArrayReturnCaller.class, rulesFor(DenyAction.empty()));

        assertEquals(0, ((byte[]) call(c, "bytes", new Class[] {String.class}, "hello")).length);
        assertEquals(0, ((char[]) call(c, "chars", new Class[] {String.class}, "hello")).length);
        assertEquals(0, ((long[]) call(c, "longs", new Class[0])).length);
    }

    @Test
    public void standard() throws Throwable {
        // This is a control, which has always worked.
        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.ArrayReturnCaller.class, rulesFor(DenyAction.standard()));

        try {
            call(c, "bytes", new Class[] {String.class}, "hello");
            fail();
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof SecurityException);
        }
    }
}
