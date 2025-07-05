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

import java.io.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class FileTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(FileTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        try {
            new FileInputStream("x");
            fail();
        } catch (FileNotFoundException e) {
            // Expected.
        }

        File f = new File("P8pTg&UbAd84stqzpi4D");

        try {
            new FileInputStream(f);
            fail();
        } catch (FileNotFoundException e) {
            // Expected.
        }

        try {
            new FileInputStream((FileDescriptor) null);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            new FileOutputStream("x");
            fail();
        } catch (FileNotFoundException e) {
            // Expected.
        }

        assertFalse(f.delete());

        assertEquals(0, File.listRoots().length);
    }
}
