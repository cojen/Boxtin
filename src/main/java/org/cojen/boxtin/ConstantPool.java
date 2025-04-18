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

import java.lang.invoke.MethodHandleInfo;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ThreadLocalRandom;

import static org.cojen.boxtin.Opcodes.*;

/**
 * Supports cheap decoding of a class file's constant pool.
 *
 * @author Brian S. O'Neill
 */
final class ConstantPool {
    static ConstantPool decode(BufferDecoder decoder)
        throws IOException, ClassFormatException
    {
        var offsets = new int[decoder.readUnsignedShort() - 1];

        int[] methodHandleOffsets = null;
        int numMethodHandles = 0;

        for (int i=0; i<offsets.length; i++) {
            offsets[i] = decoder.offset();

            decoder.skipNBytes(switch (decoder.readUnsignedByte()) {
                default -> throw new ClassFormatException();

                // CONSTANT_Utf8
                case 1 -> decoder.readUnsignedShort();

                // CONSTANT_Integer, CONSTANT_Float,
                // CONSTANT_Fieldref, CONSTANT_Methodref, CONSTANT_InterfaceMethodref,
                // CONSTANT_NameAndType, CONSTANT_Dynamic, CONSTANT_InvokeDynamic
                case 3, 4, 9, 10, 11, 12, 17, 18 -> 4;

                // CONSTANT_Long, CONSTANT_Double
                case 5, 6 -> {
                    offsets[++i] = -1;
                    yield 8;
                }

                // CONSTANT_Class, CONSTANT_String, CONSTANT_MethodType,
                // CONSTANT_Module, CONSTANT_Package
                case 7, 8, 16, 19, 20 -> 2;

                // CONSTANT_MethodHandle
                case 15 -> {
                    if (methodHandleOffsets == null) {
                        methodHandleOffsets = new int[4];
                    } else if (numMethodHandles >= methodHandleOffsets.length) {
                        methodHandleOffsets = Arrays.copyOf
                            (methodHandleOffsets, methodHandleOffsets.length << 1);
                    }
                    methodHandleOffsets[numMethodHandles++] = offsets[i] + 1;
                    yield 3;
                }
            });
        }

        return new ConstantPool(decoder, offsets, methodHandleOffsets);
    }

    private final BufferDecoder mDecoder;
    private final int mEndOffset;
    private final int[] mOffsets, mMethodHandleOffsets;

    private Constant[] mConstants;
    private Map<Constant, Constant> mMappedConstants;
    private List<Constant> mAddedConstants;

    private ConstantPool(BufferDecoder decoder, int[] offsets, int[] methodHandleOffsets) {
        mDecoder = decoder;
        mEndOffset = decoder.offset();
        mOffsets = offsets;
        mMethodHandleOffsets = methodHandleOffsets;
    }

    BufferDecoder decoder() {
        return mDecoder;
    }

    byte[] buffer() {
        return mDecoder.buffer();
    }

    int findConstantOffset(int index) throws ClassFormatException {
        try {
            int offset = mOffsets[index - 1];
            if (offset < 0) {
                throw new ClassFormatException("Accessing the second slot of a wide constant");
            }
            return offset;
        } catch (IndexOutOfBoundsException e) {
            throw new ClassFormatException("Invalid constant index");
        }
    }

    Constant findConstant(int index) throws ClassFormatException {
        Constant[] constants = mConstants;

        if (constants == null) {
            mConstants = constants = new Constant[mOffsets.length];
        } else {
            Constant c = constants[index - 1];
            if (c != null) {
                return c;
            }
        }

        final int originalOffset = mDecoder.offset();

        try {
            Constant c = resolveConstant(index);
            constants[index - 1] = c;
            return c;
        } catch (Exception e) {
            throw ClassFormatException.from(e);
        } finally {
            mDecoder.offset(originalOffset);
        }
    }

    /**
     * @throws ClassFormatException if cast fails
     */
    <C extends Constant> C findConstant(int index, Class<C> type) throws ClassFormatException {
        try {
            return type.cast(findConstant(index));
        } catch (ClassCastException e) {
            throw ClassFormatException.from(e);
        }
    }

