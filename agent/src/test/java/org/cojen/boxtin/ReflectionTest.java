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

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Set;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ReflectionTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ReflectionTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        RulesBuilder b = new RulesBuilder().applyRules(RulesApplier.java_base());

        b.forModule("org.cojen.boxtin")
            .forPackage("org.cojen.boxtin")
            .forClass("ReflectionTest$SubFile").allowAll()
            ;

        return b;
    }

    @Test
    public void notPublic() throws Exception {
        // Verify that special classes and methods don't have public access.

        assertEquals(0, SecurityAgent.class.getConstructors().length);

        Method m = SecurityAgent.class.getDeclaredMethod("testActivate", Controller.class);
        int modifiers = m.getModifiers();
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
    }

    @Test
    public void sameModule() throws Exception {
        if (runTransformed()) {
            return;
        }

        assertNotNull(ReflectionTest.class.getConstructor());

        Method[] methods = ReflectionTest.class.getDeclaredMethods();

        for (Method m : methods) {
            assertEquals(getClass(), m.getDeclaringClass());
        }
    }

    @Test
    public void getConstructor() throws Exception {
        if (runTransformed()) {
            return;
        }

        assertNotNull(StringBuilder.class.getConstructor());

        try {
            FileInputStream.class.getConstructor();
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertNotThrownByCustomCheck(e);
        }

        try {
            FileInputStream.class.getConstructor(String.class);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }
    }

    @Test
    public void getConstructors() throws Exception {
        if (runTransformed()) {
            return;
        }

        Constructor[] ctors = FileInputStream.class.getConstructors();
        assertEquals(0, ctors.length);

        ctors = PrintStream.class.getConstructors();

        for (Constructor ctor : ctors) {
            assertEquals(OutputStream.class, ctor.getParameterTypes()[0]);
        }
    }

    @Test
    public void getDeclaredConstructor() throws Exception {
        if (runTransformed()) {
            return;
        }

        assertNotNull(StringBuilder.class.getDeclaredConstructor());

        try {
            FileInputStream.class.getDeclaredConstructor();
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertNotThrownByCustomCheck(e);
        }

        try {
            FileInputStream.class.getDeclaredConstructor(String.class);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }
    }

    @Test
    public void getDeclaredConstructors() throws Exception {
        if (runTransformed()) {
            return;
        }

        Constructor[] ctors = FileInputStream.class.getDeclaredConstructors();
        assertEquals(0, ctors.length);

        ctors = PrintStream.class.getDeclaredConstructors();

        for (Constructor ctor : ctors) {
            assertEquals(OutputStream.class, ctor.getParameterTypes()[0]);
        }
    }

    @Test
    public void getDeclaredMethod() throws Exception {
        if (runTransformed()) {
            return;
        }

        assertNotNull(StringBuilder.class.getDeclaredMethod("append", Object.class));

        try {
            StringBuilder.class.getDeclaredMethod("xxx");
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertNotThrownByCustomCheck(e);
        }

        try {
            Class.class.getDeclaredMethod("getConstructors");
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }
    }

    @Test
    public void getDeclaredMethods() throws Exception {
        if (runTransformed()) {
            return;
        }

        Method[] methods = System.class.getDeclaredMethods();

        for (Method m : methods) {
            assertNotEquals("exit", m.getName());
        }
    }

    @Test
    public void getEnclosingConstructor() throws Exception {
        if (runTransformed(Outer.class, new Outer().fooClass)) {
            return;
        }

        Class fooClass = new Outer().fooClass;

        Constructor ctor = fooClass.getEnclosingConstructor();
        assertEquals("org.cojen.boxtin.ReflectionTest$Outer", ctor.getName());

        assertNull(fooClass.getEnclosingMethod());
    }

    public static class Outer {
        public final Class fooClass;

        public Outer() {
            class Foo {
            }
            fooClass = Foo.class;
        }
    }

    @Test
    public void getEnclosingMethod() throws Exception {
        class Foo {
        }

        if (runTransformed(Foo.class)) {
            return;
        }

        Method m = Foo.class.getEnclosingMethod();
        assertEquals("getEnclosingMethod", m.getName());

        assertNull(Foo.class.getEnclosingConstructor());
    }

    @Test
    public void getMethod() throws Exception {
        if (runTransformed()) {
            return;
        }

        assertNotNull(StringBuilder.class.getMethod("append", Object.class));

        try {
            StringBuilder.class.getMethod("xxx");
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertNotThrownByCustomCheck(e);
        }

        try {
            Class.class.getMethod("getDeclaredMethods");
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }
    }

    @Test
    public void getMethods() throws Exception {
        if (runTransformed()) {
            return;
        }

        Method[] methods = System.class.getMethods();

        for (Method m : methods) {
            assertNotEquals("exit", m.getName());
        }
    }

    @Test
    public void bind() throws Exception {
        if (runTransformed()) {
            return;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodType mt = MethodType.methodType(StringBuilder.class, int.class);
        lookup.bind(new StringBuilder(), "append", mt);

        try {
            lookup.bind(new StringBuilder(), "xxx", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertNotThrownByCustomCheck(e);
        }

        try {
            mt = MethodType.methodType(Method[].class);
            lookup.bind(String.class, "getDeclaredMethods", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }
    }

    @Test
    public void findConstructor() throws Exception {
        if (runTransformed()) {
            return;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodType mt = MethodType.methodType(void.class);
        lookup.findConstructor(StringBuilder.class, mt);

        try {
            lookup.findConstructor(FileInputStream.class, mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertNotThrownByCustomCheck(e);
        }

        try {
            mt = MethodType.methodType(void.class, String.class);
            lookup.findConstructor(FileInputStream.class, mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }
    }

    @Test
    public void findSpecial() throws Throwable {
        if (runTransformed()) {
            return;
        }

        MethodHandles.Lookup lookup = SubFile.lookup;

        MethodType mt = MethodType.methodType(boolean.class);

        try {
            lookup.findSpecial(File.class, "delete", mt, SubFile.class);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }

        MethodHandle mh = lookup.findSpecial(SubFile.class, "delete", mt, SubFile.class);

        assertTrue((boolean) mh.invoke(new SubFile("xxx")));
    }

    public static class SubFile extends File {
        public static MethodHandles.Lookup lookup = MethodHandles.lookup();

        public SubFile(String path) {
            super(path);
        }

        @Override
        public boolean delete() {
            // do nothing, but return true anyhow
            return true;
        }
    }

    @Test
    public void findStatic() throws Exception {
        if (runTransformed()) {
            return;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodType mt = MethodType.methodType(String.class, int.class);
        lookup.findStatic(String.class, "valueOf", mt);

        try {
            lookup.findStatic(String.class, "xxx", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertNotThrownByCustomCheck(e);
        }

        try {
            mt = MethodType.methodType(String.class, String.class);
            lookup.findStatic(System.class, "getProperty", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }
    }

    @Test
    public void findVirtual() throws Exception {
        if (runTransformed()) {
            return;
        }

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodType mt = MethodType.methodType(StringBuilder.class, Object.class);
        lookup.findVirtual(StringBuilder.class, "append", mt);

        try {
            lookup.findVirtual(StringBuilder.class, "xxx", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertNotThrownByCustomCheck(e);
        }

        try {
            mt = MethodType.methodType(Method[].class);
            lookup.findVirtual(Class.class, "getDeclaredMethods", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
            assertThrownByCustomCheck(e);
        }
    }

    private static void assertNotThrownByCustomCheck(NoSuchMethodException e) {
        assertNotEquals("org.cojen.boxtin.CustomActions", e.getStackTrace()[0].getClassName());
    }

    private static void assertThrownByCustomCheck(NoSuchMethodException e) {
        assertEquals("org.cojen.boxtin.CustomActions", e.getStackTrace()[0].getClassName());
    }
}
