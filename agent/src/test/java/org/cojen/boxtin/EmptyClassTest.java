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

import java.lang.invoke.MethodHandles;

import java.lang.reflect.Method;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class EmptyClassTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(EmptyClassTest.class.getName());
    }

    @Test
    public void basic() throws Exception {
        String name = getClass().getPackage().getName() + '.' + "FakeFakeFake";
        byte[] bytes = EmptyClassMaker.make(name);
        Class<?> clazz = MethodHandles.lookup().defineClass(bytes);
        for (Method m : clazz.getMethods()) {
            assertEquals(Object.class, m.getDeclaringClass());
        }
    }
}