    /**
     * @throws ClassFormatException if cast fails
     */
    C_UTF8 findConstantUTF8(int index) throws ClassFormatException {
        return findConstant(index, C_UTF8.class);
    }

    private Constant resolveConstant(int index) throws IOException, ClassFormatException {
        BufferDecoder decoder = mDecoder;

        decoder.offset(findConstantOffset(index));

        int tag = decoder.readUnsignedByte();

        Constant c;

        switch (tag) {
            default -> {
                // Unsupported type.
                return null;
            }

            // CONSTANT_Utf8
            case 1 -> {
                int length = decoder.readUnsignedShort();
                int offset = decoder.offset();
                c = new C_UTF8(tag, decoder.buffer(), offset, length);
            }

            // CONSTANT_Long
            case 5 -> {
                c = new C_Long(tag, decoder.readLong());
            }

            // CONSTANT_Double
            case 6 -> {
                c = new C_Double(tag, Double.longBitsToDouble(decoder.readLong()));
            }

            // CONSTANT_Class
            case 7 -> {
                int name_index = decoder.readUnsignedShort();
                c = new C_Class(tag, (C_UTF8) findConstant(name_index));
            }

            // CONSTANT_Fieldref, CONSTANT_Methodref, CONSTANT_InterfaceMethodref
            case 9, 10, 11 -> {
                int class_index = decoder.readUnsignedShort();
                int name_and_type_index = decoder.readUnsignedShort();
                c = new C_MemberRef(tag, (C_Class) findConstant(class_index),
                                    (C_NameAndType) findConstant(name_and_type_index));
            }

            // CONSTANT_NameAndType
            case 12 -> {
                int name_index = decoder.readUnsignedShort();
                int descriptor_index = decoder.readUnsignedShort();
                c = new C_NameAndType(tag, (C_UTF8) findConstant(name_index),
                                      (C_UTF8) findConstant(descriptor_index));
            }
        }

        c.mIndex = index;

        if (mMappedConstants != null) {
            mMappedConstants.put(c, c);
        }

        return c;
    }

    /**
     * Must be called before adding new constants.
     */
    void extend() throws ClassFormatException {
        try {
            doExtend();
        } catch (Exception e) {
            throw ClassFormatException.from(e);
        }
    }

    boolean hasBeenExtended() {
        return mAddedConstants != null;
    }

    private void doExtend() throws IOException {
        if (hasBeenExtended()) {
            return;
        }

        // Resolve the existing constants, to reduce duplication. Only bother doing this for
        // the types of constants which can be added.

        mMappedConstants = new LinkedHashMap<>(mOffsets.length << 1);
        mAddedConstants = new ArrayList<>();

        for (int i=0; i<mOffsets.length; i++) {
            Constant c = findConstant(i + 1);
            if (c != null && c.isWide()) {
                // Occupies two slots.
                i++;
            }
        }
    }

    /**
     * Returns the original size of the constant pool, in bytes.
     */
    long originalSize() {
        int startOffset = mOffsets[0];
        return 2L + mEndOffset - startOffset;
    }

    /**
     * Returns the extended length of the constant pool, in bytes.
     */
    long growth() {
        long size = 0;

        if (mAddedConstants != null) {
            for (Constant c : mAddedConstants) {
                if (c != null) {
                    size += c.size();
                }
            }
        }

        return size;
    }

    /**
     * Returns the size of the constant pool, in bytes.
     */
    long size() {
        return originalSize() + growth();
    }

    void writeTo(BufferEncoder encoder) throws IOException, ClassFormatException {
        int count = mOffsets.length + 1;
        if (mAddedConstants != null) {
            count += mAddedConstants.size();
        }
        if (count > 65535) {
            throw new ClassFormatException("Constant pool is full");
        }
        encoder.writeShort(count);
        int startOffset = mOffsets[0];
        encoder.write(buffer(), startOffset, mEndOffset - startOffset);
        for (Constant c : mAddedConstants) {
            if (c != null) {
                c.writeTo(encoder);
            }
        }
    }

