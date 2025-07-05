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
        assertNull(System.setProperty("user.dir", "fake"));
        assertEquals("fake", System.getProperty("user.dir"));
        System.clearProperty("user.dir");
        assertNull(System.getProperty("user.dir"));
        System.setProperty("file.separator", "qqq");
        assertEquals("qqq", System.getProperty("file.separator"));
        System.setProperties(null);
        assertNotEquals("qqq", System.getProperty("file.separator"));

        var props = new Properties();
        System.setProperties(props);

        assertNull(System.getProperty("file.separator"));

        String name = PropertiesTest.class.getName();
        System.setProperty(name, "hello");
        assertEquals("hello", props.get(name));
        assertTrue(System.getProperties().containsKey(name));
    }

    @AfterClass
    public static void finished() {
        assertNotNull(System.getProperty("file.separator"));
        assertNull(System.getProperty(PropertiesTest.class.getName()));
    }
}
