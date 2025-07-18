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

import org.junit.Ignore;

/**
 * Defines various operations which should be denied.
 *
 * @author Brian S. O'Neill
 * @see CheckedTransformTest
 */
@Ignore
public class CheckedOperations {

    public CheckedOperations() {
    }

    public static String op1(int a) {
        return "" + a;
    }

    public static long op2(int a, long b) {
        return a + b;
    }

    public static String[] op3(String a) {
        return new String[] {a};
    }
}