    C_Long addLong(long value) {
        return addConstant(new C_Long(5, value));
    }

    C_Class addClass(String className) {
        return addConstant(new C_Class(7, addUTF8(className)));
    }

    C_MemberRef addFieldRef(String className, String name, String desc) {
        return addMemberRef(9, className, name, desc);
    }

    C_MemberRef addMethodRef(String className, String name, String desc) {
        return addMemberRef(10, className, name, desc);
    }

    private C_MemberRef addMemberRef(int tag, String className, String name, String desc) {
        return addConstant(new C_MemberRef(tag, addClass(className), addNameAndType(name, desc)));
    }

    C_NameAndType addNameAndType(String name, String desc) {
        return addConstant(new C_NameAndType(12, addUTF8(name), addUTF8(desc)));
    }

    C_UTF8 addUTF8(String str) {
        return addConstant(new C_UTF8(str));
    }

    C_String addString(C_UTF8 value) {
        return addConstant(new C_String(8, value));
    }

    /**
     * Adds a type signature constant which has been adapted for static invocation, by
     * prepending the class of the given methodRef as the first argument. The extend method
     * must have already been called.
     *
     * @param op must be an invoke operation or a MethodHandle reference kind
     * @return method type descriptor
     */
    C_UTF8 addWithStaticSignature(int op, C_MemberRef methodRef) {
        C_UTF8 typeDesc = methodRef.mNameAndType.mTypeDesc;

        if (op == INVOKESTATIC || op == MethodHandleInfo.REF_invokeStatic) {
            return typeDesc;
        }

        C_UTF8 className = methodRef.mClass.mValue;

        int classNameLen = className.mLength;
        var newTypeBuf = new byte[2 + classNameLen + typeDesc.mLength];

        newTypeBuf[0] = '(';
        newTypeBuf[1] = 'L';
        System.arraycopy(className.mBuffer, className.mOffset, newTypeBuf, 2, classNameLen);
        int offset = 2 + classNameLen;
        newTypeBuf[offset++] = ';';
        System.arraycopy(typeDesc.mBuffer, typeDesc.mOffset + 1,
                         newTypeBuf, offset, typeDesc.mLength - 1);

        return addConstant(new C_UTF8(1, newTypeBuf, 0, newTypeBuf.length));
    }

    /**
     * Adds a constant method reference, with an invented name. The extend method must have
     * already been called.
     */
    C_MemberRef addUniqueMethod(C_Class clazz, C_UTF8 typeDesc) {
        var nameBuf = new byte[1 + 9]; // one prefix byte plus up to nine digits
        nameBuf[0] = '$';
        int nameLength = 2; // start with one random digit

        var rnd = ThreadLocalRandom.current();
        C_UTF8 name;

        while (true) {
            for (int i=1; i<nameLength; i++) {
                nameBuf[i] = (byte) ('0' + rnd.nextInt(10));
            }
            name = new C_UTF8(1, nameBuf, 0, nameLength);
            if (mMappedConstants.putIfAbsent(name, name) == null) {
                registerNewConstant(name);
                break;
            }
            if (nameLength < nameBuf.length) {
                nameLength++; // add another random digit
            }
        }

        C_NameAndType nat = addConstant(new C_NameAndType(12, name, typeDesc));

        // CONSTANT_Methodref tag is 10.
        return addConstant(new C_MemberRef(10, clazz, nat));
    }

    @SuppressWarnings("unchecked")
    private <C extends Constant> C addConstant(C constant) {
        Constant existing = mMappedConstants.putIfAbsent(constant, constant);
        if (existing == null) {
            registerNewConstant(constant);
        } else {
            constant = (C) existing;
        }
        return constant;
    }

    private void registerNewConstant(Constant constant) {
        mAddedConstants.add(constant);
        constant.mIndex = mConstants.length + mAddedConstants.size();
        if (constant.isWide()) {
            mAddedConstants.add(null);
        }
    }

    static abstract class Constant {
        final int mTag;
        int mIndex;

