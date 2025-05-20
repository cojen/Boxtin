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
public class TargetInterfaceTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(TargetInterfaceTransformTest.class.getName());
    }

    private static final Rules RULES;

    static {
        var b = new RulesBuilder();

        b.forModule("xxx").forPackage("org.cojen.boxtin")
            .forClass("T_InterfaceOperationsImpl").allowAllConstructors()
            .forClass("T_InterfaceOperations").denyAll()
            ;

        RULES = b.build();
    }

    @Test
    public void op1() throws Exception {
        if (runWith(RULES)) {
            return;
        }

        var opsImpl = new T_InterfaceOperationsImpl();
        var ops = (T_InterfaceOperations) opsImpl;

        try {
            opsImpl.op1(1);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            ops.op1(1);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            trampoline1(ops::op1, 1);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }

        /* FIXME: This appears to be a bug in the Java compiler. The bootstrap linkage is
                  being made to the interface method instead of the implementation method.
        try {
            trampoline1(opsImpl::op1, 1);
            fail();
        } catch (SecurityException e) {
            // Expected.
        }
        */
    }

    @FunctionalInterface
    public static interface Op1 {
        int apply(int a);
    }

    private static int trampoline1(Op1 f, int a) {
        return f.apply(a);
    }
}
