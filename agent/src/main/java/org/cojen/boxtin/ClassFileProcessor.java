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

import java.lang.reflect.Modifier;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static java.lang.invoke.MethodHandleInfo.*;

import static org.cojen.boxtin.ConstantPool.*;
import static org.cojen.boxtin.Opcodes.*;
import static org.cojen.boxtin.Utils.*;

/**
 * Implements minimal class file decoding for supporting a security checking agent.
 *
 * @author Brian S. O'Neill
 */
final class ClassFileProcessor {
    /**
     * Decodes a class file up to the where the methods are defined.
     */
    public static ClassFileProcessor begin(byte[] buffer) throws ClassFormatException {
        try {
            return begin(new BufferDecoder(buffer));
        } catch (Exception e) {
            throw ClassFormatException.from(e);
        }
    }

    private static ClassFileProcessor begin(BufferDecoder decoder)
        throws IOException, ClassFormatException
    {
        if (decoder.readInt() != 0xCAFEBABE) {
            throw new ClassFormatException();
        }

        int minor = decoder.readUnsignedShort();
        int major = decoder.readUnsignedShort();

        if (major < 49) { // require Java 5+
            throw new ClassFormatException("" + major);
        }

        var cp = ConstantPool.decode(decoder);

        int accessFlags = decoder.readUnsignedShort();
        int thisClassIndex = decoder.readUnsignedShort();
        int superClassIndex = decoder.readUnsignedShort();

        // Skip interfaces.
        decoder.skipNBytes(decoder.readUnsignedShort() * 2);

        // Skip fields.
        for (int i = decoder.readUnsignedShort(); --i >= 0;) {
            // Skip access_flags, name_index, and descriptor_index.
            decoder.skipNBytes(2 + 2 + 2);
            skipAttributes(decoder);
        }

        return new ClassFileProcessor(cp, accessFlags, thisClassIndex, decoder);
    }

    private final ConstantPool mConstantPool;
    private final int mAccessFlags;
    private final int mThisClassIndex;
    private final BufferDecoder mDecoder;
    private final int mMethodsStartOffset;
    private final int mMethodsCount;

    private static final class Replacement extends BufferEncoder {
        final long mOriginalLength;

        Replacement(int capacity, long originalLength) {
            super(capacity);
            mOriginalLength = originalLength;
        }

        long growth() {
            return length() - mOriginalLength;
        }
    }

    private Map<Integer, Replacement> mReplacements;

    private Map<Integer, C_MemberRef> mNewMethods;

    private Replacement mNewMethodsBuffer;

    private Map<Integer, C_MemberRef> mReplacedMethodHandles;

    // Work objects used by the local forClass method.
    private ConstantPool.C_UTF8 mPackageName, mClassName;

    private ClassFileProcessor(ConstantPool cp, int accessFlags,
                               int thisClassIndex, BufferDecoder decoder)
        throws IOException
    {
        mConstantPool = cp;
        mAccessFlags = accessFlags;
        mThisClassIndex = thisClassIndex;
        mDecoder = decoder;
        mMethodsStartOffset = decoder.offset();
        mMethodsCount = decoder.readUnsignedShort();
    }

