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

import java.util.Arrays;

/**
 * Cache which uses the class of a MemberRef as the lookup key, and a byte array as the stored
 * key. Stored keys are softly referenced.
 *
 * @author Brian S. O'Neill
 */
abstract class MemberRefClassCache<V, X extends Exception>
    extends LookupCache<MemberRef, byte[], V, X>
{
    public MemberRefClassCache() {
    }

    @Override
    protected int hash(MemberRef lookupKey) {
        return Utils.hash(lookupKey.buffer(), lookupKey.classOffset(), lookupKey.classLength());
    }

    @Override
    protected byte[] toStoredKey(MemberRef lookupKey) {
        return lookupKey.copyClass();
    }

    @Override
    protected boolean matches(MemberRef lookupKey, byte[] existingKey) {
        int offset = lookupKey.classOffset();
        return Arrays.equals(lookupKey.buffer(), offset, offset + lookupKey.classLength(),
                             existingKey, 0, existingKey.length);
    }

    @Override
    protected boolean equals(byte[] newKey, byte[] existingKey) {
        return Arrays.equals(newKey, existingKey);
    }
}
