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
 * Implements a simple growable int array.
 *
 * @author Brian S. O'Neill
 */
final class IntArray {
    private int[] mArray;
    private int mLength;

    IntArray(int capacity) {
        mArray = new int[capacity];
    }

    IntArray(int[] array) {
        mArray = array;
        mLength = array.length;
    }

    private IntArray(int[] array, int length) {
        mArray = array;
        mLength = length;
    }

    public int length() {
        return mLength;
    }

    public void length(int length) {
        int[] array = mArray;
        if (length > mLength) {
            int limit = Math.min(length, array.length);
            for (int i=mLength; i<limit; i++) {
                array[i] = 0;
            }
        }
        if (length > array.length) {
            mArray = Arrays.copyOf(array, Math.max(length, array.length << 1));
        }
        mLength = length;
    }

    public int get(int index) {
        return mArray[index];
    }

    public int getLast() {
        return mArray[mLength - 1];
    }

    public void set(int index, int value) {
        int[] array = mArray;
        if (index >= mLength) {
            int limit = Math.min(index, array.length);
            for (int i=mLength; i<limit; i++) {
                array[i] = 0;
            }
            if (index >= array.length) {
                mArray = array = Arrays.copyOf(array, Math.max(index + 1, (array.length + 1) << 1));
            }
            mLength = index + 1;
        }
        array[index] = value;
    }

    public void setLast(int value) {
        mArray[mLength - 1] = value;
    }

    public void push(int value) {
        set(mLength, value);
    }

    public int pop() {
        int length = mLength - 1;
        int value = mArray[length];
        mLength = length;
        return value;
    }

    public void pop(int amount) {
        int length = mLength - amount;
        if (length < 0) {
            throw new IllegalStateException();
        }
        mLength = length;
    }

    public void replaceAll(int match, int replacement) {
        int length = mLength;
        int[] array = mArray;
        for (int i=0; i<length; i++) {
            if (array[i] == match) {
                array[i] = replacement;
            }
        }
    }

    public IntArray copy() {
        return copy(0);
    }

    public IntArray copy(int minCapacity) {
        var array = new int[Math.max(minCapacity, mArray.length)];
        System.arraycopy(mArray, 0, array, 0, mLength);
        return new IntArray(array, mLength);
    }

    /**
     * Return a reference to the contents of this array, but with a smaller length.
     */
    public IntArray chop(int chop) {
        int length = mLength - chop;
        if (length < 0) {
            throw new IllegalStateException();
        }
        return new IntArray(mArray, length);
    }

    /**
     * Returns a new array, with a larger length. The new array elements are all 0.
     */
    public IntArray append(int append) {
        var array = new int[mLength + append];
        System.arraycopy(mArray, 0, array, 0, mLength);
        return new IntArray(array);
    }

    public int mismatch(IntArray other) {
        return Arrays.mismatch(mArray, 0, mLength, other.mArray, 0, other.mLength);
    }

    @Override
    public String toString() {
        var b = new StringBuilder().append('[');
        for (int i=0; i<mLength; i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(mArray[i]);
        }
        return b.append(']').toString();
    }
}
