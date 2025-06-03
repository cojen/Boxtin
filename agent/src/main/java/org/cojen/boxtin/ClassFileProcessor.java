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

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

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
            // Can be ignored because the class cannot be loaded anyhow.
            throw new ClassFormatException(true);
        }

        int minor = decoder.readUnsignedShort();
        int major = decoder.readUnsignedShort();

        if (major < 49) { // require Java 5+ (for supporting ldc of classes)
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

    private boolean mReflectionChecks;

    private Map<Integer, Replacement> mReplacements;

    private Map<Integer, C_MemberRef> mNewMethods;

    private Replacement mNewMethodsBuffer;

    private Map<Integer, C_MemberRef> mReplacedMethodHandles;

    // Work objects used by the local forClass method.
    private ConstantPool.C_UTF8 mPackageName, mClassName;

    // Must be assigned before calling the encodeAgentCheck method.
    private int mMaxLocals;

    // The access_flags for the current method, set as methods are checked.
    private int mMethodAccessFlags;

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
     * @param forCaller rules against the class when it's acting as a caller
     * @param forTargetClass rules against the class when it's acting as a target
     * @param reflectionChecks pass true to perform special caller-side reflection transforms
     * @return true if class requires modification
     */
    public boolean check(Rules forCaller, Rules.ForClass forTargetClass,
                         boolean reflectionChecks)
        throws IOException, ClassFormatException
    {
        final boolean targetClassChecked = forTargetClass.isAnyDeniedAtTarget()
            || forTargetClass.isAnyConstructorDenied(); // See isConstructor comment below.

        if (forCaller.isAllAllowed() && (!isAccessible(mAccessFlags) || !targetClassChecked)) {
            // No need to modify inaccessible classes, or those that aren't checked.
            return false;
        }

        mReflectionChecks = reflectionChecks;

        // Check the MethodHandle constants.

        mConstantPool.visitMethodHandleRefs((kind, offset, methodRef) -> {
            Rules.ForClass forClass = forClass(forCaller, methodRef);

            if (forClass.isAllAllowed()) {
                return;
            }

            C_NameAndType nat = methodRef.mNameAndType;

            Rule rule;
            byte op;
            byte proxyType;

            if (kind == REF_newInvokeSpecial) {
                rule = forClass.ruleForConstructor(nat.mTypeDesc);
                if (rule.isDenied()) {
                    op = NEW;
                    // Constructor check is always in the target.
                    proxyType = PT_PLAIN;
                } else {
                    return;
                }
            } else {
                rule = forClass.ruleForMethod(nat.mName, nat.mTypeDesc);
                if (rule.isDeniedAtTarget()) {
                    proxyType = PT_PLAIN;
                } else if (rule.isDeniedAtCaller()) {
                    proxyType = PT_CALLER;
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

            C_MemberRef proxyMethod = addProxyMethod(rule, op, proxyType, methodRef);

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
            mMethodAccessFlags = decoder.readUnsignedShort();
            int name_index = decoder.readUnsignedShort();
            int desc_index = decoder.readUnsignedShort();

            boolean targetCodeChecked;
            Rule targetRule; // can be null when !targetCodeChecked
            ConstantPool.C_UTF8 name, desc; // both can be null when !targetCodeChecked

            if (!targetClassChecked || !isAccessible(mMethodAccessFlags) ||
                (name = mConstantPool.findConstantUTF8(name_index)).isClinit())
            {
                targetCodeChecked = false;
                targetRule = null;
                name = null;
                desc = null;
            } else {
                desc = mConstantPool.findConstantUTF8(desc_index);

                if (name.isConstructor()) {
                    // Constructor check must only be in the target class. The code
                    // modifications to make it work in the client class are too complicated.
                    // The problem is that uninitialized objects cannot be passed to other
                    // methods, in this case, the proxy method. See insertCallerChecks.
                    targetRule = forTargetClass.ruleForConstructor(desc);
                    targetCodeChecked = targetRule.isDenied();
                    name = null; // indicate that the method is a constructor
                } else {
                    targetRule = forTargetClass.ruleForMethod(name, desc);
                    targetCodeChecked = targetRule.isDeniedAtTarget();
                }
            }

            if (!targetCodeChecked && forCaller.isAllAllowed()) {
                skipAttributes(decoder);
                continue;
            }

            if (targetCodeChecked && Modifier.isNative(mMethodAccessFlags)) {
                // First, rename the native method to start with NATIVE_PREFIX, and make it
                // private and synthetic.

                mConstantPool.extend();

                String newNameStr = SecurityAgent.NATIVE_PREFIX + name.str();
                ConstantPool.C_UTF8 newName = mConstantPool.addUTF8(newNameStr);

                var replacement = new Replacement(4, 4);
                int newFlags = mMethodAccessFlags & ~(Modifier.PUBLIC | Modifier.PROTECTED);
                newFlags |= Modifier.PRIVATE | 0x1000; // | synthetic
                replacement.writeShort(newFlags);
                replacement.writeShort(newName.mIndex);

                storeReplacement(startOffset, replacement);

                // Define a proxy method which matches the original, performs a check, and then
                // calls the renamed native method.

                byte op = Modifier.isStatic(mMethodAccessFlags) ? INVOKESTATIC : INVOKEVIRTUAL;

                C_Class thisClass = mConstantPool.findConstant(mThisClassIndex, C_Class.class);
                C_MemberRef methodRef = mConstantPool.addMethodRef(thisClass, newName, desc);

                addProxyMethod(targetRule, op, PT_NATIVE, methodRef,
                               mMethodAccessFlags & ~Modifier.NATIVE, name, desc);
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
                    DenyAction action = targetRule.denyAction();
                    replacement = insertChecks(forCaller, decoder, attrLength, name, desc, action);
                } else {
                    assert !forCaller.isAllAllowed();

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
            int numMethods = mMethodsCount + mNewMethods.size();
            if (numMethods >= 65536 || numMethods < 0) {
                throw new ClassFormatException();
            }
            int methodsStartOffset = mMethodsStartOffset + (int) cpGrowth;
            encodeShortBE(buffer, methodsStartOffset, numMethods);
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
    private Replacement insertChecks(Rules forCaller, BufferDecoder decoder, long codeAttrLength,
                                     ConstantPool.C_UTF8 name, ConstantPool.C_UTF8 desc,
                                     DenyAction action)
        throws IOException, ClassFormatException
    {
        ConstantPool cp = mConstantPool;
        cp.extend();

        final int name_index = name == null ? 0 : cp.addString(name).mIndex;

        // The copy needs room for new operations and possibly an updated StackMapTable.
        long capacity = codeAttrLength + 50;
        if (capacity > Integer.MAX_VALUE) {
            throw new ClassFormatException();
        }

        // Add 4 to the original length to account for the attribute_length field.
        var encoder = new Replacement((int) capacity, codeAttrLength + 4);

        int max_stack = decoder.readUnsignedShort();
        mMaxLocals = decoder.readUnsignedShort();
        long code_length = decoder.readUnsignedInt();

        encoder.writeInt(0); // attribute_length; to be filled in properly later
        encoder.writeShort(0); // max_stack; to be filled in properly later
        encoder.writeShort(0); // max_locals; to be filled in properly later
        encoder.writeInt(0); // code_length; to be filled in properly later

        long pair = encodeAgentCheck(encoder, name_index, desc, action);
        int pushed = (int) pair;
        int first = (int) (pair >> 32);

        // Fill in the proper max_stack and max_locals values.
        encodeShortBE(encoder.buffer(), 4, Math.max(max_stack, pushed));
        encodeShortBE(encoder.buffer(), 6, mMaxLocals);

        int codeGrowth = encoder.length() - 12;

        // Growth must be divisible by four, because switch statements are aligned as such.
        // Padding NOPs might need to be added.
        while ((codeGrowth & 3) != 0) {
            encoder.writeByte(NOP);
            codeGrowth++;
        }

        // Fill in the proper code_length.
        encodeIntBE(encoder.buffer(), 8, (int) (code_length + codeGrowth));

        if (forCaller.isAllAllowed()) {
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
        int attrCountOffset = encoder.length();
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
                    updateStackMapTableOffsets(decoder, encoder, attrLength, codeGrowth, first);
                }
            }
        }

        if (first != 0 && numStackMapTables == 0) {
            // Need to create a StackMapTable attribute.
            encoder.writeShort(cp.addUTF8("StackMapTable").mIndex);
            encodeOneStackMapEntry(encoder, 2, first);
            encodeShortBE(encoder.buffer(), attrCountOffset, attrCount + 1);
        }

        // Fill in the proper attribute_length.
        encodeIntBE(encoder.buffer(), 0, encoder.length() - 4);

        return encoder;
    }

    private static int clampShort(int value) {
        return Math.min(value, 65535);
    }

    /**
     * With the standard DenyAction:
     *
     * SecurityAgent.check(SecurityAgent.WALKER.getCallerClass(), thisClass, name, desc);
     *
     * or else:
     *
     * if (!SecurityAgent.tryCheck(SecurityAgent.WALKER.getCallerClass(), thisClass, name, desc)) {
     *     // One possible form.
     *     throw new IOException();
     * }
     *
     * Requires at least 4 operand stack slots.
     *
     * @param name_index pass 0 for constructors
     * @return lower word: number of stack slots pushed; upper word: a non-zero first offset if
     * a StackMapTable attribute is required
     */
    private long encodeAgentCheck(BufferEncoder encoder, int name_index, ConstantPool.C_UTF8 desc,
                                  DenyAction action)
        throws IOException
    {
        int codeStartPos = encoder.length();
        ConstantPool cp = mConstantPool;

        int desc_index = cp.addString(desc).mIndex;

        String agentName = SecurityAgent.CLASS_NAME;
        String walkerName = StackWalker.class.getName().replace('.', '/');
        String walkerDesc = 'L' + walkerName + ';';
        String classDesc = Class.class.descriptorString();
        String stringDesc = String.class.descriptorString();
        String callerDesc = "()" + classDesc;
        String checkDesc = '(' + classDesc + classDesc + stringDesc + stringDesc + ')' +
            (action == DenyAction.standard() ? 'V' : 'Z');

        encoder.writeByte(GETSTATIC);
        encoder.writeShort(cp.addFieldRef(agentName, "WALKER", walkerDesc).mIndex);
        encoder.writeByte(INVOKEVIRTUAL);
        encoder.writeShort(cp.addMethodRef(walkerName, "getCallerClass", callerDesc).mIndex);

        int callerSlot = -1;

        if (action.requiresCaller()) {
            // Capture the caller in a local variable, in case it's needed again.
            encoder.writeByte(ASTORE);
            callerSlot = mMaxLocals++;
            encoder.writeByte(callerSlot);
            encoder.writeByte(ALOAD);
            encoder.writeByte(callerSlot);
        }

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

        boolean standard = action == DenyAction.standard();
        String checkName = standard ? "check" : "tryCheck";
        encoder.writeShort(cp.addMethodRef(agentName, checkName, checkDesc).mIndex);

        if (standard) {
            return 4;
        }

        encoder.writeByte(IFNE);

        long pushed = encodeDenyAction(encoder, action, callerSlot, name_index, desc);
        pushed = Math.max(4, pushed);

        int first = encoder.length() - codeStartPos;

        // Write a NOP, to ensure that a unique offset exists for the StackMapTable entry. This
        // is simpler than attempting to update the first entry if it's at offset 0.
        encoder.writeByte(NOP);

        return pushed | ((long) first) << 32;
    }

    /**
     * Caller has already written an IFNE or IF_ACMPEQ opcode, and this method encodes the
     * branch offset.
     *
     * @param callerSlot is used by target-side DenyAction.Dynamic; can pass -1 if defining a
     * caller-side method
     * @param name_index pass 0 for constructors
     * @return number of stack slots pushed
     */
    private int encodeDenyAction(BufferEncoder encoder, DenyAction action, int callerSlot,
                                 int name_index, ConstantPool.C_UTF8 desc)
        throws IOException
    {
        int offset = encoder.length();
        encoder.writeShort(0); // branch offset; to be filled in properly later

        int checkedPushed = 0, checkedOffset = 0, checkedDynamicOffset = 0;

        if (action instanceof DenyAction.Checked checked) {
            checkedPushed = encodeMethodHandleInvoke(encoder, callerSlot, checked.predicate);
            encoder.write(IFEQ); // if false, then the operation isn't denied
            checkedOffset = encoder.length();
            encoder.writeShort(0); // branch offset; to be filled in properly later
            action = checked.action;
        }

        int pushed;

        encode: {
            DenyAction.Exception exAction;

            if (action instanceof DenyAction.Exception) {
                exAction = (DenyAction.Exception) action;
            } else {
                if (action instanceof DenyAction.Value va) {
                    if (name_index != 0) { // not a constructor
                        pushed = encodeValueAndReturn(encoder, va.value, desc);
                        break encode;
                    }
                } else if (action instanceof DenyAction.Empty) {
                    if (name_index != 0) { // not a constructor
                        pushed = encodeEmptyAndReturn(encoder, desc);
                        break encode;
                    }
                } else if (action instanceof DenyAction.Custom cu) {
                    pushed = encodeCustomAndReturn(encoder, callerSlot, name_index, cu);
                    break encode;
                } else if (action instanceof DenyAction.Dynamic dyn && callerSlot >= 0) {
                    long pair = encodeDynamicAndReturn(encoder, callerSlot, name_index, desc, dyn);
                    pushed = (int) pair;
                    checkedDynamicOffset = (int) (pair >> 32);
                    break encode;
                }

                // Make sure an exception is always thrown.
                exAction = DenyAction.Standard.THE;
            }

            pushed = encodeExceptionAction(encoder, exAction);
        }

        // Encode the branch target offsets.

        if (checkedOffset != 0) {
            encodeShortBE(encoder.buffer(), checkedOffset, encoder.length() - checkedOffset + 1);
        }

        if (checkedDynamicOffset != 0) {
            encodeShortBE(encoder.buffer(), checkedDynamicOffset,
                          encoder.length() - checkedDynamicOffset + 1);
        }

        encodeShortBE(encoder.buffer(), offset, encoder.length() - offset + 1);

        return Math.max(checkedPushed, pushed);
    }

    /**
     * @return number of stack slots pushed
     */
    private int encodeExceptionAction(BufferEncoder encoder, DenyAction.Exception exAction)
        throws IOException
    {
        ConstantPool cp = mConstantPool;
        int exClassIndex = cp.addClass(exAction.className).mIndex;
        ConstantPool.C_MemberRef exInitRef;
        int pushed;

        if (exAction instanceof DenyAction.WithMessage wm) {
            exInitRef = cp.addMethodRef(exAction.className, "<init>", "(Ljava/lang/String;)V");
            encoder.writeByte(NEW);
            encoder.writeShort(exClassIndex);
            encoder.writeByte(DUP);
            encoder.writeByte(LDC_W);
            encoder.writeShort(cp.addString(wm.message).mIndex);
            pushed = 3;
        } else {
            exInitRef = cp.addMethodRef(exAction.className, "<init>", "()V");
            encoder.writeByte(NEW);
            encoder.writeShort(exClassIndex);
            encoder.writeByte(DUP);
            pushed = 2;
        }

        encoder.writeByte(INVOKESPECIAL);
        encoder.writeShort(exInitRef.mIndex);
        encoder.writeByte(ATHROW);

        return pushed;
    }

    /**
     * @return number of stack slots pushed
     */
    private int encodeValueAndReturn(BufferEncoder encoder, Object value,
                                     ConstantPool.C_UTF8 desc)
        throws IOException
    {
        int pushed = tryEncodeValueAndReturn(encoder, value, desc.charAt(desc.length() - 1));

        if (pushed <= 0) {
            pushed = tryEncodeObjectValue(encoder, value, desc);
            if (pushed <= 0) {
                pushed = 1;
                encoder.writeByte(ACONST_NULL);
            }
            encoder.writeByte(ARETURN);
        }

        return pushed;
    }

    /**
     * Try to encode a primitive value, but don't encode the branch offset.
     *
     * @return stack slots pushed; is zero if nothing was encoded
     */
    private int tryEncodeValueAndReturn(BufferEncoder encoder, Object value, char type)
        throws IOException
    {
        switch (type) {
        default -> {
            return 0;
        }

        case 'V' -> {
            encoder.writeByte(RETURN);
        }

        case 'Z' -> {
            boolean v = value instanceof Boolean b && b;
            encoder.writeByte(v ? ICONST_1 : ICONST_0);
            encoder.writeByte(IRETURN);
        }

        case 'C' -> {
            char v = (value instanceof Character c) ? c :
                ((value instanceof Number n) ? ((char) n.intValue()) : 0);
            mConstantPool.pushInt(encoder, v);
            encoder.writeByte(IRETURN);
        }

        case 'B' -> {
            byte v = (value instanceof Number n) ? n.byteValue() : 0;
            mConstantPool.pushInt(encoder, v);
            encoder.writeByte(IRETURN);
        }

        case 'S' -> {
            short v = (value instanceof Number n) ? n.shortValue() : 0;
            mConstantPool.pushInt(encoder, v);
            encoder.writeByte(IRETURN);
        }

        case 'I' -> {
            int v = (value instanceof Number n) ? n.intValue() : 0;
            mConstantPool.pushInt(encoder, v);
            encoder.writeByte(IRETURN);
        }

        case 'J' -> {
            long v = (value instanceof Number n) ? n.longValue() : 0;
            mConstantPool.pushLong(encoder, v);
            encoder.writeByte(LRETURN);
            return 2;
        }

        case 'F' -> {
            float v = (value instanceof Number n) ? n.floatValue() : 0;
            mConstantPool.pushFloat(encoder, v);
            encoder.writeByte(FRETURN);
        }

        case 'D' -> {
            double v = (value instanceof Number n) ? n.doubleValue() : 0;
            mConstantPool.pushDouble(encoder, v);
            encoder.writeByte(DRETURN);
            return 2;
        }
        }

        return 1;
    }

    /**
     * @return stack slots pushed; is zero if nothing was encoded
     */
    private int tryEncodeObjectValue(BufferEncoder encoder, Object value, ConstantPool.C_UTF8 desc)
        throws IOException
    {
        if (value == null) {
            return 0;
        }

        if (value instanceof String str) {
            if (isCompatible(desc, String.class)) {
                encoder.writeByte(LDC_W);
                encoder.writeShort(mConstantPool.addString(str).mIndex);
                return 1;
            }
            return 0;
        }

        char primType;
        int pushed;

        pushPrim: {
            int intValue;

            prepInt: {
                if (value instanceof Number n) {
                    if (n instanceof Integer v) {
                        if (isCompatible(desc, Integer.class)) {
                            primType = 'I';
                            intValue = v.intValue();
                            break prepInt;
                        }
                        return 0;
                    }

                    if (n instanceof Long v) {
                        if (isCompatible(desc, Long.class)) {
                            primType = 'J';
                            pushed = 2;
                            mConstantPool.pushLong(encoder, v);
                            break pushPrim;
                        }
                        return 0;
                    }

                    if (n instanceof Double v) {
                        if (isCompatible(desc, Double.class)) {
                            primType = 'D';
                            pushed = 2;
                            mConstantPool.pushDouble(encoder, v);
                            break pushPrim;
                        }
                        return 0;
                    }

                    if (n instanceof Float v) {
                        if (isCompatible(desc, Float.class)) {
                            primType = 'F';
                            pushed = 1;
                            mConstantPool.pushFloat(encoder, v);
                            break pushPrim;
                        }
                        return 0;
                    }

                    if (n instanceof Byte v) {
                        if (isCompatible(desc, Byte.class)) {
                            primType = 'B';
                            intValue = v.intValue();
                            break prepInt;
                        }
                        return 0;
                    }

                    if (n instanceof Short v) {
                        if (isCompatible(desc, Short.class)) {
                            primType = 'S';
                            intValue = v.intValue();
                            break prepInt;
                        }
                        return 0;
                    }

                    return 0;
                }

                if (value instanceof Boolean b) {
                    if (isCompatible(desc, Boolean.class)) {
                        primType = 'Z';
                        intValue = b ? 1 : 0;
                        break prepInt;
                    }
                    return 0;
                }

                if (value instanceof Character c) {
                    if (isCompatible(desc, Character.class)) {
                        primType = 'C';
                        intValue = c.charValue();
                        break prepInt;
                    }
                    return 0;
                }

                return 0;
            }

            pushed = 1;
            mConstantPool.pushInt(encoder, intValue);
        }

        String boxedClass = value.getClass().getName().replace('.', '/');

        C_MemberRef ref = mConstantPool.addMethodRef
            (boxedClass, "valueOf", "" + '(' + primType + ')' + 'L' + boxedClass + ';');

        encoder.writeByte(INVOKESTATIC);
        encoder.writeShort(ref.mIndex);

        return pushed;
    }

    /**
     * Returns true if the given method descriptor return type can be assigned to the "to" type.
     */
    private static boolean isCompatible(ConstantPool.C_UTF8 desc, Class<?> to) {
        String str = desc.str();
        do {
            if (str.endsWith(')' + to.descriptorString())) {
                return true;
            }
        } while ((to = to.getSuperclass()) != null);
        return false;
    }

    /**
     * @return stack slots pushed
     */
    private int encodeEmptyAndReturn(BufferEncoder encoder, ConstantPool.C_UTF8 desc)
        throws IOException
    {
        int pushed = 1;
        char type = desc.charAt(desc.length() - 1);
        char prefix = desc.charAt(desc.length() - 2);

        if (prefix == '[') {
            type = prefix;
        }

        switch (type) {
        case 'V' -> {
            encoder.writeByte(RETURN);
        }

        case 'Z', 'C', 'B', 'S', 'I' -> {
            encoder.writeByte(ICONST_0);
            encoder.writeByte(IRETURN);
        }

        case 'J' -> {
            encoder.writeByte(LCONST_0);
            encoder.writeByte(LRETURN);
        }

        case 'F' -> {
            encoder.writeByte(FCONST_0);
            encoder.writeByte(FRETURN);
        }

        case 'D' -> {
            encoder.writeByte(DCONST_0);
            encoder.writeByte(DRETURN);
        }

        default -> {
            String descStr = desc.str();
            int ix = descStr.indexOf(')') + 1;
            if (ix < 2 || ix >= descStr.length()) {
                // Descriptor is broken.
                encoder.writeByte(ACONST_NULL);
            } else if (descStr.charAt(ix) == '[') {
                // Empty array.

                pushed = 2;
                encoder.writeByte(ICONST_0);

                type = descStr.charAt(ix + 1);

                byte code = switch (type) {
                    default -> 0;
                    case 'Z' -> 4; case 'C' -> 5; case 'F' -> 6; case 'D' -> 7;
                    case 'B' -> 8; case 'S' -> 9; case 'I' -> 10; case 'J' -> 11;
                };

                if (code != 0) {
                    encoder.writeByte(NEWARRAY);
                    encoder.writeByte(code);
                } else {
                    int start, end;
                    if (descStr.charAt(++ix) == '[') {
                        start = ix;
                        end = descStr.length();
                    } else {
                        start = ix + 1;
                        end = descStr.length() - 1;
                    }
                    if (start < end) {
                        String elementType = descStr.substring(start, end);
                        encoder.writeByte(ANEWARRAY);
                        encoder.writeShort(mConstantPool.addClass(elementType).mIndex);
                    } else {
                        // Descriptor is broken.
                        encoder.writeByte(POP);
                        encoder.writeByte(ACONST_NULL);
                    }
                }
            } else {
                pushed = 2;
                ConstantPool cp = mConstantPool;
                descStr = descStr.substring(ix + 1, descStr.length() - 1);
                C_NameAndType nat = EmptyActions.findMethod(cp, descStr);
                if (nat != null) {
                    encoder.write(INVOKESTATIC);
                    C_MemberRef ref = cp.addMethodRef(cp.addClass(EmptyActions.CLASS_NAME), nat);
                    encoder.writeShort(ref.mIndex);
                } else {
                    C_Class clazz = cp.addClass(descStr);
                    C_MemberRef init = cp.addMethodRef(clazz, "<init>", "()V");
                    encoder.write(NEW);
                    encoder.writeShort(clazz.mIndex);
                    encoder.write(DUP);
                    encoder.write(INVOKESPECIAL);
                    encoder.writeShort(init.mIndex);
                }
            }

            encoder.writeByte(ARETURN);
        }
        }

        return pushed;
    }

    /**
     * @param callerSlot valid slot to the caller local variable; can pass -1 if defining a
     * caller-side method
     * @param name_index pass 0 for constructors
     * @return number of stack slots pushed
     */
    private int encodeCustomAndReturn(BufferEncoder encoder, int callerSlot,
                                      int name_index, DenyAction.Custom custom)
        throws IOException
    {
        int pushed = encodeMethodHandleInvoke(encoder, callerSlot, custom.mhi);

        if (name_index == 0) {
            // Denied constructors must always throw an exception.
            pushed = Math.max(pushed, encodeExceptionAction(encoder, DenyAction.Standard.THE));
        } else {
            String descStr = custom.mhi.getMethodType().descriptorString();
            int ix = descStr.indexOf(')') + 1;
            char type = (ix < 2 || ix >= descStr.length()) ? 0 : descStr.charAt(ix);

            byte op;
            switch (type) {
                default -> op = ARETURN;
                case 'V' -> op = RETURN;
                case 'Z', 'C', 'B', 'S', 'I' -> op = IRETURN;
                case 'F' -> op = FRETURN;
                case 'J' -> {
                    op = LRETURN;
                    pushed = Math.max(2, pushed);
                }
                case 'D' -> {
                    op = DRETURN;
                    pushed = Math.max(2, pushed);
                }
            }

            encoder.writeByte(op);
        }

        return pushed;
    }

    /**
     * @param callerSlot valid slot to the caller local variable; can pass -1 if defining a
     * caller-side method
     * @return number of stack slots pushed
     */
    private int encodeMethodHandleInvoke(BufferEncoder encoder, int callerSlot,
                                         MethodHandleInfo mhi)
        throws IOException
    {
        int pushed = 1;

        encoder.writeByte(LDC_W);
        encoder.writeShort(mConstantPool.addMethodHandle(mhi).mIndex);

        int slot = 0;
        MethodType mt = mhi.getMethodType();
        String invokeDesc = mt.descriptorString();

        loop: for (int i = 0; i < invokeDesc.length(); ) {
            int c = invokeDesc.charAt(i++);
            switch (c) {
                default -> {
                    break loop;
                }
                case '(', 'V' -> {}
                case 'Z', 'B', 'S', 'C', 'I' -> {
                    encoder.writeByte(ILOAD);
                    encoder.writeByte(slot++);
                    pushed++;
                }
                case 'J' -> {
                    encoder.writeByte(LLOAD);
                    encoder.writeByte(slot); slot += 2;
                    pushed += 2;
                }
                case 'F' -> {
                    encoder.writeByte(FLOAD);
                    encoder.writeByte(slot++);
                    pushed++;
                }
                case 'D' -> {
                    encoder.writeByte(DLOAD);
                    encoder.writeByte(slot); slot += 2;
                    pushed += 2;
                }
                case 'L', '[' -> {
                    if (i == 2 && mt.parameterType(0) == Class.class) {
                        // Pass the caller class.
                        if (callerSlot < 0) {
                            encoder.writeByte(LDC_W);
                            encoder.writeShort(mThisClassIndex);
                        } else {
                            encoder.writeByte(ALOAD);
                            encoder.writeByte(callerSlot);
                        }
                    } else {
                        encoder.writeByte(ALOAD);
                        encoder.writeByte(slot++);
                    }

                    pushed++;

                    int j = invokeDesc.indexOf(';', i) + 1;

                    if (j > 0) {
                        i = j;
                    } else if (c == '[') {
                        while (i < invokeDesc.length() && invokeDesc.charAt(i++) == '[');
                    } else {
                        // Descriptor is broken.
                        i--;
                        break;
                    }
                }
            }
        }

        C_MemberRef invokeRef = mConstantPool.addMethodRef
            (MethodHandle.class.getName().replace('.', '/'), "invoke", invokeDesc);

        encoder.writeByte(INVOKEVIRTUAL);
        encoder.writeShort(invokeRef.mIndex);

        return pushed;
    }

    /**
     * @param callerSlot valid slot to the caller local variable
     * @param name_index pass 0 for constructors
     * @return lower word: number of stack slots pushed; upper word: a non-zero branch offset
     * to fill in if the action is checked
     */
    private long encodeDynamicAndReturn(BufferEncoder encoder, int callerSlot,
                                        int name_index, ConstantPool.C_UTF8 desc,
                                        DenyAction.Dynamic dynamic)
        throws IOException
    {
        // Dynamic actions should only be used for target-side methods.
        assert callerSlot >= 0;

        String descStr = desc.str();

        ClassDesc retTypeDesc;

        try {
            retTypeDesc = MethodTypeDesc.ofDescriptor(descStr).returnType();
        } catch (IllegalArgumentException e) {
            // Descriptor is broken.
            encoder.writeByte(ACONST_NULL);
            encoder.writeByte(ARETURN);
            return 1;
        }

        C_Class retClass, boxedClass;

        if (!retTypeDesc.isPrimitive()) {
            String str = retTypeDesc.descriptorString();
            if (!retTypeDesc.isArray()) {
                // Strip off the 'L' and ';' characters.
                str = str.substring(1, str.length() - 1);
            }
            retClass = mConstantPool.addClass(str);
            boxedClass = null;
        } else {
            retClass = null;

            Class<?> boxed = switch (retTypeDesc.descriptorString().charAt(0)) {
                default -> Void.class;
                case 'B' -> Byte.class;
                case 'C' -> Character.class;
                case 'D' -> Double.class;
                case 'F' -> Float.class;
                case 'I' -> Integer.class;
                case 'J' -> Long.class;
                case 'S' -> Short.class;
                case 'Z' -> Boolean.class;
            };

            boxedClass = mConstantPool.addClass(boxed.getName().replace('.', '/'));
        }

        encoder.writeByte(ALOAD);
        encoder.writeByte(callerSlot);
        encoder.writeByte(LDC_W);
        encoder.writeShort(mThisClassIndex);

        if (name_index != 0) {
            encoder.writeByte(LDC_W);
            encoder.writeShort(name_index);
        } else {
            encoder.writeByte(ACONST_NULL);
        }

        encoder.writeByte(LDC_W);
        encoder.writeShort(mConstantPool.addString(desc).mIndex);

        if (retClass != null) {
            encoder.writeByte(LDC_W);
            encoder.writeShort(retClass.mIndex);
        } else {
            encoder.writeByte(GETSTATIC);
            encoder.writeShort(mConstantPool.addFieldRef
                               (boxedClass, "TYPE", Class.class.descriptorString()).mIndex);
        }

        int pushed;

        {
            boolean pushThis;
            int slot;
            if (name_index == 0) {
                // Cannot push `this` from a constructor -- it's not initialized yet.
                pushThis = false;
                slot = 1;
            } else {
                pushThis = !Modifier.isStatic(mMethodAccessFlags);
                slot = pushThis ? 1 : 0;
            }

            pushed = 5 + desc.pushArgsObject(encoder, pushThis, slot);
        }

        int argsSlot = -1;

        if (dynamic instanceof DenyAction.CheckedDynamic) {
            // Capture the args object in a local variable, to be checked later.
            encoder.writeByte(ASTORE);
            argsSlot = mMaxLocals++;
            encoder.writeByte(argsSlot);
            encoder.writeByte(ALOAD);
            encoder.writeByte(argsSlot);
        }

        String agentName = SecurityAgent.CLASS_NAME;
        String classDesc = Class.class.descriptorString();
        String stringDesc = String.class.descriptorString();
        String objectDesc = Object.class.descriptorString();
        String applyDesc = '(' + classDesc + classDesc + stringDesc + stringDesc +
            classDesc + '[' + objectDesc + ')' + objectDesc;

        encoder.writeByte(INVOKESTATIC);
        encoder.writeShort(mConstantPool.addMethodRef
                           (agentName, "applyDenyAction", applyDesc).mIndex);

        int branchOffset = 0;

        if (dynamic instanceof DenyAction.CheckedDynamic) {
            // If the value is the same as the args object, then this signals that the
            // operation isn't actually denied. See DenyAction.Checked::apply.
            encoder.writeByte(ASTORE);
            int valueSlot = mMaxLocals++;
            encoder.writeByte(valueSlot);
            encoder.writeByte(ALOAD);
            encoder.writeByte(valueSlot);
            encoder.writeByte(ALOAD);
            encoder.writeByte(argsSlot);
            encoder.writeByte(IF_ACMPEQ);
            branchOffset = encoder.length();
            encoder.writeShort(0); // branch offset; to be filled in properly later
            encoder.writeByte(ALOAD);
            encoder.writeByte(valueSlot);
        }

        if (name_index == 0) {
            // Denied constructors must always throw an exception.
            pushed = Math.max(pushed, encodeExceptionAction(encoder, DenyAction.Standard.THE));
        } else {
            if (retClass != null) {
                encoder.writeByte(CHECKCAST);
                encoder.writeShort(retClass.mIndex);
            } else {
                char c = retTypeDesc.descriptorString().charAt(0);
                if (c != 'V') {
                    encoder.writeByte(CHECKCAST);
                    encoder.writeShort(boxedClass.mIndex);
                    encoder.writeByte(INVOKEVIRTUAL);
                    String methodName = retTypeDesc.displayName() + "Value";
                    encoder.writeShort(mConstantPool.addMethodRef
                                       (boxedClass, methodName, "()" + c).mIndex);
                }
            }

            desc.returnValue(encoder);
        }

        return pushed | ((long) branchOffset) << 32;
    }

    /**
     * Decodes a StackMapTable and re-encodes with all offsets incremented by the given delta.
     *
     * @param first pass a non-zero offset to insert a first entry at this offset
     */
    private static void updateStackMapTableOffsets(BufferDecoder decoder, Replacement encoder,
                                                   long attrLength, int delta, int first)
        throws IOException
    {
        final int startOffset = encoder.length();
        final int numEntries = decoder.readUnsignedShort();

        int consumed;

        if (numEntries == 0) {
            if (first == 0) {
                encoder.writeInt((int) attrLength);
                encoder.writeShort(numEntries);
            } else {
                // Insert a new first entry.
                encodeOneStackMapEntry(encoder, attrLength, first);
            }
            consumed = 2;
        } else {
            int attrLengthOffset = encoder.length();
            int newAttrLength = (int) attrLength;
            encoder.writeInt(0); // attribute_length; to be filled in properly later

            int firstDelta;

            if (first == 0) {
                encoder.writeShort(numEntries);
                firstDelta = delta;
            } else {
                // Insert a new first entry.
                encoder.writeShort(numEntries + 1); // number_of_entries
                if (first < 64) {
                    newAttrLength++;
                    encoder.writeByte(first); // same_frame
                } else {
                    newAttrLength += 3;
                    encoder.writeByte(251); // same_frame_extended
                    encoder.writeShort(first);
                }
                firstDelta = delta - (first + 1);
            }

            // Update the original first entry.
            int type = decoder.readUnsignedByte();

            if (type < 64) { // same_frame
                if (type + firstDelta < 64) {
                    encoder.writeByte(type + firstDelta);
                } else {
                    // Convert to same_frame_extended.
                    newAttrLength += 2;
                    encoder.writeByte(251);
                    encoder.writeShort(type + firstDelta);
                }
                consumed = 3;
            } else if (type < 128) { // same_locals_1_stack_item_frame
                if (type + firstDelta < 128) {
                    encoder.writeByte(type + firstDelta);
                } else {
                    // Convert to same_locals_1_stack_item_frame_extended.
                    newAttrLength += 2;
                    encoder.writeByte(247);
                    encoder.writeShort(type - 64 + firstDelta);
                }
                consumed = 3;
            } else if (type < 247) {
                // Not legal, so just leave it alone.
                encoder.writeByte(type);
                consumed = 3;
            } else {
                // chop_frame, same_frame_extended, append_frame, or full_frame
                encoder.writeByte(type);
                encoder.writeShort(decoder.readUnsignedShort() + firstDelta);
                consumed = 5;
            }

            // Fill in the proper attribute_length.
            encodeIntBE(encoder.buffer(), attrLengthOffset, newAttrLength);
        }

        decoder.transferTo(encoder, attrLength - consumed);

        // Update the offsets of all Uninitialized_variable_infos.

        byte[] buffer = encoder.buffer();
        int offset = startOffset + 4 + 2;

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
     * @param attrLength existing attribute length; pass 2 if creating a new attribute
     * @param offset the one code offset to encode (must not be zero)
     */
    private static void encodeOneStackMapEntry(BufferEncoder encoder, long attrLength, int offset)
        throws IOException
    {
        if (offset < 64) {
            encoder.writeInt((int) attrLength + 1);
            encoder.writeShort(1); // number_of_entries
            encoder.writeByte(offset); // same_frame
        } else {
            encoder.writeInt((int) attrLength + 3);
            encoder.writeShort(1); // number_of_entries
            encoder.writeByte(251); // same_frame_extended
            encoder.writeShort(offset);
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
    private Replacement insertCallerChecks(Rules forCaller,
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
            Rule rule;

            switch (op) {
                default -> {
                    throw new ClassFormatException();
                }

                // Operations which are checked...

                case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE -> {
                    methodRefIndex = decodeUnsignedShortBE(buffer, offset);
                    methodRef = mConstantPool.findConstant(methodRefIndex, C_MemberRef.class);
                    offset += op != INVOKEINTERFACE ? 2 : 4;

                    Rules.ForClass forClass = forClass(forCaller, methodRef);

                    if (!forClass.isAnyDeniedAtCaller()) {
                        continue;
                    }

                    C_NameAndType nat = methodRef.mNameAndType;

                    if (nat.mName.isConstructor()) {
                        // Constructor check should have been applied in the target. See the
                        // isConstructor comment in the check method.
                        continue;
                    }

                    rule = forClass.ruleForMethod(nat.mName, nat.mTypeDesc);

                    if (!rule.isDeniedAtCaller()) {
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
                    op = buffer[offset++];
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

            mConstantPool.extend();

            byte type = PT_CALLER;

            if (mReflectionChecks) {
                ConstantPool.C_UTF8 className = methodRef.mClass.mValue;

                if (className.equals("java/lang/Class") ||
                    className.equals("java/lang/invoke/MethodHandles$Lookup"))
                {
                    C_NameAndType nat = Reflection.findMethod(mConstantPool, methodRef);

                    if (nat != null) {
                        type = PT_REFLECTION;
                        methodRef = mConstantPool.addMethodRef
                            (mConstantPool.addClass(Reflection.CLASS_NAME), nat);
                    }
                }
            }

            C_MemberRef proxyMethod = addProxyMethod(rule, op, type, methodRef);

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
     * Returns ForClass rules from the calling side, to a target.
     */
    private Rules.ForClass forClass(Rules forCaller, C_MemberRef methodRef) {
        if (methodRef.mClass.mIndex == mThisClassIndex) {
            // Calling into the same class, which is always allowed.
            return Rule.allow();
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

    private static final byte PT_PLAIN = 0, PT_CALLER = 1, PT_NATIVE = 2, PT_REFLECTION = 3;

    /**
     * PT_PLAIN: Used by MethodHandle constants with a target-side check, which ensures that
     *           the correct caller frame is available.
     *
     * private static File $3(String path) {
     *     return File.open(path);
     * }
     *
     * PT_CALLER: Basic caller-side check.
     *
     * private static File $3(String path) {
     *     if (thisClass.getModule() != File.class.getModule()) {
     *         throw new SecurityException();
     *     }
     *     return File.open(path);
     * }
     *
     * PT_NATIVE: Native method.
     *
     * public int someNativeThing(int param) {
     *     SecurityAgent.check(SecurityAgent.WALKER.getCallerClass(), thisClass, name, desc);
     *     return this.$boxtin$_someNativeThing(param);
     * }
     *
     * PT_REFLECTION: Reflection.
     *
     * private static Method $7(Class clazz, String name, Class... paramTypes) {
     *     Reflection r = SecurityAgent.reflection();
     *     return r.Class_getMethod(clazz, name, paramTypes);
     * }
     *
     * @param rule must be a deny rule
     * @param op must be an INVOKE* or NEW operation
     * @param type PT_*
     */
    private C_MemberRef addProxyMethod(Rule rule, byte op, byte type, C_MemberRef methodRef)
        throws IOException
    {
        int access_flags = Modifier.PRIVATE | Modifier.STATIC | 0x1000; // | synthetic
        return addProxyMethod(rule, op, type, methodRef, access_flags, null, null);
    }

    /**
     * @return null when given a proxyName and proxyDesc
     */
    private C_MemberRef addProxyMethod(Rule rule, byte op, byte type, C_MemberRef methodRef,
                                       int access_flags,
                                       ConstantPool.C_UTF8 proxyName, ConstantPool.C_UTF8 proxyDesc)
        throws IOException
    {
        assert rule.isDenied();

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
            if (type != PT_REFLECTION) {
                proxyDesc = cp.addWithFullSignature(op, methodRef);
            } else {
                proxyDesc = methodRef.mNameAndType.mTypeDesc;
            }
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
        int firstSlot = 0;
        int maxStack, labelOffset;

        switch (type) {
            default -> throw new AssertionError();

            case PT_PLAIN -> {
                maxStack = 0;
                labelOffset = -1;
            }

            case PT_CALLER -> {
                maxStack = 1 + 1;

                int getModuleIndex = cp.addMethodRef
                    ("java/lang/Class", "getModule", "()Ljava/lang/Module;").mIndex;

                encoder.writeByte(LDC_W);
                encoder.writeShort(mThisClassIndex);
                encoder.writeByte(INVOKEVIRTUAL);
                encoder.writeShort(getModuleIndex);
                encoder.writeByte(LDC_W);
                encoder.writeShort(methodRef.mClass.mIndex);
                encoder.writeByte(INVOKEVIRTUAL);
                encoder.writeShort(getModuleIndex);
                encoder.writeByte(IF_ACMPEQ);

                int name_index = methodRef.mNameAndType.mName.mIndex;
                pushed = encodeDenyAction(encoder, rule.denyAction(), -1, name_index, proxyDesc);

                labelOffset = encoder.length() - codeStartPos;
            }

            case PT_NATIVE -> {
                maxStack = 4;

                int originalMaxLocals = proxyDesc.numArgs(2);
                mMaxLocals = originalMaxLocals;

                if (op != INVOKESTATIC) {
                    mMaxLocals++;
                }

                long pair = encodeAgentCheck(encoder, cp.addString(proxyName).mIndex,
                                             proxyDesc, rule.denyAction());
                maxStack = Math.max(maxStack, (int) pair);
                labelOffset = (int) (pair >> 32);

                if (op != INVOKESTATIC) {
                    encoder.writeByte(ALOAD_0);
                    firstSlot = 1;
                }

                pushed += (mMaxLocals - originalMaxLocals);
            }

            case PT_REFLECTION -> {
                pushed = 1;
                maxStack = 1;
                labelOffset = -1;

                // TODO: As an optimization, use a static final instance, captured by the
                // clinit method.
                encoder.writeByte(INVOKESTATIC);
                String agentName = SecurityAgent.CLASS_NAME;
                String desc = "()L" + Reflection.CLASS_NAME + ';';
                encoder.writeShort(cp.addMethodRef(agentName, "reflection", desc).mIndex);
            }
        }

        if (op == NEW) {
            maxStack = Math.max(maxStack, 2);
            encoder.writeByte(NEW);
            encoder.writeShort(methodRef.mClass.mIndex);
            encoder.writeByte(DUP);
            op = INVOKESPECIAL;
        }

        int nargs = proxyDesc.pushArgs(encoder, firstSlot);
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

        if (labelOffset <= 0) {
            encoder.writeShort(0); // attributes_count
        } else {
            encoder.writeShort(1); // attributes_count

            // Encode the StackMapTable attribute.
            encoder.writeShort(cp.addUTF8("StackMapTable").mIndex);
            encodeOneStackMapEntry(encoder, 2, labelOffset);
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
