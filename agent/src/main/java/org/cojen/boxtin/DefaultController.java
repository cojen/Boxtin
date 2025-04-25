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

    DefaultController(boolean allowMain) {
        var builder = new RulesBuilder().applyRules(RulesApplier.java_base());

        if (allowMain) {
            String command = System.getProperty("sun.java.command");

            if (command != null) {
                // Allow access to the main method. Otherwise, an IllegalCallerException can be
                // thrown because the main method doesn't have a caller. This doesn't happen if
                // the main method is in an unnamed module, because unnamed modules cannot have
                // target checks applied to them.

                int endIndex = command.indexOf(' ');
                if (endIndex < 0) {
                    endIndex = command.length();
                }
                int dotIndex = command.lastIndexOf('.', endIndex);

                String packageName = dotIndex < 0 ? "" : command.substring(0, dotIndex);

                Module module = null;

                for (Module m : ModuleLayer.boot().modules()) {
                    if (m.getPackages().contains(packageName)) {
                        module = m;
                        break;
                    }
                }

                if (module != null) {
                    String className = command.substring(dotIndex + 1, endIndex);

                    builder.forModule(module).forPackage(packageName).forClass(className)
                        .denyMethod("main").allowVariant("([Ljava/lang/String;)V");
                }
            }
        }

        mRules = builder.build();
    }

    @Override
    public Checker checkerForCaller(Module module) {
        return mRules;
    }

    @Override
    public Checker checkerForTarget() {
        return mRules;
    }
}
