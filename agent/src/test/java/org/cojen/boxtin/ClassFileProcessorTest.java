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

import java.lang.invoke.MethodHandles;

import java.lang.reflect.InvocationTargetException;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.MethodMaker;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ClassFileProcessorTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ClassFileProcessorTest.class.getName());
    }

    @Test
    public void broken() throws Exception {
        try {
            ClassFileProcessor.begin(new byte[0]);
        } catch (ClassFormatException e) {
            assertTrue(e.ignore);
        }

        try {
            ClassFileProcessor.begin(new byte[] {1, 2, 3, 4});
        } catch (ClassFormatException e) {
            assertTrue(e.ignore);
        }

        try {
            byte[] bytes = {
                (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe,
                0, 0,  // minor version
                0, 13, // major version
            };
            ClassFileProcessor.begin(bytes);
        } catch (ClassFormatException e) {
            assertFalse(e.ignore);
            assertTrue(e.getMessage().contains("13"));
        }
    }

    @Test
    public void transformHiddenClassCreation() throws Exception {
        var loader = new ClassLoader() {
            Class<?> inject(String className, byte[] bytes) {
                return defineClass(className, bytes, 0, bytes.length);
            }
        };

        Class<?> definer;

        {
            String definerName = getClass().getName() + "$$Definer";
            var cm = ClassMaker.beginExternal(definerName).public_();
            MethodMaker mm = cm.addMethod(byte[].class, "defineHiddenClass",
                                          MethodHandles.Lookup.class, byte[].class);
            mm.public_().static_();
            mm.return_(mm.param(1));
            byte[] bytes = cm.finishBytes();
            bytes = ClassFileProcessor.begin(bytes).transformHiddenClassCreation();
            definer = loader.inject(definerName, bytes);
        }

        String makeCallerName = getClass().getName() + "$$MakeCaller";
        var cm = ClassMaker.beginExternal(makeCallerName).public_();
        MethodMaker mm = cm.addMethod(Caller.class, "makeCaller", Class.class).public_().static_();
        mm.return_(mm.var(SecurityAgent.class).invoke("callerFor", mm.param(0)));
        byte[] bytes = cm.finishBytes();

        assertFalse(SecurityAgent.isActivated());
        SecurityAgent.testActivate(new DefaultController());

        try {
            assertTrue(SecurityAgent.isActivated());
            bytes = (byte[]) definer.getMethod
                ("defineHiddenClass", MethodHandles.Lookup.class, byte[].class)
                .invoke(null, MethodHandles.lookup(), bytes);
        } finally {
            SecurityAgent.testActivate(null);
            assertFalse(SecurityAgent.isActivated());
        }

        Class<?> makeCaller = loader.inject(makeCallerName, bytes);

        try {
            makeCaller.getMethod("makeCaller", Class.class).invoke(null, getClass());
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof SecurityException);
        }
    }
}
