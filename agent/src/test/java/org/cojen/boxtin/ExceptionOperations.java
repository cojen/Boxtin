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
 * @see ExceptionTransformTest
 */
@Ignore
public class ExceptionOperations {
    public ExceptionOperations() {
    }

    public static void op1() {
    }

    public static void op2() {
    }

    public static void op3() throws Exception {
    }

    public static void op4() throws Exception {
    }

    public static void op5() {
    }

    public static void op6() {
    }

    public static void op7() throws Exception {
    }

    public static void op8() throws Exception {
    }
}
