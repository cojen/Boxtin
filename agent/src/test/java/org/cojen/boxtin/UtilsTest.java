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

import java.lang.reflect.Method;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class UtilsTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(Utils.class.getName());
    }

    @Test
    public void fullDescriptor() {
        String desc = Utils.fullDescriptorFor(void.class, int.class, String.class);
        assertEquals("(ILjava/lang/String;)V", desc);
    }

    @Test
    public void names() {
        assertEquals("", Utils.packageName("Foo"));
        assertEquals("a", Utils.packageName("a/Foo"));
        assertEquals("a/b", Utils.packageName("a/b/Foo"));

        assertEquals("Foo", Utils.className("", "Foo"));
        assertEquals("Foo", Utils.className("a/b", "a/b/Foo"));

        assertEquals("Foo", Utils.fullName("", "Foo"));
        assertEquals("a/b/Foo", Utils.fullName("a/b", "Foo"));
    }

    @Test
    public void exception() {
        try {
            Utils.rethrow(new Exception());
            fail();
        } catch (Exception e) {
        }
    }

    @Test
    public void objectMethods() {
        for (Method m : Object.class.getDeclaredMethods()) {
            if (Utils.isAccessible(m)) {
                String desc = Utils.paramDescriptorFor(m.getParameterTypes());
                assertTrue(Utils.isObjectMethod(m.getName(), desc));
                assertFalse(Utils.isObjectMethod(m.getName(), "xxx"));
            }
        }
    }
}
