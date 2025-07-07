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

import java.security.ProtectionDomain;

import org.cojen.maker.ClassMaker;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ClassLoaderTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ClassLoaderTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        String newName = "org.cojen.boxtin.tests.ClassLoaderTest$Foo";
        ClassMaker cm = ClassMaker.beginExternal(newName).public_();
        cm.addMethod(String.class, "test").public_().static_().return_("hello");

        byte[] bytes = cm.finishBytes();

        class Loader extends ClassLoader {
            final ProtectionDomain pd;
            final int mode;

            Loader(ProtectionDomain pd, int mode) {
                super(ClassLoaderTest.class.getClassLoader());
                this.pd = pd;
                this.mode = mode;
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException
            {
                if (!name.equals(newName)) {
                    return super.loadClass(name, resolve);
                }

                return switch (mode) {
                    default -> throw new AssertionError();
                    case 0 -> defineClass(newName, bytes, 0, bytes.length, pd);
                    case 1 -> super.defineClass(newName, bytes, 0, bytes.length, pd);
                    case 2 -> {
                        var instance = this;
                        instance = null;
                        yield instance.defineClass(newName, bytes, 0, bytes.length, pd);
                    }
                };
            }
        }

        Class<?> clazz = new Loader(null, 0).loadClass(newName);
        assertEquals("hello", clazz.getMethod("test").invoke(null));

        try {
            new Loader(new ProtectionDomain(null, null), 0).loadClass(newName);
            fail();
        } catch (SecurityException e) {
        }

        try {
            new Loader(new ProtectionDomain(null, null), 1).loadClass(newName);
            fail();
        } catch (SecurityException e) {
        }

        try {
            new Loader(new ProtectionDomain(null, null), 2).loadClass(newName);
            fail();
        } catch (NullPointerException e) {
        }
    }
}