    /**
     * Examines the class to see if any constructors or methods need to be modified for
     * supporting runtime checks, and begins making modifications if so.
     *
     * @param forCaller checker against the class when it's acting as a caller
     * @param forTargetClass checker against the class when it's acting as a target
     * @return true if class requires modification
     */
    public boolean check(Checker forCaller, Checker.ForClass forTargetClass)
        throws IOException, ClassFormatException
    {
        final boolean targetClassChecked = forTargetClass.isTargetChecked()
            || forTargetClass.isAnyConstructorDeniable(); // See isConstructor comment below.

        if (forCaller == Rule.ALLOW && (!isAccessible(mAccessFlags) || !targetClassChecked)) {
            // No need to modify inaccessible classes, or those that aren't checked.
            return false;
        }

        // Check the MethodHandle constants.

        mConstantPool.visitMethodHandleRefs(true, (kind, offset, methodRef) -> {
            Checker.ForClass forClass = forClass(forCaller, methodRef);

            if (!forClass.isCallerChecked() && !forClass.isTargetChecked()) {
                return;
            }

            C_NameAndType nat = methodRef.mNameAndType;

            byte op;
            byte proxyType;

            if (kind == REF_newInvokeSpecial) {
                if (!forClass.isConstructorAllowed(nat.mTypeDesc)) {
                    op = NEW;
                    // Constructor check is always in the target.
                    proxyType = 0;
                } else {
                    return;
                }
            } else {
                if (forClass.isTargetMethodChecked(nat.mName, nat.mTypeDesc)) {
                    proxyType = 0;
                } else if (forClass.isCallerMethodChecked(nat.mName, nat.mTypeDesc)) {
                    proxyType = 1;
                } else {
                    return;
                }
                op = switch (kind) {
                    default -> throw new ClassFormatException();
                    case REF_invokeVirtual   -> INVOKEVIRTUAL;
                    case REF_invokeStatic    -> INVOKESTATIC;
                    case REF_invokeSpecial   -> INVOKESPECIAL;
                    case REF_invokeInterface -> INVOKEINTERFACE;
                };
            }

            C_MemberRef proxyMethod = addProxyMethod(op, proxyType, methodRef);

            if (mReplacedMethodHandles == null) {
                mReplacedMethodHandles = new HashMap<>();
            }

            mReplacedMethodHandles.put(offset, proxyMethod);
        });

        // Check the methods.

        final byte[] cpBuffer = mConstantPool.buffer();
        final BufferDecoder decoder = mDecoder;

        for (int i = mMethodsCount; --i >= 0; ) {
            int startOffset = decoder.offset();
            int access_flags = decoder.readUnsignedShort();
            int name_index = decoder.readUnsignedShort();
            int desc_index = decoder.readUnsignedShort();

            boolean targetCodeChecked;
            ConstantPool.C_UTF8 name, desc; // both can be null when !targetCodeChecked

            if (!targetClassChecked || !isAccessible(access_flags)) {
                targetCodeChecked = false;
                name = null;
                desc = null;
            } else {
                name = mConstantPool.findConstantUTF8(name_index);

                if (name.equals("<clinit>")) {
                    targetCodeChecked = false;
                    desc = null;
                } else {
                    desc = mConstantPool.findConstantUTF8(desc_index);

                    if (name.isConstructor()) {
                        // Constructor check must only be in the target class. The code
                        // modifications to make it work in the client class are too complicated.
                        // The problem is that uninitialized objects cannot be passed to other
                        // methods, in this case, the proxy method. See insertCallerChecks.
                        targetCodeChecked = !forTargetClass.isConstructorAllowed(desc);
                        name = null; // indicate that the method is a constructor
                    } else {
                        targetCodeChecked = forTargetClass.isTargetMethodChecked(name, desc);
                    }
                }
            }

            if (!targetCodeChecked && forCaller == Rule.ALLOW) {
                skipAttributes(decoder);
                continue;
            }

            if (targetCodeChecked && Modifier.isNative(access_flags)) {
                // First, rename the native method to start with NATIVE_PREFIX, and make it
                // private and synthetic.

                mConstantPool.extend();

                String newNameStr = SecurityAgent.NATIVE_PREFIX + name.str();
                ConstantPool.C_UTF8 newName = mConstantPool.addUTF8(newNameStr);

                var replacement = new Replacement(4, 4);
                int newFlags = access_flags & ~(Modifier.PUBLIC | Modifier.PROTECTED);
                newFlags |= Modifier.PRIVATE | 0x1000; // | synthetic
                replacement.writeShort(newFlags);
                replacement.writeShort(newName.mIndex);

                storeReplacement(startOffset, replacement);

                // Define a proxy method which matches the original, performs a check, and then
                // calls the renamed native method.

                byte op = Modifier.isStatic(access_flags) ? INVOKESTATIC : INVOKEVIRTUAL;

                C_Class thisClass = mConstantPool.findConstant(mThisClassIndex, C_Class.class);
                C_MemberRef methodRef = mConstantPool.addMethodRef(thisClass, newName, desc);

                addProxyMethod(op, (byte) 2, methodRef,
                               access_flags & ~Modifier.NATIVE, name, desc);
            }

            // Look for the Code attribute, and then modify it.
            for (int j = decoder.readUnsignedShort(); --j >= 0; ) {
                int attrNameIndex = decoder.readUnsignedShort();
                int originalOffset = decoder.offset(); // offset of the attribute_length field
                long attrLength = decoder.readUnsignedInt();
                int cpOffset = mConstantPool.findConstantOffset(attrNameIndex);
                int tag = cpBuffer[cpOffset++];

                examine: {
                    // CONSTANT_Utf8, 4 bytes
                    if (tag == 1 && decodeUnsignedShortBE(cpBuffer, cpOffset) == 4) {
                        int attrName = decodeIntBE(cpBuffer, cpOffset + 2);
                        if (attrName == 0x436f6465) { // ASCII value for "Code"
                            break examine;
                        }
                    }
                    // Skip the attribute.
                    decoder.skipNBytes(attrLength);
                    continue;
                }

                Replacement replacement;

                if (targetCodeChecked) {
                    replacement = insertChecks(forCaller, decoder, attrLength, name, desc);
                } else {
                    assert forCaller != Rule.ALLOW;

                    int max_stack = decoder.readUnsignedShort();
                    int max_locals = decoder.readUnsignedShort();
                    long code_length = decoder.readUnsignedInt();

                    replacement = insertCallerChecks(forCaller, decoder, null, code_length);

                    // Skip exception_table.
                    decoder.skipNBytes(decoder.readUnsignedShort() * (2 + 2 + 2 + 2));

                    // Skip the attributes of the Code attribute.
                    skipAttributes(decoder);
                }

                if ((originalOffset + 4 + attrLength) != decoder.offset()) {
                    throw new ClassFormatException();
                }

                if (replacement != null) {
                    storeReplacement(originalOffset, replacement);
                }
            }
        }

        int methodsEndOffset = decoder.offset();

        if (mNewMethodsBuffer != null) {
            // Append the new methods by "replacing" what is logically an empty method just
            // after the method table.
            storeReplacement(methodsEndOffset, mNewMethodsBuffer);
        }

        return mReplacements != null || mNewMethodsBuffer != null;
    }

