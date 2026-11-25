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

import java.util.function.Supplier;

/**
 * Classes which are transformed by the regression tests. They're never expected to be
 * executed in their original form.
 *
 * @author Claude
 */
public class RegressionTargets {
    /** Calls methods which return arrays of primitives. */
    public static class ArrayReturnCaller {
        public static Object bytes(String s) {
            return s.getBytes();
        }

        public static Object chars(String s) {
            return s.toCharArray();
        }

        public static Object longs() {
            return new java.util.BitSet().toLongArray();
        }
    }

    /** Inherits static methods, some of which are native. */
    public static class ThreadChild extends Thread {
    }

    /**
     * Inherits a static method (getDefault) which is declared again by a super class. Is
     * abstract only to avoid the need to implement all of the abstract methods.
     */
    public abstract static class SocketFactoryChild extends javax.net.ssl.SSLSocketFactory {
    }

    /** Uses a method handle for a constructor, from within an interface. */
    public interface ConstructorRefInterface {
        static Object make() {
            Supplier<Thread> s = Thread::new;
            return s.get();
        }
    }

    /** Uses a method handle for a constructor, from within a class. */
    public static class ConstructorRefClass {
        public static Object make() {
            Supplier<Thread> s = Thread::new;
            return s.get();
        }
    }
}
