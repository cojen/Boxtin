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

import java.util.Properties;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class PropertiesTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(PropertiesTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        return new RulesBuilder().applyRules(RulesApplier.java_base());
    }

    @Test
    public void basic() throws Exception {
        if (runTransformed()) {
            assertNotNull(System.getProperty("user.dir"));
            return;
        }

        assertNull(System.getProperty("x"));
        assertNull(System.getProperty("user.dir"));
        assertEquals("x", System.getProperty("user.dir", "x"));
        assertNotNull(System.getProperty("file.separator"));

        try {
            System.setProperty("abc", "xyz");
            fail();
        } catch (SecurityException e) {
        }

        try {
            System.clearProperty("abc");
            fail();
        } catch (SecurityException e) {
        }

        try {
            System.setProperties(null);
            fail();
        } catch (SecurityException e) {
        }

        try {
            System.getProperties();
            fail();
        } catch (SecurityException e) {
        }
    }

    @Test
    public void numbers() throws Exception {
        if (runTransformed()) {
            return;
        }

        assertTrue(15 == Integer.getInteger("x", 15));
        assertTrue(15 == Integer.getInteger("x", (Integer) 15));
        assertTrue(15L == Long.getLong("x", 15L));
        assertTrue(15L == Long.getLong("x", (Long) 15L));
    }

    @AfterClass
    public static void finished() {
        assertNotNull(System.getProperty("file.separator"));
        assertNull(System.getProperty(PropertiesTest.class.getName()));
    }
}