        Constant(int tag) {
            mTag = tag;
        }

        boolean isWide() {
            return false;
        }

        /**
         * Returns the size of the constant, in bytes.
         */
        abstract long size();

        abstract void writeTo(BufferEncoder encoder) throws IOException;
    }

    final class C_UTF8 extends Constant implements CharSequence {
        private byte[] mBuffer;
        private int mOffset, mLength;

        // 0: initial, 1: ASCII string, 2: non-ASCII string
        private int mState;
        private int mHash;
        private String mStr;

        C_UTF8(int tag, byte[] buffer, int offset, int length) {
            super(tag);
            mBuffer = buffer;
            mOffset = offset;
            mLength = length;
        }

        C_UTF8(String value) {
            super(1);
            byte[] buffer = UTFEncoder.encode(value);
            mBuffer = buffer;
            mOffset = 0;
            mLength = buffer.length;
            mStr = value;
        }

        /**
         * Create an empty instance, to be used for referencing slices of other instances.
         */
        C_UTF8() {
            super(0); // illegal tag; indicates that decode works differently
        }

        /**
         * Returns true if the value is <init>.
         */
        boolean isConstructor() {
            int length = mLength;
            if (length != 6) {
                return false;
            }
            byte[] buffer = mBuffer;
            int offset = mOffset;
            return Utils.decodeIntBE(buffer, offset) == 0x3c696e69 // <ini
                && Utils.decodeUnsignedShortBE(buffer, offset + 4) == 0x743e; // t>
        }

        String str() throws ClassFormatException {
            String str = mStr;
            return str != null ? str : decodeValue();
        }

        private String decodeValue() throws ClassFormatException {
            String str;

            try {
                if (mTag == 0) {
                    str = new UTFDecoder().decode(mBuffer, mOffset, mLength);
                } else {
                    int offset = mOffset;
                    if (offset <= 0) {
                        str = new UTFDecoder().decode(mBuffer, mOffset, mLength);
                    } else {
                        final BufferDecoder decoder = mDecoder;
                        final int originalOffset = decoder.offset();
                        try {
                            decoder.offset(offset - 2);
                            str = decoder.readUTF();
                        } finally {
                            decoder.offset(originalOffset);
                        }
                    }
                }
            } catch (Exception e) {
                throw ClassFormatException.from(e);
            }

            return mStr = str;
        }

        @Override // CharSequence
        public char charAt(int index) {
            if (mState == 0) {
                prepare();
            }
            return mState == 1 ? (char) (mBuffer[mOffset + index] & 0xff) : str().charAt(index);
        }

        @Override // CharSequence
        public int length() {
            if (mState == 0) {
                prepare();
            }
            return mState == 1 ? mLength : str().length();
        }

        @Override // CharSequence
        public CharSequence subSequence(int start, int end) {
            return str().subSequence(start, end);
        }

        @Override // CharSequence
        public String toString() {
            return str();
        }

        @Override
        public int hashCode() {
            if (mState == 0) {
                prepare();
            }
            return mHash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof C_UTF8 other
                &&  Arrays.equals(mBuffer, mOffset, mOffset + mLength,
                                  other.mBuffer, other.mOffset, other.mOffset + other.mLength)
                || obj instanceof CharSequence seq && contentEquals(seq);
        }

        private boolean contentEquals(CharSequence seq) {
            hasStr: {
                String str = mStr;
                if (str == null) {
                    prepare();
                    if ((str = mStr) == null) {
                        break hasStr;
                    }
                }
                return str.contentEquals(seq);
            }

            assert mState == 1; // ASCII string

            int length = mLength;

            if (seq.length() != length) {
                return false;
            }

            byte[] buffer = mBuffer;
            int offset = mOffset;

            for (int i=0; i<length; i++) {
                if (seq.charAt(i) != ((char) buffer[offset + i])) {
                    return false;
                }
            }

            return true;
        }

