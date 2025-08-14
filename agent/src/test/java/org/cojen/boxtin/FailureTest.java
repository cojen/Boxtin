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

import java.util.Arrays;
import java.util.List;

import java.util.logging.LogRecord;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.MethodMaker;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class FailureTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(FailureTest.class.getName());
    }

    @Test
    public void badOp() throws Exception {
        ClassMaker cm = ClassMaker.beginExplicit("Foo", null, null).public_();
        MethodMaker mm = cm.addMethod(null, "test").public_().static_();
        mm.new_(Exception.class).throw_();
        byte[] bytes = cm.finishBytes();

        for (int i=0; i<bytes.length; i++) {
            if (bytes[i] == Opcodes.NEW) {
                bytes[i] = (byte) 250; // undefined op
            }
        }

        List<LogRecord> log = testBadTransform("Foo", bytes, true);

        assertEquals(1, log.size());
        LogRecord record = log.get(0);
        assertTrue(record.getMessage().contains("Failed to transform class"));
        assertTrue(record.getMessage().contains("Foo"));
        assertTrue(record.getThrown() instanceof ClassFormatException);
    }

    @Test
    public void badMagic() throws Exception {
        byte[] bytes = ClassMaker.beginExplicit("Foo", null, null).finishBytes();
        bytes[0] = 0;
        List<LogRecord> log = testBadTransform("Foo", bytes, false);
        assertEquals(0, log.size());
    }

    @Test
    public void hugeMethod() throws Exception {
        ClassMaker cm = ClassMaker.beginExplicit("Foo", null, null);
        MethodMaker mm = cm.addMethod(null, "test");
        mm.var(System.class).invoke("exit", 1); // 3 bytes

        for (int i = 0; i < (32767 - 3); i++) {
            mm.nop();
        }

        byte[] bytes = cm.finishBytes();

        List<LogRecord> log = testBadTransform("Foo", bytes, true);

        assertEquals(1, log.size());
        LogRecord record = log.get(0);
        Throwable ex = record.getThrown();
        assertTrue(ex instanceof ClassFormatException);
        assertTrue(ex.getMessage().contains("Method is too large"));
    }

    private static List<LogRecord> testBadTransform(String name, byte[] bytes, boolean empty)
        throws Exception
    {
        List<LogRecord> log;
        Module unnamed = FailureTest.class.getClassLoader().getUnnamedModule();
        byte[][] bytesRef = {bytes};

        assertFalse(SecurityAgent.isActivated());
        SecurityAgent agent = SecurityAgent.testActivate(new DefaultController());
        try {
            log = SimpleLogHandler.forSecurityAgent(() -> {
                bytesRef[0] = agent.transform(unnamed, name, bytesRef[0]);
            });
        } finally {
            SecurityAgent.testActivate(null);
        }

        boolean isEmpty = Arrays.equals(EmptyClassMaker.make("Foo"), bytesRef[0]);

        if (empty) {
            assertTrue(isEmpty);
        } else {
            assertFalse(isEmpty);
        }

        return log;
    }
}
