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

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class SimpleReplacement extends BufferEncoder implements RegionReplacement {
    private final long mOriginalLength;

    SimpleReplacement(int originalLength) {
        this(originalLength, originalLength);
    }

    SimpleReplacement(long originalLength, int capacity) {
        super(capacity);
        mOriginalLength = originalLength;
    }

    @Override
    public long finish(ConstantPool cp, byte[] originalBuffer) {
        return length();
    }

    @Override
    public long originalLength() {
        return mOriginalLength;
    }

    @Override
    public void writeTo(BufferEncoder encoder) throws IOException {
        encoder.write(buffer(), 0, length());
    }
}