    private void storeReplacement(int originalOffset, Replacement replacement) {
        if (replacement != null) {
            if (mReplacements == null) {
                mReplacements = new TreeMap<>();
            }
            mReplacements.put(originalOffset, replacement);
        }
    }
        
    /**
     * Finishes modifications and returns a new class file buffer.
     *
     * @return null if no modifications were made
     * @throws ClassFormatException if the redefined class has too many constants or methods
     */
    public byte[] redefine() throws IOException, ClassFormatException {
        if (!mConstantPool.hasBeenExtended() && mReplacements == null) {
            return null;
        }

        BufferDecoder decoder = mDecoder;
        long cpGrowth = mConstantPool.growth();
        long capacity = decoder.buffer().length + cpGrowth;

        if (mReplacements != null) {
            for (Replacement r : mReplacements.values()) {
                capacity += r.growth();
            }
        }

        if (capacity > Integer.MAX_VALUE) {
            throw new ClassFormatException("Too large");
        }

        var encoder = new BufferEncoder((int) capacity);

        decoder.offset(0);

        // Copy magic, minor_version, major_version
        encoder.writeLong(decoder.readLong());

        decoder.skipNBytes(mConstantPool.originalSize());
        mConstantPool.writeTo(encoder);

        if (mReplacements != null) {
            for (Map.Entry<Integer, Replacement> e : mReplacements.entrySet()) {
                int repOffset = e.getKey();
                Replacement rep = e.getValue();

                int offset = decoder.offset();
                if (offset < repOffset) {
                    decoder.transferTo(encoder, repOffset - offset);
                } else if (offset > repOffset) {
                    throw new AssertionError();
                }

                int repLength = rep.length();
                decoder.skipNBytes(rep.mOriginalLength);
                encoder.write(rep.buffer(), 0, repLength);
            }
        }

        decoder.transferTo(encoder);

        if (encoder.length() != capacity) {
            throw new ClassFormatException();
        }

        byte[] buffer = encoder.buffer();

        if (buffer.length != capacity) {
            throw new AssertionError();
        }

        if (mNewMethods != null) {
            // Update the methods_count field.
            // FIXME: Throw ClassFormatException if too big.
            int numNewMethods = mNewMethods.size();
            int methodsStartOffset = mMethodsStartOffset + (int) cpGrowth;
            encodeShortBE(buffer, methodsStartOffset, mMethodsCount + numNewMethods);
        }

        if (mReplacedMethodHandles != null) {
            // Update the MethodHandle constants, changing reference_kind and reference_index.
            for (Map.Entry<Integer, C_MemberRef> e : mReplacedMethodHandles.entrySet()) {
                int offset = e.getKey();
                buffer[offset - 1] = REF_invokeStatic;
                encodeShortBE(buffer, offset, e.getValue().mIndex);
            }
        }

        return buffer;
    }

