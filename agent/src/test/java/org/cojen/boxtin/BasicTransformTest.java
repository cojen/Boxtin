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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.io.PrintStream;

import java.util.Optional;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class BasicTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(BasicTransformTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        return new RulesBuilder().applyRules(RulesApplier.java_base());
    }

    @Test
    public void basic() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            System.setOut(System.out);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void lambda() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            Optional.of(System.out).ifPresent(System::setOut);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void reflect() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            System.class.getMethod("setOut", PrintStream.class);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void reflect2() throws Exception {
        if (runTransformed()) {
            return;
        }

        Method m = BasicTransformTest.class.getDeclaredMethod("setOut");

        try {
            m.invoke(null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof SecurityException);
        }
    }

    @Test
    public void reflectMH() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            MethodType mt = MethodType.methodType(void.class, PrintStream.class);
            MethodHandles.lookup().findStatic(System.class, "setOut", mt);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void reflectPass() throws Exception {
        if (runTransformed()) {
            return;
        }

        Object x = BasicTransformTest.class.getDeclaredMethod("okay", int.class).invoke(null, 10);
        assertEquals(10, (int) x);
    }

    @Test
    public void reflectPassMH() throws Throwable {
        if (runTransformed()) {
            return;
        }

        MethodType mt = MethodType.methodType(int.class, int.class);
        MethodHandle mh = MethodHandles.lookup().findStatic(BasicTransformTest.class, "okay", mt);
        assertEquals(10, (int) mh.invokeExact(10));
    }

    @Test
    public void doubleReflect() throws Exception {
        if (runTransformed()) {
            return;
        }

        // Cannot get access to the reflection methods via reflection.

        try {
            Class.class.getMethod("getMethod", String.class, Class[].class);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    private static void setOut() {
        System.setOut(System.out);
    }

    private static int okay(int x) {
        return x;
    }
}
