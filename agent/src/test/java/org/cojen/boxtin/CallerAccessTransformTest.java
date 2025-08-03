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
public class CallerAccessTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(CallerAccessTransformTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        return new RulesBuilder().applyRules(RulesApplier.java_base());
    }

    @Test
    public void direct() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            SecurityAgent.callerFor(CallerAccessTransformTest.class);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
    }

    @Test
    public void reflection() throws Exception {
        if (runTransformed()) {
            return;
        }

        try {
            SecurityAgent.class.getMethod("callerFor", Class.class);
            fail();
        } catch (NoSuchMethodException e) {
            assertThrownByCustomCheck(e);
        }
    }

    private static void assertThrownByCustomCheck(NoSuchMethodException e) {
        assertEquals("org.cojen.boxtin.CustomActions", e.getStackTrace()[0].getClassName());
    }
}
