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

import java.util.Map;

import java.util.stream.Stream;

/**
 * Immutable map which uses part of a MemberRef as the lookup key, and a byte array as the
 * stored key.
 *
 * @author Brian S. O'Neill
 */
abstract class MemberRefLookupMap<V> extends ImmutableLookupMap<MemberRef, byte[], V> {
    public MemberRefLookupMap(int size, Stream<Map.Entry<String, V>> populator) {
        super(size,
              populator.map((Map.Entry<String, V> e) -> {
                  return Map.entry(UTFEncoder.encode(e.getKey()), e.getValue());
              })
        );
    }

    @Override
    protected final int storedHash(byte[] storedKey) {
        return Utils.hash(storedKey);
    }
}