    /**
     * Inserts a target-side check at the start of the method:
     *
     * SecurityAgent.check(SecurityAgent.WALKER.getCallerClass(), thisClass, name, desc);
     *
     * If necessary, caller-side checks are also inserted. See insertCallerChecks.
     *
     * @param forCaller is used to create caller-side checks
     * @param decoder positioned at the max_stack field of the Code attribute
     * @param name pass null if the method is a constructor
     * @return non-null Replacement instance
     */
    private Replacement insertChecks(Checker forCaller, BufferDecoder decoder, long codeAttrLength,
                                     ConstantPool.C_UTF8 name, ConstantPool.C_UTF8 desc)
        throws IOException, ClassFormatException
    {
        ConstantPool cp = mConstantPool;
        cp.extend();

        final int name_index = name == null ? 0 : cp.addString(name).mIndex;
        final int desc_index = cp.addString(desc).mIndex;

        // Growth must be divisible by four, because switch statements are aligned as such.
        // Padding NOPs might need to be added.
        final int codeGrowth = name_index == 0 ? 16 : 20;

        // The copy needs room for new operations and possibly an updated StackMapTable.
        long capacity = codeAttrLength + codeGrowth + (4 + 2);
        if (capacity > Integer.MAX_VALUE) {
            throw new ClassFormatException();
        }

        // Add 4 to the original length to account for the attribute_length field.
        var encoder = new Replacement((int) capacity, codeAttrLength + 4);

        int max_stack = decoder.readUnsignedShort();
        int max_locals = decoder.readUnsignedShort();
        long code_length = decoder.readUnsignedInt();

        if ((code_length + codeGrowth) > Integer.MAX_VALUE) {
            throw new ClassFormatException();
        }

        encoder.writeInt(0); // attribute_length; to be filled in properly later
        encoder.writeShort(Math.max(4, max_stack));
        encoder.writeShort(max_locals);
        encoder.writeInt((int) (code_length + codeGrowth));

        encodeAgentCheck(encoder, name_index, desc_index);

        if (name_index != 0) {
            // Add padding to reach 20 bytes of growth.
            encoder.writeByte(NOP);
            encoder.writeByte(NOP);
        }

        if (forCaller == Rule.ALLOW) {
            // Copy the original code.
            decoder.transferTo(encoder, code_length);
        } else {
            // Update the original code.
            encoder = insertCallerChecks(forCaller, decoder, encoder, code_length);
        }

        // Write the exception table with incremented pc values.
        int exTableLength = decoder.readUnsignedShort();
        encoder.writeShort(exTableLength);
        for (int i=0; i<exTableLength; i++) {
            for (int j=0; j<3; j++) {
                encoder.writeShort(clampShort(decoder.readUnsignedShort() + codeGrowth));
            }
            encoder.writeShort(decoder.readUnsignedShort());
        }

        // Write the attributes, some of which must be updated.

        int attrCount = decoder.readUnsignedShort();
        encoder.writeShort(attrCount);

        int numStackMapTables = 0; // at most one is expected

        for (int i=0; i<attrCount; i++) {
            int attrNameIndex = decoder.readUnsignedShort();
            encoder.writeShort(attrNameIndex);

            long attrLength = decoder.readUnsignedInt();
            String attrName = cp.findConstantUTF8(attrNameIndex).str();

            switch (attrName) {
                default -> {
                    encoder.writeInt((int) attrLength);
                    decoder.transferTo(encoder, attrLength);
                }

                case "LineNumberTable" -> {
                    // Increment the pc values.
                    encoder.writeInt((int) attrLength);
                    int tableLength = decoder.readUnsignedShort();
                    encoder.writeShort(tableLength);
                    for (int j=0; j<tableLength; j++) {
                        encoder.writeShort(clampShort(decoder.readUnsignedShort() + codeGrowth));
                        encoder.writeShort(decoder.readUnsignedShort());
                    }
                }

                case "LocalVariableTable", "LocalVariableTypeTable" -> {
                    // Increment the pc values.
                    encoder.writeInt((int) attrLength);
                    int tableLength = decoder.readUnsignedShort();
                    encoder.writeShort(tableLength);
                    for (int j=0; j<tableLength; j++) {
                        encoder.writeShort(clampShort(decoder.readUnsignedShort() + codeGrowth));
                        encoder.writeLong(decoder.readLong());
                    }
                }
            
                case "StackMapTable" -> {
                    numStackMapTables++;
                    updateStackMapTableOffsets(decoder, encoder, attrLength, codeGrowth);
                }
            }
        }

        // Fill in the proper attribute_length.
        encodeIntBE(encoder.buffer(), 0, encoder.length() - 4);

        if (numStackMapTables <= 1 && encoder.length() > capacity) {
            throw new ClassFormatException();
        }

        return encoder;
    }

    private static int clampShort(int value) {
        return Math.min(value, 65535);
    }

    /**
     * SecurityAgent.check(SecurityAgent.WALKER.getCallerClass(), thisClass, name, desc);
     *
     * Requires at least 4 operand stack slots.
     */
    private void encodeAgentCheck(BufferEncoder encoder, int name_index, int desc_index)
        throws IOException
    {
        ConstantPool cp = mConstantPool;

        String agentName = "org/cojen/boxtin/SecurityAgent";
        String walkerName = StackWalker.class.getName().replace('.', '/');
        String walkerDesc = 'L' + walkerName + ';';
        String classDesc = Class.class.descriptorString();
        String stringDesc = String.class.descriptorString();
        String callerDesc = "()" + classDesc;
        String checkDesc = '(' + classDesc + classDesc + stringDesc + stringDesc + ")V";

        encoder.writeByte(GETSTATIC);
        encoder.writeShort(cp.addFieldRef(agentName, "WALKER", walkerDesc).mIndex);
        encoder.writeByte(INVOKEVIRTUAL);
        encoder.writeShort(cp.addMethodRef(walkerName, "getCallerClass", callerDesc).mIndex);
        encoder.writeByte(LDC_W);
        encoder.writeShort(mThisClassIndex);
        if (name_index != 0) {
            encoder.writeByte(LDC_W);
            encoder.writeShort(name_index);
        } else {
            encoder.writeByte(ACONST_NULL);
        }
        encoder.writeByte(LDC_W);
        encoder.writeShort(desc_index);
        encoder.writeByte(INVOKESTATIC);
        encoder.writeShort(cp.addMethodRef(agentName, "check", checkDesc).mIndex);
    }

