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

import java.lang.invoke.VarHandle;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

import java.util.function.BiConsumer;

/**
 * Cache which supports a lookup key which can differ from the stored key. Stored keys are
 * softly referenced.
 *
 * @author Brian S. O'Neill
 * @param <LK> lookup key type
 * @param <SK> stored key type
 * @param <V> value type
 * @param <X> exception type thrown when creating new values
 */
abstract class LookupCache<LK, SK, V, X extends Exception> extends ReferenceQueue<Object> {
    private Entry<SK, V>[] mEntries;
    private int mSize;

    @SuppressWarnings({"unchecked"})
    public LookupCache() {
        // Initial capacity must be a power of 2.
        mEntries = new Entry[2];
    }

    public final V obtain(LK lookupKey) throws X {
        Object ref = poll();
        if (ref != null) {
            synchronized (this) {
                cleanup(ref);
            }
        }

        int hash = hash(lookupKey);

        // First try to find the value without synchronization.
        V value = find(lookupKey, hash);
        if (value != null) {
            return value;
        }
        
        synchronized (this) {
            value = find(lookupKey, hash);
            if (value != null) {
                return value;
            }
        }

        SK newKey = toStoredKey(lookupKey);
        value = newValue(lookupKey, newKey);
        put(newKey, hash, value);

        return value;
    }

    public synchronized void forEach(BiConsumer<? super SK, ? super V> action) {
        Entry<SK, V>[] entries = mEntries;
        for (int i=0; i<entries.length; i++) {
            for (var e = entries[i]; e != null; e = e.mNext) {
                SK key = e.get();
                if (key != null) {
                    action.accept(key, e.mValue);
                }
            }
        }
    }

    private V find(LK lookupKey, int hash) {
        Entry<SK, V>[] entries = mEntries;
        for (var e = entries[hash & (entries.length - 1)]; e != null; e = e.mNext) {
            if (hash == e.mHash) {
                SK existingKey = e.get();
                if (existingKey != null && matches(lookupKey, existingKey)) {
                    return e.mValue;
                }
            }
        }
        return null;
    }

    private synchronized void put(SK newKey, int hash, V value) {
        Object ref = poll();
        if (ref != null) {
            cleanup(ref);
        }

        Entry<SK, V>[] entries = mEntries;
        int index = hash & (entries.length - 1);

        for (Entry<SK, V> e = entries[index], prev = null; e != null; e = e.mNext) {
            if (hash == e.mHash) {
                SK existingKey = e.get();
                if (existingKey != null && equals(newKey, existingKey)) {
                    e.clear();
                    var newEntry = new Entry<>(newKey, this, hash, value);
                    if (prev == null) {
                        newEntry.mNext = e.mNext;
                    } else {
                        prev.mNext = e.mNext;
                        newEntry.mNext = entries[index];
                    }
                    VarHandle.storeStoreFence(); // ensure that entry value is safely visible
                    entries[index] = newEntry;
                    return;
                }
            }

            prev = e;
        }

        if (mSize >= entries.length) {
            // Rehash.
            @SuppressWarnings({"unchecked"})
            Entry<SK, V>[] newEntries = new Entry[entries.length << 1];
            int size = 0;
            for (int i=0; i<entries.length; i++) {
                for (var existing = entries[i]; existing != null; ) {
                    var e = existing;
                    existing = existing.mNext;
                    if (!e.refersTo(null)) {
                        size++;
                        index = e.mHash & (newEntries.length - 1);
                        e.mNext = newEntries[index];
                        newEntries[index] = e;
                    }
                }
            }
            mEntries = entries = newEntries;
            mSize = size;
            index = hash & (entries.length - 1);
        }

        var newEntry = new Entry<>(newKey, this, hash, value);
        newEntry.mNext = entries[index];
        VarHandle.storeStoreFence(); // ensure that entry value is safely visible
        entries[index] = newEntry;
        mSize++;
    }

    /**
     * Computes the hash code for a lookup key.
     */
    protected abstract int hash(LK lookupKey);

    /**
     * Returns a stored key instance which matches the given lookup key.
     */
    protected abstract SK toStoredKey(LK lookupKey);

    /**
     * Returns true if the given lookup key matches the stored key.
     */
    protected abstract boolean matches(LK lookupKey, SK existingKey);

    /**
     * Returns true if the given stored keys are equal to each other.
     */
    protected abstract boolean equals(SK newKey, SK existingKey);

    /**
     * Is called by the obtain method when a value needs to be created, but without any
     * synchronization. Multiple threads might request the same value concurrently.
     *
     * @param lookupKey was passed to the obtain method
     * @param newKey was produced by the toStoredKey method
     */
    protected abstract V newValue(LK lookupKey, SK newKey) throws X;

    /**
     * Caller must be synchronized.
     *
     * @param ref not null
     */
    private void cleanup(Object ref) {
        Entry<SK, V>[] entries = mEntries;
        do {
            @SuppressWarnings({"unchecked"})
            var cleared = (Entry<SK, V>) ref;
            int ix = cleared.mHash & (entries.length - 1);
            for (Entry<SK, V> e = entries[ix], prev = null; e != null; e = e.mNext) {
                if (e == cleared) {
                    if (prev == null) {
                        entries[ix] = e.mNext;
                    } else {
                        prev.mNext = e.mNext;
                    }
                    mSize--;
                    break;
                } else {
                    prev = e;
                }
            }
        } while ((ref = poll()) != null);
    }

    private static class Entry<SK, V> extends SoftReference<SK> {
        final int mHash;
        final V mValue;

        Entry<SK, V> mNext;

        Entry(SK storedKey, ReferenceQueue<Object> queue, int hash, V value) {
            super(storedKey, queue);
            mHash = hash;
            mValue = value;
        }
    }
}
