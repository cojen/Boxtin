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

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class AAA_SetupTest {
    @BeforeClass
    public static void setup() {
        // Force the "isAllowed3" variant to be used, which checks if the agent is null or not
        // each time.
        SecurityAgent.isAllowed(Object.class, Object.class, "", "");
    }

    @Test
    public void nop() {
    }
}
