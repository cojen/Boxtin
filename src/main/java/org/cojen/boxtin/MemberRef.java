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

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UTFDataFormatException;

import java.lang.instrument.IllegalClassFormatException;

import java.util.Arrays;

import static java.lang.invoke.MethodHandleInfo.*;

/**
 * Defines a mutable reference to a field or method descriptor.
 *
 * @author Brian S. O'Neill
 */
final class MemberRef {
    private final byte[] mBuffer;

    private int mClassOffset, mClassLength;
    private int mNameOffset, mNameLength;
    private int mDescOffset, mDescLength;

    private int mPlainClassOffset = -1;

    MemberRef(byte[] buffer) {
        mBuffer = buffer;
    }

    MemberRef(String className, String name, String descriptor) {
        BasicEncoder encoder = UTFEncoder.localEncoder();

        try {
            encoder.writeUTF(className.replace('.', '/'));
            mClassOffset = 2;
            int pos = encoder.length();
            mClassLength = pos - mClassOffset;

            encoder.writeUTF(name);
            mNameOffset = pos + 2;
            pos = encoder.length();
            mNameLength = pos - mNameOffset;

            encoder.writeUTF(descriptor);
            mDescOffset = pos + 2;
            pos = encoder.length();
            mDescLength = pos - mDescOffset;

            mBuffer = encoder.toByteArray();
        } catch (IOException e) {
            // Not expected.
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns a buffer which contains the descriptor data.
     */
    public byte[] buffer() {
        return mBuffer;
    }

    /**
     * Returns the buffer slice offset for the referenced enclosing class, which is a fully
     * qualified name.
     */
    public int classOffset() {
        return mClassOffset;
    }

    void classOffset(int offset) {
        mClassOffset = offset;
        mPlainClassOffset = -1;
    }

    /**
     * Returns the buffer slice length for the referenced enclosing class, which is a fully
     * qualified name.
     */
    public int classLength() {
        return mClassLength;
    }

    void classLength(int length) {
        mClassLength = length;
        mPlainClassOffset = -1;
    }

    public byte[] copyClass() {
        return Arrays.copyOfRange(mBuffer, mClassOffset, mClassOffset + mClassLength);
    }

    /**
     * Decodes the fully qualified class name as a string.
     *
     * @throws UTFDataFormatException if the UTF-8 encoded string is malformed
     */
    public String decodeClassString() throws UTFDataFormatException {
        return decodeUTF(new BasicDecoder(mBuffer), mClassOffset);
    }

    /**
     * Returns the buffer slice offset for the referenced enclosing package, which is the same
     * as the class offset.
     */
    public int packageOffset() {
        return mClassOffset;
    }

    /**
     * Returns the buffer slice length for the referenced enclosing package, without a trailing
     * slash.
     */
    public int packageLength() {
        return Math.max(0, plainClassOffset() - mClassOffset - 1);
    }

    public byte[] copyPackage() {
        return Arrays.copyOfRange(mBuffer, mClassOffset, mClassOffset + packageLength());
    }

    /**
     * Returns the offset to the first character of the enclosing class name, within its
     * package. If this offset is the same as the fully qualified class offset, then the
     * package name is empty.
     */
    public int plainClassOffset() {
        int offset = mPlainClassOffset;

        if (offset < 0) {
            offset = mClassOffset + mClassLength;
            while (true) {
                offset--;
                if (offset < mClassOffset || mBuffer[offset] == '/') {
                    break;
                }
            }
            offset++;
        }

        return offset;
    }

    /**
     * Returns the length of the plain enclosing class name.
     */
    public int plainClassLength() {
        return mClassOffset + mClassLength - plainClassOffset();
    }

    public byte[] copyPlainClass() {
        return Arrays.copyOfRange(mBuffer, plainClassOffset(), mClassOffset + mClassLength);
    }

    /**
     * Returns the buffer slice offset for the encoded field or method name.
     */
    public int nameOffset() {
        return mNameOffset;
    }

    void nameOffset(int offset) {
        mNameOffset = offset;
    }

    /**
     * Returns the buffer slice length for the encoded field or method name.
     */
    public int nameLength() {
        return mNameLength;
    }

    void nameLength(int length) {
        mNameLength = length;
    }

    public byte[] copyName() {
        return Arrays.copyOfRange(mBuffer, mNameOffset, mNameOffset + mNameLength);
    }

    /**
     * @throws UTFDataFormatException if the UTF-8 encoded string is malformed
     */
    public String decodeNameString() throws UTFDataFormatException {
        return decodeUTF(new BasicDecoder(mBuffer), mNameOffset);
    }

    /**
     * Returns the buffer slice offset for the encoded field or method descriptor.
     */
    public int descriptorOffset() {
        return mDescOffset;
    }

    void descriptorOffset(int offset) {
        mDescOffset = offset;
    }

    /**
     * Returns the buffer slice length for the encoded field or method descriptor.
     */
    public int descriptorLength() {
        return mDescLength;
    }

    void descriptorLength(int length) {
        mDescLength = length;
    }

    public byte[] copyDescriptor() {
        return Arrays.copyOfRange(mBuffer, mDescOffset, mDescOffset + mDescLength);
    }

    /**
     * @throws UTFDataFormatException if the UTF-8 encoded string is malformed
     */
    public String decodeDescriptorString() throws UTFDataFormatException {
        return decodeUTF(new BasicDecoder(mBuffer), mDescOffset);
    }

    /**
     * Returns true if the name is <init>.
     */
    public boolean isConstructor() {
        if (mNameLength != 6) {
            return false;
        }
        byte[] buffer = mBuffer;
        int offset = mNameOffset;
        return Utils.decodeIntBE(buffer, offset) == 0x3c696e69 // <ini
            && Utils.decodeUnsignedShortBE(buffer, offset + 4) == 0x743e; // t>
    }

    /**
     * Returns a hash code which covers all of the elements.
     */
    public int fullHash() {
        byte[] buffer = mBuffer;
        int hash = Utils.hash(buffer, mClassOffset, mClassLength);
        hash = hash * 31 + Utils.hash(buffer, mNameOffset, mNameLength);
        hash = hash * 31 + Utils.hash(buffer, mDescOffset, mDescLength);
        return hash;
    }

    /**
     * Encodes all elements into a single byte array, with ';' characters as separators.
     */
    public byte[] encodeFull() {
        byte[] storedKey = new byte[mClassLength + mNameLength + mDescLength + 2];
        byte[] buffer = buffer();
        System.arraycopy(buffer, classOffset(), storedKey, 0, mClassLength);
        int offset = mClassLength;
        storedKey[offset++] = ';';
        System.arraycopy(buffer, nameOffset(), storedKey, offset, mNameLength);
        offset += mNameLength;
        storedKey[offset++] = ';';
        System.arraycopy(buffer, descriptorOffset(), storedKey, offset, mDescLength);
        assert offset + mDescLength == storedKey.length;
        return storedKey;
    }

    /**
     * Returns true if the given fully encoded descriptor matches this reference.
     */
    public boolean equalsFull(byte[] full) {
        byte[] buffer = buffer();

        int length = mClassLength;
        if (!sliceEquals(buffer, mClassOffset, length, full, 0)) {
            return false;
        }

        int existingOffset = length;
        if (existingOffset >= full.length || full[existingOffset++] != ';') {
            return false;
        }

        length = mNameLength;
        if (!sliceEquals(buffer, mNameOffset, length, full, existingOffset)) {
            return false;
        }

        existingOffset += length;
        if (existingOffset >= full.length || full[existingOffset++] != ';') {
            return false;
        }

        length = mDescLength;
        if (!sliceEquals(buffer, mDescOffset, length, full, existingOffset)) {
            return false;
        }

        return existingOffset + length == full.length;
    }

    private static boolean sliceEquals(byte[] buffer, int offset, int length,
                                       byte[] key, int keyOffset)
    {
        int keyEnd = keyOffset + length;
        return keyEnd > key.length ? false
            : Arrays.equals(buffer, offset, offset + length, key, keyOffset, keyEnd);
    }

    /**
     * Returns a method descriptor which matches the operand stack before and after an
     * operation against this member.
     *
     * @param opOrKind bytecode op or CONSTANT_MethodHandle kind
     * @throws IllegalArgumentException if an unsupported opOrKind is given
     */
    byte[] compatibleMethodDescriptor(int opOrKind) throws IllegalClassFormatException {
        switch (opOrKind) {
            default -> throw new IllegalArgumentException();

            // GETSTATIC
            case 178, REF_getStatic -> {
                var desc = new byte[mDescLength + 2];
                desc[0] = '(';
                desc[1] = ')';
                System.arraycopy(mBuffer, mDescOffset, desc, 2, mDescLength);
                return desc;
            }

            // PUTSTATIC
            case 179, REF_putStatic -> {
                var desc = new byte[mDescLength + 3];
                int off = 0;
                desc[off++] = '(';
                System.arraycopy(mBuffer, mDescOffset, desc, off, mDescLength);
                off += mDescLength;
                desc[off++] = ')';
                desc[off++] = 'V';
                assert off == desc.length;
                return desc;
            }

            // GETFIELD
            case 180, REF_getField -> {
                var desc = new byte[mClassLength + mDescLength + 4];
                int off = 0;
                desc[off++] = '(';
                desc[off++] = 'L';
                System.arraycopy(mBuffer, mClassOffset, desc, off, mClassLength);
                off += mClassLength;
                desc[off++] = ';';
                desc[off++] = ')';
                System.arraycopy(mBuffer, mDescOffset, desc, off, mDescLength);
                assert off + mDescLength == desc.length;
                return desc;
            }

            // PUTFIELD
            case 181, REF_putField -> {
                var desc = new byte[mClassLength + mDescLength + 5];
                int off = 0;
                desc[off++] = '(';
                desc[off++] = 'L';
                System.arraycopy(mBuffer, mClassOffset, desc, off, mClassLength);
                off += mClassLength;
                desc[off++] = ';';
                System.arraycopy(mBuffer, mDescOffset, desc, off, mDescLength);
                off += mDescLength;
                desc[off++] = ')';
                desc[off++] = 'V';
                assert off == desc.length;
                return desc;
            }

            // INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE
            case 182, 183, 185,
                REF_invokeVirtual, REF_invokeSpecial, REF_newInvokeSpecial, REF_invokeInterface ->
            {
                if (mBuffer[mDescOffset] != '(') {
                    throw new IllegalClassFormatException();
                }
                var desc = new byte[mClassLength + mDescLength + 2];
                int off = 0;
                desc[off++] = '(';
                desc[off++] = 'L';
                System.arraycopy(mBuffer, mClassOffset, desc, off, mClassLength);
                off += mClassLength;
                desc[off++] = ';';
                System.arraycopy(mBuffer, mDescOffset + 1, desc, off, mDescLength - 1);
                assert off + mDescLength - 1 == desc.length;
                return desc;
            }

            // INVOKESTATIC
            case 184, REF_invokeStatic -> {
                return Arrays.copyOfRange(mBuffer, mDescOffset, mDescOffset + mDescLength);
            }
        }
    }

    @Override
    public String toString() {
        var decoder = new BasicDecoder(mBuffer);
        return new StringBuilder()
            .append("{class=").append(decodeUTFNoEx(decoder, mClassOffset))
            .append(", name=").append(decodeUTFNoEx(decoder, mNameOffset))
            .append(", desc=").append(decodeUTFNoEx(decoder, mDescOffset))
            .append('}').toString();
    }

    private static String decodeUTF(BasicDecoder decoder, int offset)
        throws UTFDataFormatException
    {
        decoder.offset(offset - 2); // set to the offset of the length field
        try {
            return decoder.readUTF();
        } catch (UTFDataFormatException e) {
            throw e;
        } catch (IOException e) { // catch EOFException and plain IOException (not expected)
            throw new UTFDataFormatException("eof");
        }
    }

    private static String decodeUTFNoEx(BasicDecoder decoder, int offset) {
        try {
            return decodeUTF(decoder, offset);
        } catch (UTFDataFormatException e) {
            return e.toString();
        }
    }
}
