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
import java.lang.reflect.RecordComponent;

import org.junit.*;
import static org.junit.Assert.*;

import org.cojen.maker.ClassMaker;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ReflectionTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ReflectionTest.class.getName());
    }

    public ReflectionTest() {
    }

    @BeforeClass
    public static void setup() {
        // Force the "isAllowed3" variant to be used, which checks if the agent is null or not
        // each time.
        SecurityAgent.isAllowed(Object.class, Object.class, "", "");
    }

    @After
    public void teardown() {
        SecurityAgent.testActivate(null);
    }

    @Test
    public void notPublic() throws Exception {
        // Verify that special classes and methods don't have public access.

        assertEquals(0, Reflection.class.getConstructors().length);
        assertEquals(0, SecurityAgent.class.getConstructors().length);

        Method m = SecurityAgent.class.getDeclaredMethod("testActivate", Controller.class);
        int modifiers = m.getModifiers();
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
    }

    @Test
    public void sameModule() throws Exception {
        Reflection r = SecurityAgent.reflection();
        r.Class_getConstructor(ReflectionTest.class);

        Method[] methods = r.Class_getMethods(ReflectionTest.class);

        for (Method m : methods) {
            assertEquals(getClass(), m.getDeclaringClass());
        }
    }

    @Test
    public void noAgent() throws Exception {
        Reflection r = SecurityAgent.reflection();
        try {
            r.Class_getConstructor(StringBuilder.class);
            fail();
        } catch (SecurityException e) {
            // Security agent hasn't been activated.
        }
    }

    @Test
    public void getConstructor() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        r.Class_getConstructor(StringBuilder.class);

        try {
            r.Class_getConstructor(FileInputStream.class);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }

        try {
            r.Class_getConstructor(FileInputStream.class, String.class);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void getConstructors() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        Constructor[] ctors = r.Class_getConstructors(FileInputStream.class);
        assertEquals(0, ctors.length);

        ctors = r.Class_getConstructors(PrintStream.class);

        for (Constructor ctor : ctors) {
            assertEquals(OutputStream.class, ctor.getParameterTypes()[0]);
        }
    }

    @Test
    public void getDeclaredConstructor() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        r.Class_getDeclaredConstructor(StringBuilder.class);

        try {
            r.Class_getDeclaredConstructor(FileInputStream.class);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }

        try {
            r.Class_getDeclaredConstructor(FileInputStream.class, String.class);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void getDeclaredConstructors() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        Constructor[] ctors = r.Class_getDeclaredConstructors(FileInputStream.class);
        assertEquals(0, ctors.length);

        ctors = r.Class_getDeclaredConstructors(PrintStream.class);

        for (Constructor ctor : ctors) {
            assertEquals(OutputStream.class, ctor.getParameterTypes()[0]);
        }
    }

    @Test
    public void getDeclaredMethod() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        r.Class_getDeclaredMethod(StringBuilder.class, "append", Object.class);

        try {
            r.Class_getDeclaredMethod(StringBuilder.class, "xxx");
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }

        try {
            r.Class_getDeclaredMethod(Class.class, "getConstructors");
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void getDeclaredMethods() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        Method[] methods = r.Class_getDeclaredMethods(System.class);

        for (Method m : methods) {
            assertNotEquals("exit", m.getName());
        }
    }

    @Test
    public void getEnclosingConstructor() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        class Outer {
            public Outer() {
                class Foo {
                }

                Constructor ctor = r.Class_getEnclosingConstructor(Foo.class);
                assertNotNull(ctor);

                assertNull(r.Class_getEnclosingMethod(Foo.class));
            }
        }

        new Outer();
    }

    @Test
    public void getEnclosingMethod() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        class Foo {
        }

        Method m = r.Class_getEnclosingMethod(Foo.class);
        assertEquals("getEnclosingMethod", m.getName());

        assertNull(r.Class_getEnclosingConstructor(Foo.class));
    }

    @Test
    public void getMethod() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        r.Class_getMethod(StringBuilder.class, "append", Object.class);

        try {
            r.Class_getMethod(StringBuilder.class, "xxx");
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }

        try {
            r.Class_getMethod(Class.class, "getDeclaredMethods");
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void getMethods() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        Method[] methods = r.Class_getMethods(System.class);

        for (Method m : methods) {
            assertNotEquals("exit", m.getName());
        }
    }

    @Test
    public void getRecordComponents() throws Exception {
        SecurityAgent.testActivate(new TestController());
        Reflection r = SecurityAgent.reflection();

        String name = "boxtin.ReflectionTest.TestRecord";

        ClassMaker cm = ClassMaker.beginExternal(name).public_();

        cm.addField(String.class, "a");
        cm.addField(int.class, "b");

        cm.asRecord();

        Class<?> clazz = cm.finish();

        RecordComponent[] components = r.Class_getRecordComponents(clazz);

        for (RecordComponent rc : components) {
            assertNotEquals("b", rc.getName());
        }
    }

    @Test
    public void bind() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodType mt = MethodType.methodType(StringBuilder.class, int.class);
        r.MethodHandles$Lookup_bind(lookup, new StringBuilder(), "append", mt);

        try {
            r.MethodHandles$Lookup_bind(lookup, new StringBuilder(), "xxx", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }

        try {
            mt = MethodType.methodType(Method[].class);
            r.MethodHandles$Lookup_bind(lookup, String.class, "getDeclaredMethods", mt);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void findConstructor() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodType mt = MethodType.methodType(void.class);
        r.MethodHandles$Lookup_findConstructor(lookup, StringBuilder.class, mt);

        try {
            r.MethodHandles$Lookup_findConstructor(lookup, FileInputStream.class, mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }

        try {
            mt = MethodType.methodType(void.class, String.class);
            r.MethodHandles$Lookup_findConstructor(lookup, FileInputStream.class, mt);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void findSpecial() throws Throwable {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        class SubFile extends File {
            static MethodHandles.Lookup lookup = MethodHandles.lookup();

            SubFile(String path) {
                super(path);
            }

            @Override
            public boolean delete() {
                // do nothing, but return true anyhow
                return true;
            }
        }

        MethodHandles.Lookup lookup = SubFile.lookup;

        MethodType mt = MethodType.methodType(boolean.class);

        try {
            r.MethodHandles$Lookup_findSpecial(lookup, File.class, "delete", mt, SubFile.class);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        MethodHandle mh = r.MethodHandles$Lookup_findSpecial
            (lookup, SubFile.class, "delete", mt, SubFile.class);

        assertTrue((boolean) mh.invoke(new SubFile("xxx")));
    }

    @Test
    public void findStatic() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodType mt = MethodType.methodType(String.class, int.class);
        r.MethodHandles$Lookup_findStatic(lookup, String.class, "valueOf", mt);

        try {
            r.MethodHandles$Lookup_findStatic(lookup, String.class, "xxx", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }

        try {
            mt = MethodType.methodType(String.class, String.class);
            r.MethodHandles$Lookup_findStatic(lookup, System.class, "getProperty", mt);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void findVirtual() throws Exception {
        SecurityAgent.testActivate(new DefaultController(false));
        Reflection r = SecurityAgent.reflection();

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodType mt = MethodType.methodType(StringBuilder.class, Object.class);
        r.MethodHandles$Lookup_findVirtual(lookup, StringBuilder.class, "append", mt);

        try {
            r.MethodHandles$Lookup_findVirtual(lookup, StringBuilder.class, "xxx", mt);
            fail();
        } catch (NoSuchMethodException e) {
            // Expected.
        }

        try {
            mt = MethodType.methodType(Method[].class);
            r.MethodHandles$Lookup_findVirtual(lookup, Class.class, "getDeclaredMethods", mt);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    static class TestController implements Controller, Rules, Rules.ForClass {
        TestController() {
        }

        @Override
        public Rules rulesForCaller(Module module) {
            return this;
        }

        @Override
        public Rules rulesForTarget() {
            return this;
        }

        @Override
        public boolean isAllAllowed() {
            return false;
        }

        @Override
        public ForClass forClass(CharSequence packageName, CharSequence className) {
            return this;
        }

        @Override
        public boolean printTo(Appendable a, String indent, String plusIndent) {
            return false;
        }

        @Override
        public Rule ruleForConstructor(CharSequence descriptor) {
            return Rule.allow();
        }

        @Override
        public Rule ruleForMethod(CharSequence name, CharSequence descriptor) {
            return name.equals("a") ? Rule.allow() : Rule.denyAtCaller();
        }

        @Override
        public boolean isAnyConstructorDenied() {
            return false;
        }

        @Override
        public boolean isAnyMethodDenied() {
            return true;
        }

        @Override
        public boolean isAnyDeniedAtCaller() {
            return true;
        }

        @Override
        public boolean isAnyDeniedAtTarget() {
            return true;
        }
    }
}
