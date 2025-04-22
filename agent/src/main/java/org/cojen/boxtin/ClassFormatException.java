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
 * Is thrown when a class being transformed is malformed, or if the transformation failed.
 *
 * @author Brian S. O'Neill
 */
public class ClassFormatException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public ClassFormatException() {
        super();
    }

    public ClassFormatException(String message) {
        super(message);
    }

    public ClassFormatException(Throwable cause) {
        super(cause);
    }

    public ClassFormatException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns a ClassFormatException, wrapping if necessary.
     */
    static ClassFormatException from(Throwable e) {
        if (e instanceof ClassFormatException cfe) {
            return cfe;
        }
        return new ClassFormatException(e);
    }
}
