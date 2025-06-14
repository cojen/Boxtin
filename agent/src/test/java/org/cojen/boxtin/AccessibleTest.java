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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class AccessibleTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(AccessibleTest.class.getName());
    }

    private static final Rules RULES = 
        new RulesBuilder().applyRules(RulesApplier.java_base()).build();

    @Test
    public void unsafe() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        Class<?> clazz = Class.forName("sun.misc.Unsafe");
        Field f = clazz.getDeclaredField("theUnsafe");

        f.setAccessible(false);
        ((AccessibleObject) f).setAccessible(false);

        assertFalse(f.trySetAccessible());
        assertFalse(((AccessibleObject) f).trySetAccessible());

        try {
            f.setAccessible(true);
            fail();
        } catch (InaccessibleObjectException e) {
        }

        try {
            ((AccessibleObject) f).setAccessible(true);
            fail();
        } catch (InaccessibleObjectException e) {
        }
    }

    @Test
    public void field() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        // Although this is a public field, the current implementation doesn't permit setting
        // it accessible because it would need to perform module access checks.
        Field f = System.class.getDeclaredField("out");

        f.setAccessible(false);
        ((AccessibleObject) f).setAccessible(false);

        assertFalse(f.trySetAccessible());
        assertFalse(((AccessibleObject) f).trySetAccessible());

        try {
            f.setAccessible(true);
            fail();
        } catch (InaccessibleObjectException e) {
        }

        try {
            ((AccessibleObject) f).setAccessible(true);
            fail();
        } catch (InaccessibleObjectException e) {
        }
    }

    @Test
    public void method() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        // Although this is a public method, the current implementation doesn't permit setting
        // it accessible because it would need to perform module access checks.
        Method m = Object.class.getDeclaredMethod("getClass");

        m.setAccessible(false);
        ((AccessibleObject) m).setAccessible(false);

        assertFalse(m.trySetAccessible());
        assertFalse(((AccessibleObject) m).trySetAccessible());

        try {
            m.setAccessible(true);
            fail();
        } catch (InaccessibleObjectException e) {
        }

        try {
            ((AccessibleObject) m).setAccessible(true);
            fail();
        } catch (InaccessibleObjectException e) {
        }
    }

    @Test
    public void constructor() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        // Although this is a public constructor, the current implementation doesn't permit
        // setting it accessible because it would need to perform module access checks.
        Constructor c = Object.class.getDeclaredConstructor();

        c.setAccessible(false);
        ((AccessibleObject) c).setAccessible(false);

        assertFalse(c.trySetAccessible());
        assertFalse(((AccessibleObject) c).trySetAccessible());

        try {
            c.setAccessible(true);
            fail();
        } catch (InaccessibleObjectException e) {
        }

        try {
            ((AccessibleObject) c).setAccessible(true);
            fail();
        } catch (InaccessibleObjectException e) {
        }
    }

    @Test
    public void local() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        // It's in the same module, so it's okay.
        AccessibleTest.class.getDeclaredMethod("m1").setAccessible(true);
    }

    private void m1() {
    }
}
