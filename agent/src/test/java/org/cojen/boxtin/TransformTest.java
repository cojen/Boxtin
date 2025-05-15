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

    private static final SoftCache<Class<?>, Class<?>> cTransformed = new SoftCache<>();

    /**
     * Transforms the current class using a SecurityAgent, and then runs the caller's test
     * method. Note that before and after test actions aren't performed.
     *
     * @return true if the test should simply return because it already ran
     */
    protected boolean runWith(Rules rules) throws Exception {
        if (cRunning.get()) {
            return false;
        }

        var frame = WALKER.walk(s -> s.skip(1).findFirst()).get();
        Class<?> original = frame.getDeclaringClass();
        Class<?> transformed = cTransformed.get(original);

        Controller c = new Controller() {
            @Override
            public Rules rulesForCaller(Module module) {
                return rules;
            }

            @Override
            public Rules rulesForTarget() {
                return rules;
            }
        };

        var agent = SecurityAgent.testActivate(c);

        try {
            if (transformed == null) {
                try {
                    transformed = doTransform(original, agent);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }

                cTransformed.put(original, transformed);
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

    private Class<?> doTransform(Class<?> original, SecurityAgent agent) throws Throwable {
        byte[] bytes;
        try (InputStream in = original.getResourceAsStream
             ('/' + original.getName().replace('.', '/') + ".class"))
        {
            bytes = in.readAllBytes();
        }

        bytes = agent.doTransform(original.getModule(), original.getName(), null, bytes);

        return Injector.inject(original, bytes);
    }

    private static class Injector extends ClassLoader {
        static Class<?> inject(Class<?> original, byte[] bytes) {
            return new Injector(original.getClassLoader())
                .defineClass(original.getName(), bytes, 0, bytes.length);
        }

        private Injector(ClassLoader parent) {
            super(parent);
        }
    }
}
