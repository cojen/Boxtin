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
public class IntArrayTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(IntArrayTest.class.getName());
    }

    @Test
    public void illegal() {
        var array = new IntArray(0);
        array.push(1);

        try {
            array.pop(2);
            fail();
        } catch (IllegalStateException e) {
        }

        array.push(3);
        assertEquals(2, array.length());
        assertEquals(1, array.get(0));
        assertEquals(3, array.get(1));

        try {
            array.chop(3);
            fail();
        } catch (IllegalStateException e) {
        }

        assertEquals("[1,3]", array.toString());
    }

    @Test
    public void lengthChange() {
        var array = new IntArray(new int[] {1, 2});
        array.length(5);
        assertEquals("[1,2,0,0,0]", array.toString());
        array.set(4, 9);
        assertEquals("[1,2,0,0,9]", array.toString());
        array.length(4);
        assertEquals("[1,2,0,0]", array.toString());
        array.length(5);
        assertEquals("[1,2,0,0,0]", array.toString());
    }
}
