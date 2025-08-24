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

package org.cojen.boxtin.tests;

import org.cojen.boxtin.Controller;
import org.cojen.boxtin.Rules;
import org.cojen.boxtin.RulesApplier;
import org.cojen.boxtin.RulesBuilder;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TestController implements Controller {
    private final Rules mRules;

    public TestController() {
        var b = new RulesBuilder().applyRules(RulesApplier.java_base());
        b.forModule("org.cojen.maker").forPackage("org.cojen.maker").allowAll();
        b.forModule("junit").forPackage("org.junit").allowAll();
        mRules = b.build();
    }

    @Override
    public Rules rulesForCaller(Module module) {
        if ("org.cojen.boxtin.tests".equals(module.getName())) {
            return mRules;
        }
        // Note that returning null (allow all) for unknown or unnamed modules isn't usually
        // appropriate. It's done this way only for the benefit of the test suite.
        return null;
    }
}
