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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class CheckedTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(CheckedTransformTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() throws Exception {
        var b = new RulesBuilder();

        b.forModule("org.cojen.boxtin").forPackage("org.cojen.boxtin")
            .forClass("CheckedOperations")
            .allowAllConstructors()
            .denyMethod(DenyAction.standard().check(find("c_op1", int.class)), "op1")
            .denyMethod(DenyAction.value(-1L).check(find("c_op2", int.class, long.class)), "op2")
            .denyMethod(DenyAction.empty().check(find("c_op3", Caller.class, String.class)),
                        "op3");
        ;

        b.forModule("java.base").allowAll();

        return b;
    }

    private static MethodHandleInfo find(String name, Class... paramTypes)
        throws NoSuchMethodException, IllegalAccessException
    {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mt = MethodType.methodType(boolean.class, paramTypes);
        MethodHandle mh = name == null ? lookup.findConstructor(CheckedTransformTest.class, mt)
            : lookup.findStatic(CheckedTransformTest.class, name, mt);
        return lookup.revealDirect(mh);
    }

    public static boolean c_op1(int a) {
        return a >= 0; // deny negative inputs
    }

    public static boolean c_op2(int a, long b) {
        return a >= 0 && b >= 0; // deny negative inputs
    }

    public static boolean c_op3(Caller caller, String a) {
        assertSame(caller.validate(), CheckedTransformTest.class);
        return !"magic".equals(a); // deny magic string
    }

    @Test
    public void illegal() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mt = MethodType.methodType(void.class);
        MethodHandle mh = lookup.findVirtual(CheckedTransformTest.class, "illegal", mt);
        MethodHandleInfo illegal = lookup.revealDirect(mh);

        try {
            DenyAction.standard().check(illegal);
            fail();
        } catch (IllegalArgumentException e) {
            // predicate doesn't return boolean
        }

        MethodHandleInfo predicate = find("c_op1", int.class);
        DenyAction action = DenyAction.standard().check(predicate);

        try {
            action.check(predicate);
            fail();
        } catch (IllegalStateException e) {
            // action is checked
        }
    }

    @Test
    public void checked() throws Exception {
        if (runTransformed()) {
            return;
        }

        assertEquals("10", CheckedOperations.op1(10));

        try {
            CheckedOperations.op1(-10);
            fail();
        } catch (SecurityException e) {
        }

        assertEquals(100L, CheckedOperations.op2(10, 90));
        assertEquals(-1L, CheckedOperations.op2(10, -1000));

        assertEquals(0, CheckedOperations.op3("magic").length);
        assertEquals("hello", CheckedOperations.op3("hello")[0]);
    }
}