    /**
     * Decodes a StackMapTable and re-encodes with all offsets incremented by the given delta.
     */
    private static void updateStackMapTableOffsets(BufferDecoder decoder, Replacement encoder,
                                                   long attrLength, int delta)
        throws IOException
    {
        final int startOffset = encoder.length();
        final int numEntries = decoder.readUnsignedShort();

        int consumed;

        if (numEntries == 0) {
            encoder.writeInt((int) attrLength);
            encoder.writeShort(numEntries);
            consumed = 2;
        } else {
            // Update the first entry.
            int type = decoder.readUnsignedByte();

            if (type < 64) { // same_frame
                if (type + delta < 64) {
                    encoder.writeInt((int) attrLength);
                    encoder.writeShort(numEntries);
                    encoder.writeByte(type + delta);
                } else {
                    // Convert to same_frame_extended.
                    encoder.writeInt((int) attrLength + 2);
                    encoder.writeShort(numEntries);
                    encoder.writeByte(251);
                    encoder.writeShort(type + delta);
                }
                consumed = 3;
            } else if (type < 128) { // same_locals_1_stack_item_frame
                if (type + delta < 128) {
                    encoder.writeInt((int) attrLength);
                    encoder.writeShort(numEntries);
                    encoder.writeByte(type + delta);
                } else {
                    // Convert to same_locals_1_stack_item_frame_extended.
                    encoder.writeInt((int) attrLength + 2);
                    encoder.writeShort(numEntries);
                    encoder.writeByte(247);
                    encoder.writeShort(type - 64 + delta);
                }
                consumed = 3;
            } else if (type < 247) {
                // Not legal, so just leave it alone.
                encoder.writeInt((int) attrLength);
                encoder.writeShort(numEntries);
                encoder.writeByte(type);
                consumed = 3;
            } else {
                encoder.writeInt((int) attrLength);
                encoder.writeShort(numEntries);
                encoder.writeByte(type);
                encoder.writeShort(decoder.readUnsignedShort() + delta);
                consumed = 5;
            }
        }

        decoder.transferTo(encoder, attrLength - consumed);

        // Update the offsets of all Uninitialized_variable_infos.

        byte[] buffer = encoder.buffer();
        int offset = startOffset + 2;

        for (int i=0; i<numEntries; i++) {
            int type = buffer[offset++] & 0xff;
            int numItems;

            if (type < 64) { // same_frame
                continue;
            } else if (type < 128) { // same_locals_1_stack_item_frame
                numItems = 1;
            } else if (type < 247) { // illegal
                break;
            } else if (type == 247) { // same_locals_1_stack_item_frame_extended
                offset += 2;
                numItems = 1;
            } else if (type < 252) { // chop_frame or same_frame_extended
                offset += 2;
                continue;
            } else if (type < 255) { // append_frame
                offset += 2;
                numItems = type - 251;
            } else { // full_frame
                offset += 2;
                numItems = decodeUnsignedShortBE(buffer, offset); offset += 2;
                offset = updateTypeInfos(buffer, offset, delta, numItems);
                numItems = decodeUnsignedShortBE(buffer, offset); offset += 2;
            }

            offset = updateTypeInfos(buffer, offset, delta, numItems);
        }
    }

    /**
     * @return updated offset
     */
    private static int updateTypeInfos(byte[] buffer, int offset, int delta, int numItems) {
        for (int i=0; i<numItems; i++) {
            int tag = buffer[offset++] & 0xff;
            if (tag == 7) { // ITEM_Object
                offset += 2;
            } else if (tag == 8) { // ITEM_Uninitialized
                encodeShortBE(buffer, offset, decodeUnsignedShortBE(buffer, offset) + delta);
                offset += 2;
            }
        }
        return offset;
    }

