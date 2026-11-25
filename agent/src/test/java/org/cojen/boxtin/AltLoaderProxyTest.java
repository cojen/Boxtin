/*
 *  Copyright 2026 Cojen.org
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests SecurityAgent.Proxy when the agent classes are loaded by the boot loader (as is the
 * case when the agent is loaded via the Boot-Class-Path manifest attribute) and the agent has
 * not been activated. The Proxy used to look up the wrong method for transforming hidden
 * classes, which caused its initialization to fail. The scenario is run in a child JVM
 * because it requires special JVM options.
 *
 * @author Claude
 */
public class AltLoaderProxyTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            org.junit.runner.JUnitCore.main(AltLoaderProxyTest.class.getName());
        } else {
            child();
        }
    }

    /**
     * The child's main method. The SecurityAgent class is accessed only reflectively, because
     * it's loaded by the boot loader.
     */
    private static void child() throws Exception {
        Class<?> agent = Class.forName("org.cojen.boxtin.SecurityAgent");
        System.out.println("boot loaded: " + (agent.getClassLoader() == null));

        Method m = agent.getDeclaredMethod
            ("isAllowed", Class.class, Class.class, String.class, String.class);
        m.setAccessible(true);

        try {
            // Triggers initialization of the Proxy class.
            System.out.println("RESULT " + m.invoke(null, Object.class, Object.class, "", ""));
        } catch (InvocationTargetException e) {
            System.out.println("FAILED " + e.getCause());
        }
    }

    @Test
    public void bootLoadedInactiveAgent() throws Exception {
        String agentRoot = RegressionSupport.rootOf(SecurityAgent.class);

        var result = RegressionSupport.runChild
            (List.of("-Xbootclasspath/a:" + agentRoot), List.of("child"),
             AltLoaderProxyTest.class, AltLoaderProxyTest.class);

        assertEquals(result.output(), 0, result.exitCode());
        assertTrue(result.output(), result.output().contains("boot loaded: true"));
        assertFalse(result.output(), result.output().contains("FAILED"));
        // An inactive agent denies nothing.
        assertTrue(result.output(), result.output().contains("RESULT false"));
    }
}
