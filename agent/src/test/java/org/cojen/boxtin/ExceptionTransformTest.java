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

import java.io.IOException;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ExceptionTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ExceptionTransformTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() throws Exception {
        var b = new RulesBuilder();

        b.forModule("org.cojen.boxtin").forPackage("org.cojen.boxtin")
            .forClass("ExceptionOperations")
            .allowAllConstructors()
            .denyMethod(DenyAction.exception("java.lang.SecurityException"), "op1")
            .denyMethod(DenyAction.exception("java.lang.SecurityException", "m2"), "op2")
            .denyMethod(DenyAction.exception("java.io.IOException"), "op3")
            .denyMethod(DenyAction.exception("java.io.IOException", "m4"), "op4")
            .denyMethod(DenyAction.exception(SecurityException.class), "op5")
            .denyMethod(DenyAction.exception(SecurityException.class, "m6"), "op6")
            .denyMethod(DenyAction.exception(IOException.class), "op7")
            .denyMethod(DenyAction.exception(IOException.class, "m8"), "op8")
        ;

        b.forModule("java.base").allowAll();

        b.validate();

        return b;
    }

    @Test
    public void basic() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            ExceptionOperations.op1();
            fail();
        } catch (SecurityException e) {
            assertNull(e.getMessage());
        }

        try {
            ExceptionOperations.op2();
            fail();
        } catch (SecurityException e) {
            assertEquals("m2", e.getMessage());
        }

        try {
            ExceptionOperations.op3();
            fail();
        } catch (IOException e) {
            assertNull(e.getMessage());
        }

        try {
            ExceptionOperations.op4();
            fail();
        } catch (IOException e) {
            assertEquals("m4", e.getMessage());
        }

        try {
            ExceptionOperations.op5();
            fail();
        } catch (SecurityException e) {
            assertNull(e.getMessage());
        }

        try {
            ExceptionOperations.op6();
            fail();
        } catch (SecurityException e) {
            assertEquals("m6", e.getMessage());
        }

        try {
            ExceptionOperations.op7();
            fail();
        } catch (IOException e) {
            assertNull(e.getMessage());
        }

        try {
            ExceptionOperations.op8();
            fail();
        } catch (IOException e) {
            assertEquals("m8", e.getMessage());
        }
    }
}
