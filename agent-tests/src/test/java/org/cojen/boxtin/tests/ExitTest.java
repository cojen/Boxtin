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
import java.lang.invoke.MethodType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.OptionalInt;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ExitTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ExitTest.class.getName());
    }

    private static final SecurityException INIT_EX;

    static {
        SecurityException ex;
        try {
            System.exit(1);
            ex = null;
        } catch (SecurityException e) {
            ex = e;
        }
        INIT_EX = ex;
    }

    @Test
    public void clinit() {
        assertNotNull(INIT_EX);
    }

    @Test
    public void system() throws Exception {
        try {
            System.exit(1);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void runtime() throws Exception {
        try {
            Runtime.getRuntime().exit(1);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void lambda() throws Exception {
        try {
            OptionalInt.of(1).ifPresent(System::exit);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void reflect() throws Exception {
        try {
            System.class.getMethod("exit", int.class);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void reflect2() throws Exception {
        Method m = ExitTest.class.getDeclaredMethod("exit");

        try {
            m.invoke(null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof SecurityException);
        }
    }

    @Test
    public void reflectMH() throws Exception {
        try {
            MethodType mt = MethodType.methodType(void.class, int.class);
            MethodHandles.lookup().findStatic(System.class, "exit", mt);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void reflectPass() throws Exception {
        Object x = ExitTest.class.getDeclaredMethod("okay", int.class).invoke(null, 10);
        assertEquals(10, (int) x);
    }

    @Test
    public void reflectPassMH() throws Throwable {
        MethodType mt = MethodType.methodType(int.class, int.class);
        MethodHandle mh = MethodHandles.lookup().findStatic(ExitTest.class, "okay", mt);
        assertEquals(10, (int) mh.invokeExact(10));
    }

    @Test
    public void reflectFilter() throws Exception {
        Method[] methods = System.class.getMethods();

        for (Method m : methods) {
            assertNotEquals("exit", m.getName());
        }
    }

    @Test
    public void doubleReflect() throws Exception {
        // Cannot get access to the reflection methods via reflection.

        try {
            Class.class.getMethod("getMethod", String.class, Class[].class);
            fail();
        } catch (SecurityException e) {
            // Expected.
        } 
   }

    private static void exit() {
        System.exit(1);
    }

    private static int okay(int x) {
        return x;
    }
}
