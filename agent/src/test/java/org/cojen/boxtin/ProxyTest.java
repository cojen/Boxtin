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

import java.io.PrintStream;
import java.io.PrintWriter;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import java.util.spi.ToolProvider;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class ProxyTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(PropertiesTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        return new RulesBuilder().applyRules(RulesApplier.java_base());
    }

    @Test
    public void accessDefault() throws Throwable {
        if (runTransformed(TestProvider.class, TestHandler.class)) {
            return;
        }

        try {
            ToolProvider.findFirst("x");
            fail();
        } catch (SecurityException e) {
        }

        try {
            new TestProvider();
            fail();
        } catch (SecurityException e) {
        }

        try {
            Proxy.newProxyInstance(getClass().getClassLoader(),
                                   new Class<?>[] {ToolProvider.class}, new TestHandler());
            fail();
        } catch (SecurityException e) {
            // ToolProvider has denied methods.
        }

        var runnable = (Runnable) Proxy.newProxyInstance
            (getClass().getClassLoader(), new Class<?>[] {Runnable.class}, new TestHandler());

        runnable.run();
    }

    public static class TestProvider implements ToolProvider {
        @Override
        public String name() {
            return "name";
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return 0;
        }
    }

    public static class TestHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }
}