    /**
     * Inserts caller-side checks into the bytecode if necessary. Affected method invocations
     * are replaced with a call through a local proxy. A proxy method can look like this:
     *
     * private static File $3(String path) {
     *     if (thisClass.getModule() != File.class.getModule()) {
     *         throw new SecurityException();
     *     }
     *     return File.open(path);
     * }
     *
     * The reason the proxy performs a check at runtime rather than always throwing a
     * SecurityException is because calls within the same module are always allowed. The Module
     * instances aren't available until the classes are loaded, and they cannot be loaded yet.
     *
     * The module check code must be defined here rather than delegating to an external utility
     * method because a different version of the method could be loaded in when the caller
     * class is linked.
     *
     * @param forCaller is used to create caller-side checks
     * @param decoder positioned at the first bytecode operation
     * @param encoder accepts the updated bytecode; can be null initially
     * @return the given encoder, or a new instance if necessary
     */
    private Replacement insertCallerChecks(Checker forCaller,
                                           BufferDecoder decoder, Replacement encoder,
                                           long code_length)
        throws IOException, ClassFormatException
    {
        final int startOffset = decoder.offset();

        final int endOffset;
        {
            long endL = startOffset + code_length;
            if (endL > Integer.MAX_VALUE) {
                throw new ClassFormatException();
            }
            endOffset = (int) endL;
        }

        final byte[] buffer = decoder.buffer();
        int offset = startOffset;
        int transferOffset = offset;

        while (offset < endOffset) {
            final int opOffset = offset;
            byte op = buffer[offset++];

            int methodRefIndex;
            C_MemberRef methodRef;

            switch (op) {
                default -> {
                    throw new ClassFormatException();
                }

                // Operations which are checked...

                case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE -> {
                    methodRefIndex = decodeUnsignedShortBE(buffer, offset);
                    methodRef = mConstantPool.findConstant(methodRefIndex, C_MemberRef.class);
                    offset += op != INVOKEINTERFACE ? 2 : 4;

                    Checker.ForClass forClass = forClass(forCaller, methodRef);

                    if (!forClass.isCallerChecked()) {
                        continue;
                    }

                    C_NameAndType nat = methodRef.mNameAndType;

                    if (nat.mName.isConstructor()) {
                        // Constructor check should have been applied in the target. See the
                        // isConstructor comment in the check method.
                        continue;
                    }

                    if (!forClass.isCallerMethodChecked(nat.mName, nat.mTypeDesc)) {
                        continue;
                    }
                }

                // Unchecked operations with no operands...

                case NOP, ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4,
                    ICONST_5, LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1,
                    ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3, FLOAD_0,
                    FLOAD_1, FLOAD_2, FLOAD_3, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3, ALOAD_0, ALOAD_1,
                    ALOAD_2, ALOAD_3, IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD,
                    SALOAD, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3, LSTORE_0, LSTORE_1, LSTORE_2,
                    LSTORE_3, FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3, DSTORE_0, DSTORE_1, DSTORE_2,
                    DSTORE_3, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3, IASTORE, LASTORE, FASTORE,
                    DASTORE, AASTORE, BASTORE, CASTORE, SASTORE, POP, POP2, DUP, DUP_X1, DUP_X2,
                    DUP2, DUP2_X1, DUP2_X2, SWAP, IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB,
                    IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV, IREM, LREM, FREM, DREM, INEG,
                    LNEG, FNEG, DNEG, ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR, IAND, LAND, IOR, LOR,
                    IXOR, LXOR, I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B,
                    I2C, I2S, LCMP, FCMPL, FCMPG, DCMPL, DCMPG, IRETURN, LRETURN, FRETURN, DRETURN,
                    ARETURN, RETURN, ARRAYLENGTH, ATHROW, MONITORENTER, MONITOREXIT ->
                {
                    continue;
                }

                // Unchecked operations with one operand byte...

                case BIPUSH, LDC, ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                    ISTORE, LSTORE, FSTORE, DSTORE, ASTORE, RET, NEWARRAY ->
                {
                    offset++;
                    continue;
                }

                // Unchecked operations with two operand bytes...

                case SIPUSH, LDC_W, LDC2_W, IINC, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ,
                    IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE,
                    GOTO, JSR, GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD, NEW, ANEWARRAY, CHECKCAST,
                    INSTANCEOF, IFNULL, IFNONNULL ->
                {
                    offset += 2;
                    continue;
                }

                // Unchecked operations with three operand bytes...

                case MULTIANEWARRAY -> {
                    offset += 3;
                    continue;
                }

                // Unchecked operations with four operand bytes...

                case INVOKEDYNAMIC, GOTO_W, JSR_W -> {
                    offset += 4;
                    continue;
                }

                // Unchecked operation with three or five operand bytes.

                case WIDE -> {
                    op = buffer[++offset];
                    offset += 2;
                    if (op == IINC) {
                        offset += 2;
                    }
                    continue;
                }

                // Unchecked operations with a complex amount of operand bytes...

                case TABLESWITCH -> {
                    offset += pad(offset - startOffset);
                    offset += 4; // skip the default jump target
                    int lowValue = decodeIntBE(buffer, offset); offset += 4;
                    int highValue = decodeIntBE(buffer, offset); offset += 4;
                    // skip the jump targets
                    offset = skip(offset, ((long) highValue - (long) lowValue + 1L) * 4L);
                    continue;
                }

                case LOOKUPSWITCH -> {
                    offset += pad(offset - startOffset);
                    offset += 4; // skip the default jump target
                    // skip the value and jump target pairs
                    offset = skip(offset, decodeIntBE(buffer, offset) * 8L);
                    continue;
                }
            } // end switch

            // This point is reached if the code needs to be modified.

            C_MemberRef proxyMethod = addProxyMethod(op, (byte) 1, methodRef);

            // Length of the attribute_length, max_stack, max_locals, and code_length fields.
            // They all appear immediately before the first bytecode operation.
            final int extraFieldsLength = 4 + 2 + 2 + 4;

            if (encoder == null) {
                // The originalLength also includes the attribute_length, max_stack,
                // max_locals, and code_length fields.
                long originalLength = extraFieldsLength + code_length;
                if (originalLength > Integer.MAX_VALUE) {
                    throw new ClassFormatException();
                }

                // Capacity is same as originalLength, because it won't need to increase.
                int capacity = (int) originalLength;

                encoder = new Replacement(capacity, originalLength);

                // Copy the fields into the encoder.
                encoder.write(buffer, startOffset - extraFieldsLength, extraFieldsLength);
            }

            // Copy all the code prior to this point, and prepare the next copy to skip over
            // the replacement code.
            encoder.write(buffer, transferOffset, opOffset - transferOffset);
            transferOffset = offset;

            // Invoke the proxy method instead.

            encoder.writeByte(INVOKESTATIC);
            encoder.writeShort(proxyMethod.mIndex);

            if (op == INVOKEINTERFACE) {
                // Original operation is five bytes, so pad with NOPs.
                encoder.writeShort(NOP << 8 | NOP);
            }
        }

        if (encoder != null) {
            // Copy the remaining code.
            encoder.write(buffer, transferOffset, offset - transferOffset);
        }

        decoder.offset(offset);

        return encoder;
    }

