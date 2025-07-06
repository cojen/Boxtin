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

import org.cojen.maker.ClassMaker;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Defining of hidden classes must be denied because they're never passed to an instrumentation
 * agent.
 *
 * @author Brian S. O'Neill
 */
public class HiddenClassTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(HiddenClassTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        RulesBuilder b = new RulesBuilder().applyRules(RulesApplier.java_base());

        b.forModule("org.cojen.maker").forPackage("org.cojen.maker")
            .forClass("ClassMaker").allowAll();

        return b;
    }

    @Test
    public void basic() throws Exception {
        if (runTransformed()) {
            return;
        }

        byte[] bytes = ClassMaker.begin().finishBytes();

        var lookup = MethodHandles.lookup();

        try {
            lookup.defineHiddenClass(bytes, false);
            fail();
        } catch (SecurityException e) {
        }

        try {
            lookup.defineHiddenClassWithClassData(bytes, "data", false);
            fail();
        } catch (SecurityException e) {
        }
    }
}
