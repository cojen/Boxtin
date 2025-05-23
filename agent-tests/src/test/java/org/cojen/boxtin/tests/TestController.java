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

import java.util.Set;

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
        var builder = new RulesBuilder();

        builder.allowAll()
            .forModule("java.base")

            .forPackage("java.io")
            .allowAll()

            .forClass("File")
            .denyAllMethods()
            .allowMethod("compareTo")
            .allowMethod("getName")
            .allowMethod("getParent")
            .allowMethod("getParentFile")
            .allowMethod("getPath")
            .allowMethod("toPath")

            .forClass("FileInputStream")
            .denyAllConstructors()

            .forClass("FileOutputStream")
            .denyAllConstructors()

            .forPackage("java.lang")
            .allowAll()

            .forClass("Class")
            .callerCheck()
            .denyMethod("forName")
            .allowVariant(String.class)
            .denyMethod("getClassLoader")
            .denyMethod("getClasses")
            .denyMethod("getConstructor")
            .denyMethod("getConstructors")
            .denyMethod("getDeclaredClasses")
            .denyMethod("getDeclaredConstructor")
            .denyMethod("getDeclaredConstructors")
            .denyMethod("getDeclaredMethod")
            .denyMethod("getDeclaredMethods")
            .denyMethod("getDeclaringClass")
            .denyMethod("getEnclosingClass")
            .denyMethod("getEnclosingConstructor")
            .denyMethod("getEnclosingMethod")
            .denyMethod("getMethod")
            .denyMethod("getMethods")
            .denyMethod("getNestHost")
            .denyMethod("getNestMembers")
            .denyMethod("getPermittedSubclasses")
            .denyMethod("getProtectionDomain")
            .denyMethod("getRecordComponents")
            .denyMethod("newInstance") // deprecated

            .forClass("Process")
            .denyMethod("children")
            .denyMethod("descendants")
            .denyMethod("toHandle")

            .forClass("ProcessBuilder")
            .denyMethod("environment")
            .denyMethod("start")
            .denyMethod("startPipeline")

            .forClass("ProcessHandle")
            .denyMethod("allProcesses")
            .denyMethod("current")
            .denyMethod("of")
            .callerCheck()
            .denyMethod("children")
            .denyMethod("descendants")
            .denyMethod("parent")

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

            .forPackage("java.lang.invoke")
            .allowAll()

            .forClass("MethodHandles.Lookup")
            .callerCheck()
            .denyAllMethods()
            .allowMethod("accessClass")
            .allowMethod("dropLookupMode")
            .allowMethod("ensureInitialized")
            .allowMethod("findClass")
            .allowMethod("findGetter")
            .allowMethod("findSetter")
            .allowMethod("findStaticGetter")
            .allowMethod("findStaticSetter")
            .allowMethod("findStaticVarHandle")
            .allowMethod("findVarHandle")
            .allowMethod("hasFullPrivilegeAccess")
            .allowMethod("hasPrivateAccess")
            .allowMethod("in")
            .allowMethod("lookupClass")
            .allowMethod("lookupModes")
            .allowMethod("previousLookupClass")
            .allowMethod("revealDirect")
            .allowMethod("unreflect")
            .allowMethod("unreflectConstructor")
            .allowMethod("unreflectGetter")
            .allowMethod("unreflectSetter")
            .allowMethod("unreflectSpecial")
            .allowMethod("unreflectVarHandle")

            .forClass("MethodType")
            .denyMethod("fromMethodDescriptorString")

            ;

        mRules = builder.build();
    }

    @Override
    public Rules rulesForCaller(Module module) {
        if ("org.cojen.boxtin.tests".equals(module.getName())) {
            return mRules;
        }
        return null;
    }

    @Override
    public Set<Rules> allRules() {
        return Set.of(mRules);
    }
}
