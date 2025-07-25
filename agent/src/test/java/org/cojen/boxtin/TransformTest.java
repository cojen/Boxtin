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

import java.io.InputStream;

import java.util.List;
import java.util.Set;

import org.junit.Ignore;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
@Ignore
public abstract class TransformTest {
    protected static final StackWalker WALKER;

    static {
        WALKER = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), 2);
    }

    private static final ThreadLocal<Boolean> cRunning = ThreadLocal.withInitial(() -> false);

    private static final SoftCache<Object, Class<?>> cTransformed = new SoftCache<>();

    protected abstract RulesBuilder builder() throws Exception;

    /**
     * Transforms the current class using a SecurityAgent, and then runs the caller's test
     * method. Note that any before and after test actions aren't performed.
     *
     * @return true if the test should simply return because it already ran
     */
    protected boolean runTransformed(Class... dependencies) throws Exception {
        if (cRunning.get()) {
            return false;
        }

        RulesBuilder b;
        try {
            b = builder();
        } catch (SecurityException e) {
            // Access to the RulesBuilder class is denied.
            return false;
        }

        b.forModule("org.cojen.boxtin")
            .forPackage("org.cojen.boxtin")
            .forClass("Caller").allowAll()
            .forClass("TransformTest").allowAll()
            .forClass("TestUtils").allowAll()
            ;

        Rules rules = b.build();

        var frame = WALKER.walk(s -> s.skip(1).findFirst()).get();
        Class<?> original = frame.getDeclaringClass();

        Object key = original;
        if (dependencies.length > 0) {
            key = List.of(original, List.of(dependencies));
        }

        Class<?> transformed = cTransformed.get(key);

        var agent = SecurityAgent.testActivate(module -> rules);

        try {
            if (transformed == null) {
                try {
                    var injector = new Injector(original.getClassLoader(), agent);
                    transformed = injector.inject(original);

                    for (Class c : dependencies) {
                        injector.inject(c);
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }

                cTransformed.put(key, transformed);
            }

            cRunning.set(true);

            Object instance = transformed.getConstructor().newInstance();

            try {
                transformed.getMethod(frame.getMethodName()).invoke(instance);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception re) {
                    throw re;
                }
                if (cause instanceof Error er) {
                    throw er;
                }
                throw e;
            }

            return true;
        } finally {
            cRunning.set(false);
            SecurityAgent.testActivate(null);
        }
    }

    protected Class<?> inject(Class<?> original) throws Throwable {
        return ((Injector) getClass().getClassLoader()).inject(original);
    }

    private static class Injector extends ClassLoader {
        private final SecurityAgent mAgent;

        private Injector(ClassLoader parent, SecurityAgent agent) {
            super(parent);
            mAgent = agent;
        }

        Class<?> inject(Class<?> original) throws Throwable {
            return loadAndTransformClass(original.getName());
        }

        private Class<?> loadAndTransformClass(String className)
            throws Throwable
        {
            String pathName = className.replace('.', '/');

            byte[] bytes;
            try (InputStream in = getParent().getResourceAsStream(pathName + ".class")) {
                bytes = in.readAllBytes();
            }

            byte[] xbytes = mAgent.doTransform(getUnnamedModule(), pathName, bytes);

            if (xbytes == null) {
                xbytes = bytes;
            }

            return defineClass(className, xbytes, 0, xbytes.length);
        }
    }
}
