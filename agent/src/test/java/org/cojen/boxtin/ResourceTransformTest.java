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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ResourceTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(ResourceTransformTest.class.getName());
    }

    private static final Rules RULES = 
        new RulesBuilder().applyRules(RulesApplier.java_base())
        .forModule("java.base").forPackage("java.lang").forClass("System").allowAll()
        .end().end().end().build();

    // To be used with ClassLoader.
    private static final String RESOURCE =
        ResourceTransformTest.class.getName().replace('.', '/') + ".class";

    // To be used with Class and Module.
    private static final String ABS_RESOURCE = "/" + RESOURCE;

    private static final String PROP_KEY = ResourceTransformTest.class.getName() + ".class";

    @BeforeClass
    public static void setup() {
        System.getProperties().put(PROP_KEY, ResourceTransformTest.class);
    }

    @AfterClass
    public static void teardown() {
        System.getProperties().remove(PROP_KEY);
    }

    @Test
    public void fromClass() throws Exception {
        Class<?> thisClass = getClass();

        assertNotNull(thisClass.getResource(ABS_RESOURCE));
        thisClass.getResourceAsStream(ABS_RESOURCE).close();

        if (runWith(RULES)) {
            return;
        }

        Class<?> original = (Class) System.getProperties().get(PROP_KEY);

        assertNull(original.getResource(ABS_RESOURCE));
        assertNull(original.getResourceAsStream(ABS_RESOURCE));
    }

    @Test
    public void fromClassLoader() throws Exception {
        ClassLoader loader = getClass().getClassLoader();

        assertNotNull(loader.getResource(RESOURCE));
        loader.getResourceAsStream(RESOURCE).close();
        assertTrue(loader.getResources(RESOURCE).hasMoreElements());
        assertTrue(loader.resources(RESOURCE).count() > 0);

        if (runWith(RULES)) {
            return;
        }

        ClassLoader original = ((Class) System.getProperties().get(PROP_KEY)).getClassLoader();

        assertNull(original.getResource(RESOURCE));
        assertNull(original.getResourceAsStream(RESOURCE));
        assertFalse(original.getResources(RESOURCE).hasMoreElements());
        assertEquals(0, original.resources(RESOURCE).count());
    }

    @Test
    public void fromModule() throws Exception {
        if (runWith(RULES)) {
            getClass().getModule().getResourceAsStream(ABS_RESOURCE).close();
            return;
        }

        Module original = ((Class) System.getProperties().get(PROP_KEY)).getModule();

        assertNull(original.getResourceAsStream(ABS_RESOURCE));
    }
}
