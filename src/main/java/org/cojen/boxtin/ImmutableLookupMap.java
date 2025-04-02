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
 * Immutable map which supports a lookup key which can differ from the stored key.
 *
 * @author Brian S. O'Neill
 * @param <LK> lookup key type
 * @param <SK> stored key type
 * @param <V> value type
 */
abstract class ImmutableLookupMap<LK, SK, V> {
    private final Entry<SK, V>[] mEntries;

    /**
     * @param size fixed amount of entries to hold
     * @param populator provides the map entries; must not have any duplicate keys
     */
    @SuppressWarnings({"unchecked"})
    public ImmutableLookupMap(int size, Stream<Map.Entry<SK, V>> populator) {
        if (size == 0) {
            mEntries = null;
        } else {
            mEntries = new Entry[Utils.roundUpPower2(size)];

            populator.forEach((Map.Entry<SK, V> pEntry) -> {
                SK storedKey = pEntry.getKey();
                int hash = storedHash(storedKey);
                int index = hash & (mEntries.length - 1);
                mEntries[index] = new Entry(storedKey, hash, pEntry.getValue(), mEntries[index]);
            });
        }
    }

    public final V get(LK lookupKey) {
        int hash = lookupHash(lookupKey);
        Entry<SK, V>[] entries = mEntries;
        if (entries != null) {
            for (var e = entries[hash & (entries.length - 1)]; e != null; e = e.mNext) {
                if (hash == e.mHash && matches(lookupKey, e.mStoredKey)) {
                    return e.mValue;
                }
            }
        }
        return null;
    }

    public final boolean isEmpty() {
        return mEntries == null;
    }

    /**
     * Computes the hash code for a lookup key, which must be the same as for the corresponding
     * stored key.
     */
    protected abstract int lookupHash(LK lookupKey);

    /**
     * Computes the hash code for a stored key, which must be the same as for the corresponding
     * lookup key.
     */
    protected abstract int storedHash(SK storedKey);

    /**
     * Returns true if the given lookup key matches the stored key.
     */
    protected abstract boolean matches(LK lookupKey, SK existingKey);

    private static class Entry<SK, V> {
        final SK mStoredKey;
        final int mHash;
        final V mValue;
        final Entry<SK, V> mNext;

        Entry(SK storedKey, int hash, V value, Entry<SK, V> next) {
            mStoredKey = storedKey;
            mHash = hash;
            mValue = value;
            mNext = next;
        }
    }
}
