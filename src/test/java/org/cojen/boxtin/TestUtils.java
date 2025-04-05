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

import java.lang.instrument.IllegalClassFormatException;

import java.lang.reflect.InvocationTargetException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
@org.junit.Ignore
public class TestUtils {
    private static volatile Rules javaBaseRules;

    public static synchronized Rules javaBaseRules()
        throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException
    {
        Rules rules = javaBaseRules;

        if (rules == null) {
            javaBaseRules = rules = new RulesBuilder()
                .applyRules(RulesApplier.java_base())
                .validate(TestUtils.class.getClassLoader())
                .build();
        }

        return rules;
    }

    /**
     * @throw IllegalStateException if no transformations were made
     */
    public static Class<?> loadAndTransform(Class clazz, Controller controller)
        throws IOException, IllegalClassFormatException
    {
        Module module = TestUtils.class.getModule();
        return loadAndTransform(clazz.getName(), controller.checkerFor(module));
    }

    /**
     * @throw IllegalStateException if no transformations were made
     */
    public static Class<?> loadAndTransform(Class clazz, Checker checker)
        throws IOException, IllegalClassFormatException
    {
        return loadAndTransform(clazz.getName(), checker);
    }

    /**
     * @throw IllegalStateException if no transformations were made
     */
    public static Class<?> loadAndTransform(String className, Checker checker)
        throws IOException, IllegalClassFormatException
    {
        var key = new CacheKey(className, checker);
        Class<?> clazz = transformCache.get(key);

        if (clazz == null) synchronized (transformCache) {
            clazz = transformCache.get(key);

            if (clazz != null) {
                return clazz;
            }

            String resourceName = '/' + className.replace('.', '/') + ".class";

            byte[] bytes;
            try (InputStream in = TestUtils.class.getResourceAsStream(resourceName)) {
                bytes = in.readAllBytes();
            }

            var processor = ClassFileProcessor.begin(bytes);
            if (processor.check(checker)) {
                throw new IllegalStateException("Class wasn't transformed: " + className);
            }

            bytes = processor.redefine();

            clazz = new Injector(TestUtils.class.getClassLoader()).inject(className, bytes);

            transformCache.put(key, clazz);
        }

        return clazz;
    }

    private record CacheKey(String className, Checker checker) {}

    private static final SoftCache<CacheKey, Class<?>> transformCache = new SoftCache<>();

    private static class Injector extends ClassLoader {
        Injector(ClassLoader parent) {
            super(parent);
        }

        Class<?> inject(String className, byte[] bytes) {
            return defineClass(className, bytes, 0, bytes.length);
        }
    }

    /**
     * @return true if the thread has already run the test against the transformed code
     * @throw IllegalStateException if no transformations were made
     */
    public static boolean runTransformedTest(Object testInstance, Controller controller)
        throws Exception
    {
        return runTransformedTest(testInstance, controller.checkerFor(TestUtils.class.getModule()));
    }

    /**
     * @return true if the thread has already run the test against the transformed code
     * @throw IllegalStateException if no transformations were made
     */
    public static boolean runTransformedTest(Object testInstance, Checker checker)
        throws Exception
    {
        if (inTransformer.get()) {
            // Was called from the transformed code. Return false so that it can actually run.
            return false;
        }

        var frame = StackWalker.getInstance().walk
            (s -> s.filter(f -> !f.getClassName().equals(TestUtils.class.getName())).findFirst())
            .orElseThrow();

        Class<?> clazz = loadAndTransform(frame.getClassName(), checker);
        testInstance = clazz.getConstructor().newInstance();

        inTransformer.set(true);

        try {
            clazz.getMethod(frame.getMethodName()).invoke(testInstance);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error er) {
                throw er;
            }
            throw e;
        } finally {
            inTransformer.set(false);
        }

        return true;
    }

    private static final ThreadLocal<Boolean> inTransformer = ThreadLocal.withInitial(() -> false);
}
