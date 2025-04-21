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

import org.cojen.boxtin.Checker;
import org.cojen.boxtin.Controller;
import org.cojen.boxtin.Rules;
import org.cojen.boxtin.RulesBuilder;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TestController implements Controller {
    private final Rules mRules;

    public TestController() {
        var builder = new RulesBuilder()
            .allowAll()

            .forPackage("java.lang")
            .allowAll()

            .forClass("Process")
            .denyMethod("children")
            .denyMethod("descendants")
            .denyMethod("toHandle")
            .end()

            .forClass("ProcessBuilder")
            .denyMethod("environment")
            .denyMethod("start")
            .denyMethod("startPipeline")
            .end()

            .forClass("ProcessHandle")
            .denyMethod("allProcesses")
            .denyMethod("current")
            .denyMethod("of")
            .callerCheck()
            .denyMethod("children")
            .denyMethod("descendants")
            .denyMethod("parent")
            .end()

            .forClass("Runtime")
            .callerCheck()
            .denyAll()
            .allowMethod("availableProcessors")
            .allowMethod("freeMemory")
            .allowMethod("gc")
            .allowMethod("getRuntime")
            .allowMethod("maxMemory")
            .allowMethod("runFinalization")
            .allowMethod("totalMemory")
            .allowMethod("version")
            .end()

            .forClass("System")
            .callerCheck()
            .denyAll()
            .allowMethod("arraycopy")
            .allowMethod("currentTimeMillis")
            .allowMethod("gc")
            .allowMethod("getLogger")
            .allowMethod("identityHashCode")
            .allowMethod("lineSeparator")
            .allowMethod("nanoTime")
            .allowMethod("runFinalization")
            .end()

            .end();

        mRules = builder.build();
    }

    @Override
    public Checker checkerForCaller(Module module, Object clazz) {
        if (clazz instanceof String s && !s.startsWith("org/cojen/boxtin/tests")) {
            return null;
        }
        return mRules;
    }

    @Override
    public Checker checkerForTarget() {
        return mRules;
    }
}
