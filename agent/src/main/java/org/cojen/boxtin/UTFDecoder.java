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

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UTFDataFormatException;

/**
 * Utility which decodes strings from a modified UTF-8 format.
 *
 * @author Brian S. O'Neill
 */
final class UTFDecoder extends DataInputStream {
    UTFDecoder() {
        super(new Buffer());
    }

    String decode(byte[] buf) throws UTFDataFormatException {
        return decode(buf, 0, buf.length);
    }

    String decode(byte[] buf, int off, int len) throws UTFDataFormatException {
        if (len < 0 || len > 65535) {
            throw new IllegalArgumentException();
        }

        ((Buffer) in).prepare(buf, off, len);

        try {
            return readUTF();
        } catch (UTFDataFormatException e) {
            throw e;
        } catch (IOException e) {
            // Not expected.
            throw new UncheckedIOException(e);
        }
    }

    private static final class Buffer extends InputStream {
        private byte[] mBuffer;
        private int mOffset;
        private int mLength;
        private int mLengthPos;

        Buffer() {
        }

        @Override
        public int read() {
            if (mLengthPos < 2) {
                return mLengthPos++ == 0 ? (mLength >> 8) & 0xff : (mLength & 0xff);
            } else {
                return mBuffer[mOffset++] & 0xff;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len <= 0) {
                return 0;
            }

            if (mLengthPos < 2) {
                b[off] = (byte) (mLength >> 8);
                if (len < 2) {
                    mLengthPos++;
                    return 1;
                } else {
                    mLengthPos += 2;
                    b[off + 1] = (byte) mLength;
                    return 2;
                }
            }

            System.arraycopy(mBuffer, mOffset, b, off, len);
            mOffset += len;

            return len;
        }

        private void prepare(byte[] buf, int off, int len) {
            mBuffer = buf;
            mOffset = off;
            mLength = len;
            mLengthPos = 0;
        }
    }
}