    /**
     * Returns a ForClass checker from the calling side, to a target.
     */
    private Checker.ForClass forClass(Checker forCaller, C_MemberRef methodRef) {
        if (methodRef.mClass.mIndex == mThisClassIndex) {
            // Calling into the same class, which is always allowed.
            return Rule.ALLOW;
        }

        ConstantPool.C_UTF8 packageName = mPackageName;
        ConstantPool.C_UTF8 className = mClassName;

        if (packageName == null) {
            mPackageName = packageName = mConstantPool.new C_UTF8();
            mClassName = className = mConstantPool.new C_UTF8();
        }

        methodRef.mClass.split(packageName, className);

        return forCaller.forClass(packageName, className);
    }

    /**
     * Proxy type 0: Used by MethodHandle constants with a target-side check, which ensures
     *               that the correct caller frame is available.
     *
     * private static File $3(String path) {
     *     return File.open(path);
     * }
     *
     * Proxy type 1: Basic caller-side check.
     *
     * private static File $3(String path) {
     *     if (thisClass.getModule() != File.class.getModule()) {
     *         throw new SecurityException();
     *     }
     *     return File.open(path);
     * }
     *
     * Proxy type 2: Native method.
     *
     * public int someNativeThing(int param) {
     *     SecurityAgent.check(SecurityAgent.WALKER.getCallerClass(), thisClass, name, desc);
     *     return this.$boxtin$_someNativeThing(param);
     * }
     *
     * @param op must be an INVOKE* or NEW operation
     */
    private C_MemberRef addProxyMethod(byte op, byte type, C_MemberRef methodRef)
        throws IOException
    {
        int access_flags = Modifier.PRIVATE | Modifier.STATIC | 0x1000; // | synthetic
        return addProxyMethod(op, type, methodRef, access_flags, null, null);
    }

