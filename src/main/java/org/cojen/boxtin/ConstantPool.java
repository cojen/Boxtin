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

import java.lang.instrument.IllegalClassFormatException;

import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ThreadLocalRandom;

import static java.lang.invoke.MethodHandleInfo.*;

/**
 * Supports cheap decoding of a class file's constant pool.
 *
 * @author Brian S. O'Neill
 */
final class ConstantPool {
    static ConstantPool decode(BasicDecoder decoder)
        throws IOException, IllegalClassFormatException
    {
        var offsets = new int[decoder.readUnsignedShort() - 1];

        int[] methodHandleOffsets = null;
        int numMethodHandles = 0;

        for (int i=0; i<offsets.length; i++) {
            offsets[i] = decoder.offset();

            decoder.skipNBytes(switch (decoder.readUnsignedByte()) {
                default -> throw new IllegalClassFormatException();

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

        return new ConstantPool(decoder.buffer(), decoder.offset(), offsets, methodHandleOffsets);
    }

    private byte[] mBuffer;
    private final int mEndOffset;
    private final int[] mOffsets, mMethodHandleOffsets;

    private Constant[] mIndexedConstants;
    private Map<Constant, Constant> mMappedConstants;
    private List<Constant> mAddedConstants;

    private ConstantPool(byte[] buffer, int endOffset, int[] offsets, int[] methodHandleOffsets) {
        mBuffer = buffer;
        mEndOffset = endOffset;
        mOffsets = offsets;
        mMethodHandleOffsets = methodHandleOffsets;
    }

    int size() {
        int size = mOffsets.length;
        if (mAddedConstants != null) {
            size += mAddedConstants.size();
        }
        return size;
    }

    byte[] buffer() {
        return mBuffer;
    }

    /**
     * Returns the byte offset for the given constant index, which refers to the tag byte.
     * Returns a negative offset or throws an IndexOutOfBoundsException if the given index is
     * invalid.
     */
    int offsetOf(int constantIndex) {
        return mOffsets[constantIndex - 1];
    }

    /**
     * Returns the byte offset for the given constant index, immediately past the tag.
     */
    int offsetOf(int constantIndex, int expectTag) throws IllegalClassFormatException {
        int offset = offsetOf(constantIndex);
        expectTag(offset, expectTag);
        return offset + 1;
    }

    /**
     * @param ref is updated by this method
     */
    void decodeFieldRef(int constantIndex, MemberRef ref) throws IllegalClassFormatException {
        int offset = offsetOf(constantIndex, 9); // CONSTANT_Fieldref
        decodeClassRef(ref, offset); offset += 2;
        decodeNameAndTypeRef(ref, offset);
    }

    /**
     * @param ref is updated by this method
     */
    void decodeMethodRef(int constantIndex, MemberRef ref) throws IllegalClassFormatException {
        int offset = offsetOf(constantIndex);
        int tag = decodeTag(offset);
        if (tag != 10 && tag != 11) { // CONSTANT_Methodref, CONSTANT_InterfaceMethodref
            throw new IllegalClassFormatException();
        }
        offset++;
        decodeClassRef(ref, offset); offset += 2;
        decodeNameAndTypeRef(ref, offset);
    }

    @FunctionalInterface
    static interface MethodHandleConsumer {
        /**
         * @param kind the kind of MethodHandle constant
         * @param offset buffer offset which stores a constant field or method index
         * @param ref refers to the current MethodHandle info
         * @see MethodHandleInfo
         */
        void accept(int kind, int offset, MemberRef ref) throws IllegalClassFormatException;
    }

    /**
     * @param ref is updated for each MethodHandle
     * @param consumer is called for each MethodHandle
     * @return the number of MethodHandles which were decoded
     */
    int decodeMethodHandleRefs(MemberRef ref, MethodHandleConsumer consumer)
        throws IllegalClassFormatException
    {
        int num = 0;

        if (mMethodHandleOffsets != null) for (int i=0; i<mMethodHandleOffsets.length; i++) {
            int offset = mMethodHandleOffsets[i];

            if (offset == 0) {
                break;
            }

            num++;

            int kind = mBuffer[offset++] & 0xff;
            int constantIndex = decodeUnsignedShortBE(offset);

            switch (kind) {
                default -> throw new IllegalClassFormatException();

                case REF_getField, REF_getStatic, REF_putField, REF_putStatic -> {
                    decodeFieldRef(constantIndex, ref);
                }

                case REF_invokeVirtual, REF_invokeStatic, REF_invokeSpecial,
                    REF_newInvokeSpecial, REF_invokeInterface ->
                {
                    decodeMethodRef(constantIndex, ref);
                }
            }

            consumer.accept(kind, offset, ref);
        }

        return num;
    }

    private void decodeClassRef(MemberRef ref, int offset) throws IllegalClassFormatException {
        offset = offsetOf(decodeUnsignedShortBE(offset), 7); // CONSTANT_Class
        offset = offsetOf(decodeUnsignedShortBE(offset), 1); // CONSTANT_Utf8
        ref.classOffset(offset + 2);
        ref.classLength(decodeUnsignedShortBE(offset));
    }

    private void decodeNameAndTypeRef(MemberRef ref, int offset)
        throws IllegalClassFormatException
    {
        offset = offsetOf(decodeUnsignedShortBE(offset), 12); // CONSTANT_NameAndType
        int nameOffset = offsetOf(decodeUnsignedShortBE(offset), 1); // CONSTANT_Utf8
        ref.nameOffset(nameOffset + 2);
        ref.nameLength(decodeUnsignedShortBE(nameOffset));
        int descOffset = offsetOf(decodeUnsignedShortBE(offset + 2), 1); // CONSTANT_Utf8
        ref.descriptorOffset(descOffset + 2);
        ref.descriptorLength(decodeUnsignedShortBE(descOffset));
    }

    private int decodeTag(int offset) throws IllegalClassFormatException {
        try {
            return mBuffer[offset];
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalClassFormatException();
        }
    }

    private void expectTag(int offset, int expect) throws IllegalClassFormatException {
        int tag = decodeTag(offset);
        if (tag != expect) {
            throw new IllegalClassFormatException();
        }
    }

    private int decodeUnsignedShortBE(int offset) {
        return Utils.decodeUnsignedShortBE(mBuffer, offset);
    }

    /**
     * Must be called before adding new constants.
     *
     * @return a mutable class file buffer
     */
    byte[] extend() throws IllegalClassFormatException {
        try {
            return doExtend();
        } catch (Exception e) {
            throw new IllegalClassFormatException(e.toString());
        }
    }

    boolean hasBeenExtended() {
        return mIndexedConstants != null;
    }

    private byte[] doExtend() throws IOException {
        if (hasBeenExtended()) {
            return mBuffer;
        }

        byte[] buffer = mBuffer.clone();
        mBuffer = buffer;

        // Resolve the existing constants, to reduce duplication. Only bother doing this for
        // the types of constants which can be added.

        mIndexedConstants = new Constant[mOffsets.length];
        mMappedConstants = new LinkedHashMap<>(mOffsets.length << 1);
        mAddedConstants = new ArrayList<>();

        var decoder = new BasicDecoder(buffer);

        for (int i=0; i<mOffsets.length; i++) {
            findConstant(i + 1, decoder);
        }

        return buffer;
    }

    /**
     * @param decoder offset can be modified as a side-effect
     * @return null if the constant type is unsupported
     */
    private Constant findConstant(int constantIndex, BasicDecoder decoder) throws IOException {
        Constant c = mIndexedConstants[constantIndex - 1];
        return c != null ? c : resolveConstant(constantIndex, decoder);
    }

    /**
     * @param decoder offset is modified as a side-effect
     * @return null if the constant type is unsupported
     */
    private Constant resolveConstant(int constantIndex, BasicDecoder decoder) throws IOException {
        {
            int offset = offsetOf(constantIndex);
            if (offset < 0) {
                // Requesting the second slot of a long or double constant.
                return null;
            }
            decoder.offset(offset);
        }

        int tag = decoder.readUnsignedByte();

        Constant c;

        switch (tag) {
            default -> {
                return null;
            }

            // CONSTANT_Utf8
            case 1 -> {
                int length = decoder.readUnsignedShort();
                int offset = decoder.offset();
                c = new C_UTF8(mBuffer, offset, length);
            }

            // CONSTANT_Class
            case 7 -> {
                int name_index = decoder.readUnsignedShort();
                c = new C_Class((C_UTF8) findConstant(name_index, decoder));
            }

            // CONSTANT_Fieldref, CONSTANT_Methodref, CONSTANT_InterfaceMethodref
            case 9, 10, 11 -> {
                int class_index = decoder.readUnsignedShort();
                int name_and_type_index = decoder.readUnsignedShort();
                c = new C_MemberRef(tag, (C_Class) findConstant(class_index, decoder),
                                    (C_NameAndType) findConstant(name_and_type_index, decoder));
            }

            // CONSTANT_NameAndType
            case 12 -> {
                int name_index = decoder.readUnsignedShort();
                int descriptor_index = decoder.readUnsignedShort();
                c = new C_NameAndType((C_UTF8) findConstant(name_index, decoder),
                                      (C_UTF8) findConstant(descriptor_index, decoder));
            }
        }

        c.mIndex = constantIndex;

        mIndexedConstants[constantIndex - 1] = c;
        mMappedConstants.put(c, c);

        return c;
    }

    /**
     * Adds a constant name and descriptor for a method, with a generated name. The extend
     * method must have already been called.
     *
     * @param prefix name prefix
     * @param classIndex valid index into the constant pool which refers to a CONSTANT_Class.
     * @param descriptor UTF-8 encoded method descriptor
     * @param nameRef optional; is set to the generated name
     */
    C_MemberRef addUniqueMethod(byte prefix, int classIndex, byte[] descriptor, String[] nameRef) {
        var classConstant = (C_Class) mIndexedConstants[classIndex - 1];
 
        var name = new byte[1 + 9]; // one prefix byte plus up to nine digits
        name[0] = prefix;
        int nameLength = 2; // start with one random digit

        var rnd = ThreadLocalRandom.current();
        C_UTF8 nameConstant;

        while (true) {
            for (int i=1; i<nameLength; i++) {
                name[i] = (byte) ('0' + rnd.nextInt(10));
            }
            nameConstant = new C_UTF8(name, 0, nameLength);
            if (mMappedConstants.putIfAbsent(nameConstant, nameConstant) == null) {
                registerNewConstant(nameConstant);
                break;
            }
            if (nameLength < name.length) {
                nameLength++; // add another random digit
            }
        }

        if (nameRef != null) {
            nameRef[0] = new String(name, 0, nameLength, StandardCharsets.UTF_8);
        }

        C_UTF8 descConstant = addConstant(new C_UTF8(descriptor));

        C_NameAndType natConstant = addConstant(new C_NameAndType(nameConstant, descConstant));

        // CONSTANT_Methodref tag is 10.
        return addConstant(new C_MemberRef(10, classConstant, natConstant));
    }

    /**
     * Returns the index to the CONSTANT_Utf8 representation of "Code", adding it if necessary.
     * The extend method must have already been called.
     */
    int codeStrIndex() {
        return addConstant(new C_UTF8(new byte[] {'C', 'o', 'd', 'e'})).mIndex;
    }

    /**
     * Returns the index to the CONSTANT_String representation of the given string, adding it
     * if necessary. The extend method must have already been called.
     */
    int strIndex(String str) {
        C_UTF8 utf = addConstant(new C_UTF8(UTFEncoder.encode(str)));
        // CONSTANT_String tag is 8.
        return addConstant(new C_String(8, utf)).mIndex;
    }

    /**
     * Returns the index to the CONSTANT_Class representation of the given class, adding it if
     * necessary. The extend method must have already been called.
     */
    C_Class addClass(Class clazz) {
        return addClass(clazz.getName());
    }

    C_Class addClass(String name) {
        return addConstant(new C_Class(addConstant(new C_UTF8(name.replace('.', '/')))));
    }

    /**
     * Returns the index to the CONSTANT_Methodref representation of the constructor init
     * method, adding it if necessary. The extend method must have already been called.
     *
     * @param desc can pass null if no args
     */
    C_MemberRef ctorInitStr(C_Class ex, String desc) {
        C_UTF8 nameConstant = addConstant(new C_UTF8(new byte[] {'<', 'i', 'n', 'i', 't', '>'}));

        byte[] descBytes;
        if (desc == null) {
            descBytes = new byte[] {'(', ')', 'V'};
        } else {
            descBytes = UTFEncoder.encode(desc);
        }

        C_UTF8 descConstant = addConstant(new C_UTF8(descBytes));

        C_NameAndType natConstant = addConstant(new C_NameAndType(nameConstant, descConstant));
        // CONSTANT_Methodref tag is 10.
        return addConstant(new C_MemberRef(10, ex, natConstant));
    }

    void writeTo(BasicEncoder encoder) throws IOException, IllegalClassFormatException {
        int count = size() + 1;
        if (count > 65535) {
            throw new IllegalClassFormatException("Constant pool is full");
        }
        encoder.writeShort(count);
        int startOffset = mOffsets[0];
        encoder.write(mBuffer, startOffset, mEndOffset - startOffset);
        for (Constant c : mAddedConstants) {
            c.writeTo(encoder);
        }
    }

    /**
     * @param constant must not be long or double
     */
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

    /**
     * @param constant must not be long or double
     */
    private void registerNewConstant(Constant constant) {
        mAddedConstants.add(constant);
        constant.mIndex = mIndexedConstants.length + mAddedConstants.size();
    }

    static abstract class Constant {
        final int mTag;
        int mIndex;

        Constant(int tag) {
            mTag = tag;
        }

        void writeTo(BasicEncoder encoder) throws IOException {
            encoder.writeByte(mTag);
        }
    }

    static final class C_UTF8 extends Constant {
        final byte[] mValue;
        final int mOffset, mLength;
        final int mHash;

        C_UTF8(byte[] value, int offset, int length) {
            super(1);
            mValue = Objects.requireNonNull(value);
            mOffset = offset;
            mLength = length;
            mHash = Utils.hash(value, offset, length);
        }

        C_UTF8(byte[] value) {
            this(value, 0, value.length);
        }

        C_UTF8(String value) {
            this(UTFEncoder.encode(value));
        }

        @Override
        public int hashCode() {
            return mHash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof C_UTF8 other
                && Arrays.equals(mValue, mOffset, mOffset + mLength,
                                 other.mValue, other.mOffset, other.mOffset + other.mLength);
        }

        @Override
        void writeTo(BasicEncoder encoder) throws IOException {
            super.writeTo(encoder);
            encoder.writeShort(mLength);
            encoder.write(mValue, mOffset, mLength);
        }
    }

    static class C_String extends Constant {
        C_UTF8 mValue;

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
        void writeTo(BasicEncoder encoder) throws IOException {
            super.writeTo(encoder);
            encoder.writeShort(mValue.mIndex);
        }
    }

    static final class C_Class extends C_String {
        C_Class(C_UTF8 name) {
            super(7, name);
        }
    }

    static final class C_NameAndType extends Constant {
        final C_UTF8 mName;
        final C_UTF8 mTypeDesc;

        C_NameAndType(C_UTF8 name, C_UTF8 typeDesc) {
            super(12);
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
        void writeTo(BasicEncoder encoder) throws IOException {
            super.writeTo(encoder);
            encoder.writeShort(mName.mIndex);
            encoder.writeShort(mTypeDesc.mIndex);
        }
    }

    // Supports CONSTANT_Fieldref, CONSTANT_Methodref, and CONSTANT_InterfaceMethodref.
    static final class C_MemberRef extends Constant {
        final C_Class mClass;
        final C_NameAndType mNameAndType;

        C_MemberRef(int tag, C_Class clazz, C_NameAndType nameAndType) {
            super(tag);
            mClass = Objects.requireNonNull(clazz);
            mNameAndType = Objects.requireNonNull(nameAndType);
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

        /**
         * Should only be called for CONSTANT_Methodref or CONSTANT_InterfaceMethodref.
         */
        int argCount() {
            C_UTF8 td = mNameAndType.mTypeDesc;
            byte[] value = td.mValue;
            int offset = td.mOffset;
            int endOffset = offset + td.mLength;
            offset++; // skip the '('

            int count = 0;

            loop: while (offset < endOffset) {
                switch (value[offset++] & 0xff) {
                    case ')' -> {
                        break loop;
                    }

                    case 'Z', 'B', 'S', 'C', 'I', 'F' -> count++;
                
                    case 'J', 'D' -> count += 2;

                    case 'L' -> {
                        count++;
                        while (offset < endOffset && value[offset++] != ';');
                    }

                    case '[' -> {
                        count++;
                        loop2: while (offset < endOffset) {
                            switch (value[offset++] & 0xff) {
                                case ')', 'Z', 'B', 'S', 'C', 'I', 'F', 'J', 'D', ';' -> {
                                    break loop2;
                                }
                            }
                        }
                    }
                }
            }

            return count;
        }

        @Override
        void writeTo(BasicEncoder encoder) throws IOException {
            super.writeTo(encoder);
            encoder.writeShort(mClass.mIndex);
            encoder.writeShort(mNameAndType.mIndex);
        }
    }
}
