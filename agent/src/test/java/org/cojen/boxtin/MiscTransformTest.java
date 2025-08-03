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

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.net.Socket;

import java.util.Formatter;

import java.util.function.Supplier;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class MiscTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(MiscTransformTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        return new RulesBuilder().applyRules(RulesApplier.java_base());
    }

    @Test
    public void array() throws Exception {
        if (runTransformed()) {
            return;
        }

        var array = new ProcessBuilder[1];
        assertArrayEquals(array, array.clone());
    }

    @Test
    public void switches() throws Exception {
        if (runTransformed()) {
            return;
        }

        int a = (int) System.currentTimeMillis();

        try {
            new ProcessBuilder();
            fail();
        } catch (SecurityException e) {
        }

        int b;

        switch (a) {
        default: b = 1;
        case 1: b = 2;
        case 2: b = 3;
        case 3: b = 0;
        }

        try {
            new ProcessBuilder();
            fail();
        } catch (SecurityException e) {
        }

        switch (a + b) {
        default: b = 1;
        case 10: b = 2;
        case 20: b = 3;
        case 30: b = 0;
        }

        try {
            new FileInputStream("" + b);
            fail();
        } catch (FileNotFoundException e) {
        }
    }

    @Test
    public void handles() throws Exception {
        if (runTransformed()) {
            return;
        }

        {
            Supplier<Object> s = Formatter::new;
            s.get();
        }

        {
            Supplier<Object> s = Socket::new;
            try {
                s.get();
                fail();
            } catch (SecurityException e) {
            }
        }

        {
            ThreadGroup g = Thread.currentThread().getThreadGroup();
            Supplier<Object> s = g::getParent;
            try {
                s.get();
                fail();
            } catch (SecurityException e) {
            }
        }
    }
}