        private void prepare() {
            byte[] buffer = mBuffer;
            int i = mOffset;
            int end = i + mLength;

            if (mStr != null) {
                mHash = mStr.hashCode();
                for (; i < end; i++) {
                    int c = buffer[i];
                    if (c < 0) {
                        mState = 2; // non-ASCII string
                        return;
                    }
                }
                mState = 1; // ASCII string
            } else {
                int hash = 0;
                for (; i < end; i++) {
                    int c = buffer[i];
                    if (c < 0) {
                        mHash = str().hashCode();
                        mState = 2; // non-ASCII string
                        return;
                    }
                    hash = hash * 31 + c;
                }
                mHash = hash;
                mState = 1; // ASCII string
            }
        }

        /**
         * Assume that the string refers to a method type descriptor, and generate operations
         * to push all of the arguments to the operand stack.
         *
         * @return number of stack slots pushed
         */
        int pushArgs(BufferEncoder encoder) throws IOException {
            int numPushed = 0;

            byte[] buffer = mBuffer;
            int offset = mOffset + 1; // skip the '('
            int endOffset = offset + mLength;

            loop: while (offset < endOffset) {
                int c = buffer[offset++] & 0xff;

                switch (c) {
                    default -> {
                        break loop;
                    }
                    case 'B', 'C', 'I', 'S', 'Z' -> {
                        encoder.writeByte(ILOAD);
                        encoder.writeByte(numPushed++);
                    }
                    case 'J' -> {
                        encoder.writeByte(LLOAD);
                        encoder.writeByte(numPushed); numPushed += 2;
                    }
                    case 'F' -> {
                        encoder.writeByte(FLOAD);
                        encoder.writeByte(numPushed++);
                    }
                    case 'D' -> {
                        encoder.writeByte(DLOAD);
                        encoder.writeByte(numPushed); numPushed += 2;
                    }
                    case 'L', '[' -> {
                        encoder.writeByte(ALOAD);
                        encoder.writeByte(numPushed++);
                        // Find the ';' terminator.
                        while (offset < endOffset && (buffer[offset++] & 0xff) != ';');
                    }
                }
            }

            return numPushed;
        }

        /**
         * Assume that the string refers to a method type descriptor, and generate a return
         * operation.
         *
         * @return number of stack slots popped
         */
        int returnValue(BufferEncoder encoder) throws IOException {
            int c = mBuffer[mOffset + mLength - 1] & 0xff;
            switch (c) {
                default -> {
                    encoder.writeByte(RETURN);
                    return 0;
                }
                case 'B', 'C', 'I', 'S', 'Z' -> {
                    encoder.writeByte(IRETURN);
                    return 1;
                }
                case 'J' -> {
                    encoder.writeByte(LRETURN);
                    return 2;
                }
                case 'F' -> {
                    encoder.writeByte(FRETURN);
                    return 1;
                }
                case 'D' -> {
                    encoder.writeByte(DRETURN);
                    return 2;
                }
                case ';' -> {
                    encoder.writeByte(ARETURN);
                    return 1;
                }
            }
        }

        @Override
        long size() {
            return (1 + 2) + mLength;
        }

        @Override
        void writeTo(BufferEncoder encoder) throws IOException {
            encoder.writeByte(mTag);
            encoder.writeShort(mLength);
            encoder.write(mBuffer, mOffset, mLength);
        }
    }

    static class C_String extends Constant {
        final C_UTF8 mValue;

        C_String(int tag, C_UTF8 value) {
            super(tag);
            mValue = Objects.requireNonNull(value);
        }

        @Override
        public int hashCode() {
            return mValue.hashCode() * 31 + mTag;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof C_String other
                && mTag == other.mTag && mValue.equals(other.mValue);
        }

        @Override
        long size() {
            return 1 + 2;
        }

        @Override
        void writeTo(BufferEncoder encoder) throws IOException {
            encoder.writeByte(mTag);
            encoder.writeShort(mValue.mIndex);
        }
    }

    static final class C_Long extends Constant {
        final long mValue;

        C_Long(int tag, long value) {
            super(tag);
            mValue = value;
        }

