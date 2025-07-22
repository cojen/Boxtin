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

import java.lang.reflect.InvocationTargetException;

import java.util.Collections;
import java.util.Enumeration;
import java.util.ResourceBundle;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ResourceBundleTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ResourceBundleTransformTest.class.getName());
    }

    private static final String PROP_KEY = ResourceBundleTransformTest.class.getName() + ".class";

    @BeforeClass
    public static void setup() {
        System.getProperties().put(PROP_KEY + ".Sub", Sub.class);
        System.getProperties().put(PROP_KEY + ".SubSub", SubSub.class);
    }

    @AfterClass
    public static void teardown() {
        System.getProperties().remove(PROP_KEY + ".Sub");
        System.getProperties().remove(PROP_KEY + ".SubSub");
    }

    @Override
    protected RulesBuilder builder() {
        RulesBuilder b = new RulesBuilder().applyRules(RulesApplier.java_base());

        b.forModule("java.base").forPackage("java.lang").forClass("System").allowAll();

        b.forModule("org.cojen.boxtin").forPackage("org.cojen.boxtin")
            .forClass("ResourceBundleTransformTest$Sub").allowAll();

        return b;
    }

    @Test
    public void basic() throws Throwable {
        final String name = "org.cojen.boxtin.messages";

        ResourceBundle bundle = ResourceBundle.getBundle(name);
        assertEquals("hello", bundle.getString("key1"));

        if (runTransformed()) {
            return;
        }

        try {
            ResourceBundle.getBundle(name, String.class.getModule());
            fail();
        } catch (SecurityException e) {
        }

        // Verify that subclassing doesn't bypass the caller-side access check to an inherited
        // static method.

        Class<?> sub = inject((Class) System.getProperties().get(PROP_KEY + ".Sub"));
        Object subObj = sub.getConstructor().newInstance();

        try {
            sub.getMethod("getBundle_", String.class, Module.class)
                .invoke(subObj, name, String.class.getModule());
            fail();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof SecurityException);
        }

        Class<?> subsub = inject((Class) System.getProperties().get(PROP_KEY + ".SubSub"));
        Object subsubObj = subsub.getConstructor().newInstance();

        try {
            subsub.getMethod("getBundle_", String.class, Module.class)
                .invoke(subsubObj, name, String.class.getModule());
            fail();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof SecurityException);
        }
    }

    public static class Sub extends ResourceBundle {
        @Override
        public Enumeration<String> getKeys() {
            return Collections.emptyEnumeration();
        }

        @Override
        protected Object handleGetObject(String key) {
            return null;
        }

        public ResourceBundle getBundle_(String name, Module module) {
            return getBundle(name, module);
        }
    }

    public static class SubSub extends Sub {
        @Override
        public ResourceBundle getBundle_(String name, Module module) {
            return getBundle(name, module);
        }
    }
}
