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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Encodes the primitive data elements of a class file.
 *
 * @author Brian S. O'Neill
 */
class BasicEncoder extends DataOutputStream {
    BasicEncoder(int capacity) {
        super(new Buffer(capacity));
    }

    byte[] toByteArray() {
        return ((Buffer) out).toByteArray();
    }

    void writeTo(OutputStream dest) throws IOException {
        ((Buffer) out).writeTo(dest);
    }

    byte[] buffer() {
        return ((Buffer) out).buffer();
    }

    int length() {
        return ((Buffer) out).length();
    }

    void reset() {
        ((Buffer) out).reset();
    }

    private static class Buffer extends ByteArrayOutputStream {
        Buffer(int capacity) {
            super(capacity);
        }

        byte[] buffer() {
            return buf;
        }

        int length() {
            return count;
        }
    }
}
