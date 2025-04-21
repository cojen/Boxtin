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

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class DefaultController implements Controller {
    private final Rules mRules;

    DefaultController() {
        var builder = new RulesBuilder().applyRules(RulesApplier.java_base());

        String command = System.getProperty("sun.java.command");
        if (command != null) {
            // Allow access to the main method. Otherwise, an IllegalCallerException is thrown
            // because main method doesn't have a caller.
            int index = command.lastIndexOf('.');
            builder.forPackage(index < 0 ? "" : command.substring(0, index))
                .forClass(command.substring(index + 1))
                .denyMethod("main").allowVariant("([Ljava/lang/String;)V");
        }

        mRules = builder.build();
    }

    @Override
    public Checker checkerForCaller(Module module, Object clazz) {
        return mRules;
    }

    @Override
    public Checker checkerForTarget() {
        return mRules;
    }
}
