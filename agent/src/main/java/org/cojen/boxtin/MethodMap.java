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

/**
 * Maps of method definitions, used for determining if any invocations refer to them.
 *
 * @author Brian S. O'Neill
 */
final class MethodMap {
    private Entry[] mEntries;

    MethodMap(int capacity) {
        mEntries = new Entry[Utils.roundUpPower2(capacity)];
    }

    /**
     * Puts a method into the map, unless it refers to a constructor.
     */
    void put(ConstantPool cp, int name_index, int desc_index) {
        ConstantPool.C_UTF8 name = cp.findConstantUTF8(name_index);
        if (!name.isConstructor()) {
            ConstantPool.C_UTF8 desc = cp.findConstantUTF8(desc_index);
            int hash = hash(name, desc);
            var entries = mEntries;
            int slot = hash & (entries.length - 1);
            entries[slot] = new Entry(name, desc, hash, entries[slot]);
        }
    }

    boolean contains(ConstantPool.C_MemberRef methodRef) {
        return contains(methodRef.mNameAndType);
    }

    boolean contains(ConstantPool.C_NameAndType nat) {
        ConstantPool.C_UTF8 name = nat.mName;
        ConstantPool.C_UTF8 desc = nat.mTypeDesc;
        int hash = hash(name, desc);
        var entries = mEntries;
        for (var e = entries[hash & (entries.length - 1)]; e != null; e = e.mNext) {
            if (equals(e.mName, name) && equals(e.mDesc, desc)) {
                return true;
            }
        }
        return false;
    }

    private static int hash(ConstantPool.C_UTF8 a, ConstantPool.C_UTF8 b) {
        return a.hashCode() * 31 + b.hashCode();
    }

    private static boolean equals(ConstantPool.C_UTF8 a, ConstantPool.C_UTF8 b) {
        return a.mIndex == b.mIndex || a.equals(b);
    }

    private static final class Entry {
        final ConstantPool.C_UTF8 mName, mDesc;
        final int mHash;
        final Entry mNext;

        Entry(ConstantPool.C_UTF8 name, ConstantPool.C_UTF8 desc, int hash, Entry next) {
            mName = name;
            mDesc = desc;
            mHash = hash;
            mNext = next;
        }
    }
}
