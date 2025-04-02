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
import java.io.UncheckedIOException;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import java.util.Arrays;

/**
 * Standalone utility which encodes strings to a modified UTF-8 format.
 *
 * @author Brian S. O'Neill
 */
final class UTFEncoder {
    private static final SoftCache<String, byte[]> cCache = new SoftCache<>();

    private static final ThreadLocal<Reference<BasicEncoder>> cLocalEncoder = new ThreadLocal<>();

    /**
     * Note: the returned byte array is shared and so it shouldn't be modified
     */
    static byte[] encode(String str) {
        byte[] bytes = cCache.get(str);

        if (bytes != null) {
            return bytes;
        }

        Reference<BasicEncoder> encoderRef = cLocalEncoder.get();
        BasicEncoder encoder;

        if (encoderRef == null || (encoder = encoderRef.get()) == null) {
            encoder = new BasicEncoder(50);
            encoderRef = new WeakReference<>(encoder);
            cLocalEncoder.set(encoderRef);
        }

        encoder.reset();

        synchronized (cCache) {
            bytes = cCache.get(str);
            if (bytes != null) {
                return bytes;
            }
        }

        try {
            encoder.writeUTF(str);
        } catch (IOException e) {
            // Not expected.
            throw new UncheckedIOException(e);
        }
        
        bytes = Arrays.copyOfRange(encoder.buffer(), 2, encoder.length());

        synchronized (cCache) {
            cCache.put(str, bytes);
        }
        
        return bytes;
    }

    private UTFEncoder() {
    }
}
