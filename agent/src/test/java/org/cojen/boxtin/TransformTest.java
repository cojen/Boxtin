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
            public Set<Rules> allRules() {
                return Set.of(rules);
            }
        };

        var agent = SecurityAgent.testActivate(c);

        try {
            if (transformed == null) {
                try {
                    var injector = new Injector(original.getClassLoader(), agent, c);
                    transformed = injector.inject(original);
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

    private static class Injector extends ClassLoader {
        private final SecurityAgent mAgent;
        private final Controller mController;

        private Injector(ClassLoader parent, SecurityAgent agent, Controller c) {
            super(parent);
            mAgent = agent;
            mController = c;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Check if the class name starts with a special prefix which indicates that it
            // should be transformed, but with target rules only.
            if (!name.startsWith("org.cojen.boxtin.T_")) {
                return super.loadClass(name, resolve);
            }

            Class<?> clazz = findLoadedClass(name);
            if (clazz != null) {
                return clazz;
            }

            try {
                return loadAndTransformClass(name, true);
            } catch (ClassNotFoundException e) {
                throw e;
            } catch (Throwable e) {
                throw new ClassNotFoundException(e.getMessage(), e);
            }
        }

        Class<?> inject(Class<?> original) throws Throwable {
            return loadAndTransformClass(original.getName(), false);
        }

        private Class<?> loadAndTransformClass(String className, boolean isTarget)
            throws Throwable
        {
            String pathName = className.replace('.', '/');

            byte[] bytes;
            try (InputStream in = getParent().getResourceAsStream(pathName + ".class")) {
                bytes = in.readAllBytes();
            }

            byte[] xbytes;
            if (!isTarget) {
                xbytes = mAgent.doTransform(getClass().getModule(), className, null, bytes);
            } else {
                // If the class is explicitly designated as a target, force it to have target
                // rules applied, and don't apply caller rules.

                Rules forTarget = MergedTargetRules.from(mController);

                int index = pathName.lastIndexOf('/');
                String packageName = index < 0 ? "" : pathName.substring(0, index);
                String justClassName = pathName.substring(index + 1);

                Rules.ForClass forTargetClass = forTarget.forClass(packageName, justClassName);

                var processor = ClassFileProcessor.begin(bytes);

                if (processor.check(Rule.allow(), forTargetClass, true)) {
                    xbytes = processor.redefine();

                    // Must be in a different module in order for checks to be applied.
                    return new ClassLoader(this) {
                        Class<?> inject(byte[] b) {
                            return defineClass(className, b, 0, b.length);
                        }
                    }.inject(xbytes);
                }

                xbytes = null;
            }

            if (xbytes == null) {
                xbytes = bytes;
            }

            return defineClass(className, xbytes, 0, xbytes.length);
        }
    }
}
