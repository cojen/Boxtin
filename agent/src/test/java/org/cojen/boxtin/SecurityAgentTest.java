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

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.jar.JarFile;

import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class SecurityAgentTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(SecurityAgentTest.class.getName());
    }

    @Test
    public void badController() throws Exception {
        assertNull(SecurityAgent.initController(null));
        assertNull(SecurityAgent.initController(""));
        assertTrue(SecurityAgent.initController("default") instanceof DefaultController);

        try {
            SecurityAgent.initController("Foo");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().endsWith("Foo"));
        }

        try {
            SecurityAgent.initController("Foo=bar");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().endsWith("Foo"));
        }

        try {
            SecurityAgent.initController("java.lang.String");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Controller interface"));
        }

        try {
            SecurityAgent.initController(Broken1.class.getName());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("public constructor"));
        }

        try {
            SecurityAgent.initController(Broken2.class.getName());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getCause() instanceof InstantiationException);
        }

        try {
            SecurityAgent.initController(Broken3.class.getName());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getCause() instanceof InstantiationException);
        }

        try {
            SecurityAgent.initController(Broken4.class.getName());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getCause() instanceof InstantiationException);
        }

        try {
            SecurityAgent.initController(Broken4.class.getName() + "=bar");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getCause() instanceof InstantiationException);
        }

        try {
            SecurityAgent.initController(Broken5.class.getName());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
    }

    public static abstract class Broken1 implements Controller {
        private Broken1() {
        }
    }

    public static abstract class Broken2 implements Controller {
        public Broken2() {
        }
    }

    public static abstract class Broken3 implements Controller {
        public Broken3(String args) {
        }
    }

    public static abstract class Broken4 implements Controller {
        public Broken4() {
        }

        public Broken4(String args) {
        }
    }

    public static class Broken5 implements Controller {
        public Broken5() {
            throw null;
        }

        @Override
        public Rules rulesForCaller(Module caller) {
            return null;
        }
    }

    @Test
    public void initController() throws Exception {
        var c = (Controller1) SecurityAgent.initController(Controller1.class.getName());
        assertNull(c.args);

        c = (Controller1) SecurityAgent.initController(Controller1.class.getName() + "=bar");
        assertEquals("bar", c.args);
    }

    public static class Controller1 implements Controller {
        public final String args;

        public Controller1() {
            args = null;
        }

        public Controller1(String args) {
            this.args = args;
        }

        @Override
        public Rules rulesForCaller(Module caller) {
            return null;
        }
    }

    @Test
    public void logException() throws Exception {
        Logger logger = Logger.getLogger(SecurityAgent.class.getName());
        logger.setUseParentHandlers(false);

        final ArrayList<LogRecord> records = new ArrayList<>();

        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        logger.addHandler(handler);

        SecurityAgent.logException(new Exception());
        SecurityAgent.logException(new Exception("hello"));
        SecurityAgent.logException("hello", new IllegalArgumentException());

        logger.setUseParentHandlers(true);
        logger.removeHandler(handler);

        assertEquals(3, records.size());

        assertEquals(Exception.class.getName(), records.get(0).getMessage());
        assertEquals(Exception.class, records.get(0).getThrown().getClass());

        assertEquals("hello", records.get(1).getMessage());
        assertEquals(Exception.class, records.get(1).getThrown().getClass());

        assertEquals("hello", records.get(2).getMessage());
        assertEquals(IllegalArgumentException.class, records.get(2).getThrown().getClass());
    }

    @Test
    public void activate() throws Exception {
        try {
            SecurityAgent.activate(null);
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            SecurityAgent.activate(new DefaultController());
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            SecurityAgent.premain("", null);
            fail();
        } catch (NullPointerException e) {
        }

        var fake = new FakeInstrumentation();
        SecurityAgent.premain(null, fake);

        try {
            SecurityAgent.premain(null, fake);
            fail();
        } catch (SecurityException e) {
        }

        SecurityAgent.activate(null);

        try {
            SecurityAgent.activate(null);
            fail();
        } catch (SecurityException e) {
        }

        SecurityAgent.testActivate(null);

        assertEquals(2, fake.mAdded.size());
        assertEquals(1, fake.mRemoved.size());
        assertTrue(fake.mAdded.contains(fake.mRemoved.get(0)));
        assertEquals(1, fake.mRetransformed.size());
        assertEquals(java.lang.invoke.MethodHandles.Lookup.class, fake.mRetransformed.get(0));
    }

    static class FakeInstrumentation implements Instrumentation {
        final List<ClassFileTransformer> mAdded = new ArrayList<>();
        final List<ClassFileTransformer> mRemoved = new ArrayList<>();
        final List<Class> mRetransformed = new ArrayList<>();

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            mAdded.add(transformer);
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            mAdded.add(transformer);
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            if (mAdded.contains(transformer)) {
                mRemoved.add(transformer);
                return true;
            }
            return false;
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) {
            for (Class clazz : classes) {
                mRetransformed.add(clazz);
            }
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModifiableClass(Class<?> theClass) {
            return false;
        }

        @Override
        public Class[] getAllLoadedClasses() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class[] getInitiatedClasses(ClassLoader loader) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void redefineModule(Module module,
                                   Set<Module> extraReads,
                                   Map<String, Set<Module>> extraExports,
                                   Map<String, Set<Module>> extraOpens,
                                   Set<Class<?>> extraUses,
                                   Map<Class<?>, List<Class<?>>> extraProvides)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }
    }
}
