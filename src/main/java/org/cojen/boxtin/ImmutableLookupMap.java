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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import java.util.stream.Stream;

/**
 * Immutable map which supports a lookup key which can differ from the stored key.
 *
 * @author Brian S. O'Neill
 * @param <LK> lookup key type
 * @param <SK> stored key type
 * @param <V> value type
 */
abstract class ImmutableLookupMap<LK, SK, V> implements Iterable<Map.Entry<SK, V>> {
    private final Entry<SK, V>[] mEntries;

    /**
     * @param capacity fixed amount of entries to hold
     * @param populator provides the map entries; must not have any duplicate keys
     */
    @SuppressWarnings({"unchecked"})
    public ImmutableLookupMap(int capacity, Stream<Map.Entry<SK, V>> populator) {
        if (capacity == 0) {
            mEntries = null;
        } else {
            mEntries = new Entry[Utils.roundUpPower2(capacity)];

            populator.forEach((Map.Entry<SK, V> e) -> {
                SK storedKey = e.getKey();
                int hash = storedHash(storedKey);
                int index = hash & (mEntries.length - 1);
                mEntries[index] = new Entry(storedKey, hash, e.getValue(), mEntries[index]);
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

    @Override
    public Iterator<Map.Entry<SK, V>> iterator() {
        if (mEntries == null) {
            return Collections.emptyIterator();
        }

        return new Iterator<>() {
            private int mSlot;
            private Entry<SK, V> mNext;

            @Override
            public boolean hasNext() {
                if (mNext != null) {
                    return true;
                }
                return prepareNext();
            }

            @Override
            public Map.Entry<SK, V> next() {
                Entry<SK, V> next = mNext;
                if (next == null) {
                    if (!prepareNext()) {
                        throw new NoSuchElementException();
                    }
                    next = mNext;
                }
                if ((mNext = next.mNext) == null) {
                    mSlot++;
                }
                return next;
            }

            private boolean prepareNext() {
                while (true) {
                    if (mSlot >= mEntries.length) {
                        return false;
                    }
                    Entry<SK, V> next = mEntries[mSlot];
                    if (next != null) {
                        mNext = next;
                        return true;
                    }
                    mSlot++;
                }
            }
        };
    }

    public List<Map.Entry<SK, V>> sortEntries(Comparator<SK> comparator) {
        if (mEntries == null) {
            return List.of();
        }

        var list = new ArrayList<Map.Entry<SK, V>>(mEntries.length);

        for (Map.Entry<SK, V> e : this) {
            list.add(e);
        }

        Collections.sort(list, (a, b) -> comparator.compare(a.getKey(), b.getKey()));

        return list;
    }

    /**
     * Note: Only truly works when the populators provided entries in the same order.
     */
    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ImmutableLookupMap other
            && Arrays.equals(mEntries, other.mEntries);
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

    private static class Entry<SK, V> implements Map.Entry<SK, V> {
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

        @Override
        public SK getKey() {
            return mStoredKey;
        }

        @Override
        public V getValue() {
            return mValue;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            return mHash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Entry other && chainEquals(this, other);
        }

        private static boolean chainEquals(Entry<?, ?> entry, Entry<?, ?> other) {
            while (true) {
                if (entry.mHash == other.mHash
                    && Objects.deepEquals(entry.mValue, other.mValue)
                    && Objects.deepEquals(entry.mStoredKey, other.mStoredKey))
                {
                    entry = entry.mNext;
                    other = other.mNext;
                    if (entry == null) {
                        return other == null;
                    } else if (other == null) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
    }
}