    /**
     * @return null when given a proxyName and proxyDesc
     */
    private C_MemberRef addProxyMethod(byte op, byte type, C_MemberRef methodRef,
                                       int access_flags,
                                       ConstantPool.C_UTF8 proxyName, ConstantPool.C_UTF8 proxyDesc)
        throws IOException
    {
        ConstantPool cp = mConstantPool;

        if (mNewMethods == null) {
            mNewMethods = new HashMap<>();
            cp.extend();
        }

        Integer key = (op << 24) | (type << 16) | methodRef.mIndex;

        C_MemberRef proxyMethod;

        if (proxyName != null) {
            if (proxyDesc == null) {
                throw new IllegalArgumentException();
            }
            proxyMethod = null;
        } else {
            proxyMethod = mNewMethods.get(key);
            if (proxyMethod != null) {
                return proxyMethod;
            }
            proxyDesc = cp.addWithFullSignature(op, methodRef);
            C_Class thisClass = cp.findConstant(mThisClassIndex, C_Class.class);
            proxyMethod = cp.addUniqueMethod(thisClass, proxyDesc);
            proxyName = proxyMethod.mNameAndType.mName;
        }

        mNewMethods.put(key, proxyMethod);

        var encoder = mNewMethodsBuffer;

        if (encoder == null) {
            mNewMethodsBuffer = encoder = new Replacement(100, 0);
        }

        encoder.writeShort(access_flags);
        encoder.writeShort(proxyName.mIndex); // name_index
        encoder.writeShort(proxyDesc.mIndex);  // descriptor_index

        encoder.writeShort(1); // attributes_count
        encoder.writeShort(cp.addUTF8("Code").mIndex); // attribute_name_index
        int startPos = encoder.length();
        encoder.writeInt(0); // attribute_length; to be filled in properly later
        encoder.writeShort(0); // max_stack; to be filled in properly later
        encoder.writeShort(0); // max_locals; to be filled in properly later
        encoder.writeInt(0); // code_length; to be filled in properly later

        final int codeStartPos = encoder.length();

        int pushed = 0;
        int maxStack, labelOffset;

        switch (type) {
            default -> throw new AssertionError();

            case 0 -> {
                maxStack = 0;
                labelOffset = -1;
            }

            case 1 -> {
                maxStack = 1 + 1;

                int getModuleIndex = cp.addMethodRef
                    ("java/lang/Class", "getModule", "()Ljava/lang/Module;").mIndex;
                String exClassName = "java/lang/SecurityException";
                int exClassIndex = cp.addClass(exClassName).mIndex;
                int exInitIndex = cp.addMethodRef(exClassName, "<init>", "()V").mIndex;

                encoder.writeByte(LDC_W);
                encoder.writeShort(mThisClassIndex);
                encoder.writeByte(INVOKEVIRTUAL);
                encoder.writeShort(getModuleIndex);
                encoder.writeByte(LDC_W);
                encoder.writeShort(methodRef.mClass.mIndex);
                encoder.writeByte(INVOKEVIRTUAL);
                encoder.writeShort(getModuleIndex);
                encoder.writeByte(IF_ACMPEQ);
                encoder.writeShort(11); // offset past the ATHROW operation
                encoder.writeByte(NEW);
                encoder.writeShort(exClassIndex);
                encoder.writeByte(DUP);
                encoder.writeByte(INVOKESPECIAL);
                encoder.writeShort(exInitIndex);
                encoder.writeByte(ATHROW);

                labelOffset = encoder.length() - codeStartPos;
            }

            case 2 -> {
                maxStack = 4;
                labelOffset = -1;

                encodeAgentCheck(encoder, cp.addString(proxyName).mIndex,
                                 cp.addString(proxyDesc).mIndex);

                if (op != INVOKESTATIC) {
                    encoder.writeByte(ALOAD_0);
                    pushed++;
                }
            }
        }

        if (op == NEW) {
            maxStack = Math.max(maxStack, 2);
            encoder.writeByte(NEW);
            encoder.writeShort(methodRef.mClass.mIndex);
            encoder.writeByte(DUP);
            op = INVOKESPECIAL;
        }

        int nargs = proxyDesc.pushArgs(encoder);
        pushed += nargs;

        encoder.writeByte(op);
        encoder.writeShort(methodRef.mIndex);

        if (op == INVOKEINTERFACE) {
            encoder.writeByte(nargs);
            encoder.writeByte(0);
        }

        proxyDesc.returnValue(encoder);

        // Update max_stack, max_locals, and code_length.
        byte[] buffer = encoder.buffer();
        encodeShortBE(buffer, startPos + 4, Math.max(maxStack, pushed));
        encodeShortBE(buffer, startPos + 6, pushed);
        encodeIntBE(buffer, startPos + 8, encoder.length() - codeStartPos);

        encoder.writeShort(0); // exception_table_length

        if (labelOffset < 0) {
            encoder.writeShort(0); // attributes_count
        } else {
            encoder.writeShort(1); // attributes_count

            // Encode the StackMapTable attribute.
            encoder.writeShort(cp.addUTF8("StackMapTable").mIndex);
            encoder.writeInt(3);   // attribute_length
            encoder.writeShort(1); // number_of_entries
            encoder.writeByte(labelOffset); // same_frame
        }

        // Update attribute_length.
        buffer = encoder.buffer(); // might have been replaced due to growth
        encodeIntBE(buffer, startPos, encoder.length() - startPos - 4);

        return proxyMethod;
    }

    /**
     * Computes padding for TABLESWITCH and LOOKUPSWITCH.
     */
    private static int pad(int offset) {
        return (4 - (offset & 3)) & 3;
    }

    /**
     * @return the updated offset
     */
    private static int skip(int offset, long amount) throws ClassFormatException {
        long updated = offset + amount;
        if (updated < offset || updated > Integer.MAX_VALUE) {
            throw new ClassFormatException();
        }
        return (int) updated;
    }

    private static void skipAttributes(BufferDecoder decoder) throws IOException {
        for (int i = decoder.readUnsignedShort(); --i >= 0;) {
            decoder.readUnsignedShort(); // attribute_name_index
            decoder.skipNBytes(decoder.readUnsignedInt());
        }
    }
}
