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

package org.cojen.boxtin.tests;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import java.lang.reflect.InvocationTargetException;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.MethodMaker;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Defining of hidden classes should be allowed, and security checks should be applied to them.
 *
 * @author Brian S. O'Neill
 */
public class HiddenClassTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(HiddenClassTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        basic(false);
        basic(true);
    }

    private void basic(boolean withClassData) throws Exception {
        ClassMaker cm = ClassMaker.beginExternal("org.cojen.boxtin.tests.X").public_();
        MethodMaker mm = cm.addMethod(null, "exit").public_().static_();
        mm.var(System.class).invoke("exit", 1);

        byte[] bytes = cm.finishBytes();

        var lookup = MethodHandles.lookup();

        if (!withClassData) {
            lookup = lookup.defineHiddenClass(bytes, false);
        } else {
            lookup = lookup.defineHiddenClassWithClassData(bytes, "data", false);
        }

        Class<?> clazz = lookup.lookupClass();

        try {
            clazz.getMethod("exit").invoke(null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof SecurityException);
        }
    }

    @Test
    public void handle() throws Throwable {
        MethodMaker mm = MethodMaker.begin(MethodHandles.lookup(), null, "exit").public_();
        mm.var(System.class).invoke("exit", 1);
        MethodHandle mh = mm.finish();

        try {
            mh.invokeExact();
            fail();
        } catch (SecurityException e) {
        }
    }
}
