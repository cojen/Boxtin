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

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public interface Rules {
    /**
     * @param module the caller's module
     */
    public Checker checkerFor(Module module);

    /**
     * Print a description of the rules to the given Appendable object.
     *
     * @param indent minimum indent for each line; pass an empty string for no indent
     * @param plusIndent additional indent when entering a scope
     */
    public void printTo(Appendable a, String indent, String plusIndent) throws IOException;

    /**
     * Print a description of the rules to the given PrintStream.
     */
    public default void printTo(PrintStream ps) {
        try {
            printTo(ps, "", "  ");
        } catch (IOException e) {
            // Not expected.
        }
    }

    /**
     * Print a description of the rules to the given PrintStream.
     */
    public default void printTo(PrintWriter pw) {
        try {
            printTo(pw, "", "  ");
        } catch (IOException e) {
            // Not expected.
        }
    }
}
