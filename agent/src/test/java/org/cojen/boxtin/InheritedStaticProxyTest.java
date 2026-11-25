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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests the transformation of classes which extend a class whose construction is denied (static
 * methods are inherited, and are explicitly denied by generated proxy methods), and the
 * transformation of method handle constants which are replaced by proxy methods.
 *
 * @author Claude
 */
public class InheritedStaticProxyTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(InheritedStaticProxyTest.class.getName());
    }

    private static Rules denyThreadConstruction() {
        return RegressionSupport.rules(b -> b.forModule("java.base").forPackage("java.lang")
                                       .forClass("Thread").denyAllConstructors());
    }

    /**
     * Proxy methods were generated with the ACC_NATIVE flag copied from the original method
     * (Thread.currentThread and Thread.holdsLock), which is illegal for a method which has a
     * Code attribute. The result was a ClassFormatError.
     */
    @Test
    public void nativeStaticMethods() throws Throwable {
        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.ThreadChild.class, denyThreadConstruction());

        boolean foundProxy = false;

        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals("currentThread")) {
                foundProxy = true;
            }
            assertFalse("Proxy must not be native: " + m, Modifier.isNative(m.getModifiers()));
        }

        assertTrue(foundProxy);
    }

    /**
     * Static methods which are redeclared in a super class (this hides the original method)
     * were visited multiple times, and so multiple proxies were generated with the same name
     * and descriptor. The result was a ClassFormatError.
     */
    @Test
    public void redeclaredStaticMethods() throws Throwable {
        var rules = RegressionSupport.rules
            (b -> b.forModule("java.base").forPackage("javax.net.ssl")
             .forClass("SSLSocketFactory").denyAllConstructors());

        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.SocketFactoryChild.class, rules);

        int count = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals("getDefault") && m.getParameterCount() == 0) {
                count++;
            }
        }

        assertEquals(1, count);
    }

    /**
     * Method handle constants for denied operations are replaced with proxy methods, which
     * must be referenced with a CONSTANT_InterfaceMethodref when the class is an interface.
     * Without it, an IncompatibleClassChangeError was thrown when the method handle was
     * resolved.
     */
    @Test
    public void methodHandleInInterface() throws Throwable {
        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.ConstructorRefInterface.class, denyThreadConstruction());

        assertTrue(c.isInterface());

        try {
            c.getMethod("make").invoke(null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof SecurityException);
        }
    }

    @Test
    public void methodHandleInClass() throws Throwable {
        // This is a control, which has always worked.
        Class<?> c = RegressionSupport.transformAndLink
            (RegressionTargets.ConstructorRefClass.class, denyThreadConstruction());

        assertFalse(c.isInterface());

        try {
            c.getMethod("make").invoke(null);
            fail();
        } catch (InvocationTargetException e) {
            assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof SecurityException);
        }
    }
}
