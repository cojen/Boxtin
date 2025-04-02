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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Decodes the primitive data elements of a class file.
 *
 * @author Brian S. O'Neill
 */
final class BasicDecoder extends DataInputStream {
    BasicDecoder(byte[] buffer) {
        super(new Buffer(buffer));
    }

    public long readUnsignedInt() throws IOException {
        return readInt() & 0xffff_ffffL;
    }

    byte[] buffer() {
        return ((Buffer) in).buffer();
    }

    /**
     * Returns the offset of the next byte to read from the byte array.
     */
    int offset() {
        return ((Buffer) in).offset();
    }

    /**
     * Sets the offset to read from.
     */
    void offset(int offset) {
        ((Buffer) in).offset(offset);
    }

    private static class Buffer extends ByteArrayInputStream {
        Buffer(byte[] buffer) {
            super(buffer);
        }

        byte[] buffer() {
            return buf;
        }

        int offset() {
            return pos;
        }

        void offset(int offset) {
            pos = offset;
        }
    }
}
