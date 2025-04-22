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

import java.util.OptionalInt;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ExitTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ExitTest.class.getName());
    }

    private static final SecurityException INIT_EX;

    static {
        SecurityException ex;
        try {
            System.exit(1);
            ex = null;
        } catch (SecurityException e) {
            ex = e;
        }
        INIT_EX = ex;
    }

    @Test
    public void clinit() {
        assertNotNull(INIT_EX);
    }

    @Test
    public void system() throws Exception {
        try {
            System.exit(1);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void runtime() throws Exception {
        try {
            Runtime.getRuntime().exit(1);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void lambda() throws Exception {
        try {
            OptionalInt.of(1).ifPresent(System::exit);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }
}