        @Override
        public boolean isWide() {
            return true;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(mValue) * 31 + mTag;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof C_Long other
                && mTag == other.mTag && mValue == other.mValue;
        }

        @Override
        long size() {
            return 1 + 8;
        }

        @Override
        void writeTo(BufferEncoder encoder) throws IOException {
            encoder.writeByte(mTag);
            encoder.writeLong(mValue);
        }
    }

    static final class C_Double extends Constant {
        final double mValue;

        C_Double(int tag, double value) {
            super(tag);
            mValue = value;
        }

        @Override
        public boolean isWide() {
            return true;
        }

        @Override
        public int hashCode() {
            return Double.hashCode(mValue) * 31 + mTag;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof C_Double other
                && mTag == other.mTag && mValue == other.mValue;
        }

        @Override
        long size() {
            return 1 + 8;
        }

        @Override
        void writeTo(BufferEncoder encoder) throws IOException {
            encoder.writeByte(mTag);
            encoder.writeLong(Double.doubleToRawLongBits(mValue));
        }
    }

    static final class C_Class extends C_String {
        private String mName;

        C_Class(int tag, C_UTF8 name) {
            super(tag, name);
        }

        /**
         * Returns the name using '.' characters instead of '/' characters.
         */
        String name() {
            String name = mName;
            if (name == null) {
                mName = name = mValue.str().replace('/', '.');
            }
            return name;
        }

        /**
         * Split the class name into package name and plain class name, by updating the buffer
         * reference in the given C_UTF8 instance. They should have been initially created as
         * empty.
         */
        void split(C_UTF8 packageName, C_UTF8 className) {
            packageName.mState = 0;
            packageName.mStr = null;
            className.mState = 0;
            className.mStr = null;

            final C_UTF8 full = mValue;

            final byte[] buffer = full.mBuffer;
            packageName.mBuffer = buffer;
            className.mBuffer = buffer;

            final int offset = full.mOffset;
            packageName.mOffset = offset;

            final int length = full.mLength;

            int i = length;
            while (--i >= 0 && buffer[offset + i] != '/');

            packageName.mLength = i;
            className.mOffset = offset + i + 1;
            className.mLength = length - i - 1;
        }
    }

    static final class C_NameAndType extends Constant {
        final C_UTF8 mName;
        final C_UTF8 mTypeDesc;

        C_NameAndType(int tag, C_UTF8 name, C_UTF8 typeDesc) {
            super(tag);
            mName = Objects.requireNonNull(name);
            mTypeDesc = Objects.requireNonNull(typeDesc);
        }

        @Override
        public int hashCode() {
            return mName.hashCode() * 31 + mTypeDesc.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof C_NameAndType other
                && mName.equals(other.mName) && mTypeDesc.equals(other.mTypeDesc);
        }

        @Override
        long size() {
            return 1 + 4;
        }

        @Override
        void writeTo(BufferEncoder encoder) throws IOException {
            encoder.writeByte(mTag);
            encoder.writeShort(mName.mIndex);
            encoder.writeShort(mTypeDesc.mIndex);
        }
    }

    // Supports CONSTANT_Fieldref, CONSTANT_Methodref, and CONSTANT_InterfaceMethodref.
    static final class C_MemberRef extends Constant {
        final C_Class mClass;
        final C_NameAndType mNameAndType;

        C_MemberRef(int tag, C_Class clazz, C_NameAndType nat) {
            super(tag);
            mClass = Objects.requireNonNull(clazz);
            mNameAndType = Objects.requireNonNull(nat);
        }

        @Override
        public int hashCode() {
            return (mClass.hashCode() + mNameAndType.hashCode()) * 31 + mTag;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof C_MemberRef other
                && mTag == other.mTag && mClass.equals(other.mClass)
                && mNameAndType.equals(other.mNameAndType);
        }

        @Override
        long size() {
            return 1 + 4;
        }

        @Override
        void writeTo(BufferEncoder encoder) throws IOException {
            encoder.writeByte(mTag);
            encoder.writeShort(mClass.mIndex);
            encoder.writeShort(mNameAndType.mIndex);
        }
    }
}
