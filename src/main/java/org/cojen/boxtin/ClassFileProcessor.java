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

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;

import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import java.lang.instrument.IllegalClassFormatException;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import java.util.function.Predicate;

import static java.lang.invoke.MethodHandleInfo.*;

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
    public static ClassFileProcessor begin(byte[] buffer) throws IllegalClassFormatException {
        try {
            return begin(new BasicDecoder(buffer));
        } catch (IllegalClassFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalClassFormatException(e.toString());
        }
    }

    private static ClassFileProcessor begin(BasicDecoder decoder)
        throws IOException, IllegalClassFormatException
    {
        if (decoder.readInt() != 0xCAFEBABE) {
            throw new IllegalClassFormatException();
        }

        int minor = decoder.readUnsignedShort();
        int major = decoder.readUnsignedShort();

        if (major < 51) { // require Java 7+
            throw new IllegalClassFormatException();
        }

        var cp = ConstantPool.decode(decoder);

        int accessFlagsOffset = decoder.offset();
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

        return new ClassFileProcessor(cp, accessFlagsOffset, thisClassIndex, decoder);
    }

    private final ConstantPool mConstantPool;
    private final int mAccessFlagsOffset; // offset immediately after the constant pool
    private final int mThisClassIndex;
    private final BasicDecoder mDecoder;
    private final int mMethodsOffset;
    private final int mMethodsCount;

    private final MemberRef mMemberRef;

    // Is set as a side-effect of calling prepareTransformation.
    private Map<byte[], Integer> mDenyHooks;

    private String mRethrowMethodName;

    // Is set as a side-effect of calling prepareTransformation.
    private BasicEncoder mMethodsEncoder;

    // Is set as a side-effect of calling the check method.
    // Maps method names to descriptors to byte code indexes to transform operations.
    private Map<String, Map<String, Map<Integer, Transform>>> mMethodsToTransform;

    // Is set as a side-effect of calling the check method. It's the offset immediately after
    // the methods.
    private int mAttributesOffset;

    private ClassFileProcessor(ConstantPool cp, int accessFlagsOffset,
                               int thisClassIndex, BasicDecoder decoder)
        throws IOException
    {
        mConstantPool = cp;
        mAccessFlagsOffset = accessFlagsOffset;
        mThisClassIndex = thisClassIndex;
        mDecoder = decoder;
        mMethodsOffset = decoder.offset();
        mMethodsCount = decoder.readUnsignedShort();
        mMemberRef = new MemberRef(cp.buffer());
    }

    /**
     * Checks all the methods and constant MethodHandles, and returns if true if no operations
     * were denied.
     */
    public boolean check(Checker checker) throws IOException, IllegalClassFormatException {
        final byte[] cpBuffer = mConstantPool.buffer();
        final BasicDecoder decoder = mDecoder;

        for (int i = mMethodsCount; --i >= 0; ) {
            int access_flags = decoder.readUnsignedShort();
            int name_index = decoder.readUnsignedShort();
            int descriptor_index = decoder.readUnsignedShort();

            // Look for the Code attribute.
            for (int j = decoder.readUnsignedShort(); --j >= 0; ) {
                int attrNameIndex = decoder.readUnsignedShort();
                long attrLength = decoder.readUnsignedInt();
                int cpOffset = mConstantPool.offsetOf(attrNameIndex);
                int tag = cpBuffer[cpOffset++];

                examine: {
                    // CONSTANT_Utf8, 4 bytes
                    if (tag == 1 && decodeUnsignedShortBE(cpBuffer, cpOffset) == 4) {
                        int name = decodeIntBE(cpBuffer, cpOffset + 2);
                        if (name == 0x436f6465) { // ASCII value for "Code"
                            break examine;
                        }
                    }
                    // Skip the attribute.
                    decoder.skipNBytes(attrLength);
                    continue;
                }

                decoder.skipNBytes(2 + 2); // skip max_stack and max_locals

                long codeLength = decoder.readUnsignedInt();
                checkCode(name_index, descriptor_index, checker, decoder.offset(), codeLength);

                decoder.skipNBytes(codeLength);

                // Skip exception_table.
                decoder.skipNBytes(decoder.readUnsignedShort() * (2 + 2 + 2 + 2));

                // Skip the attributes of the Code attribute.
                skipAttributes(decoder);
            }
        }

        // Stash the location of where the class attributes are defined.
        mAttributesOffset = decoder.offset();

        if (mMethodsToTransform != null) {
            // Define a trampoline method to throw an exception, as a bug workaround.
            prepareTransformation();
            addRethrowMethod();
        }

        // Now check the constant MethodHandles.

        mConstantPool.decodeMethodHandleRefs(mMemberRef, (kind, offset, memberRef) -> {
            Transform transform = switch (kind) {
                default -> throw new IllegalClassFormatException();

                case REF_newInvokeSpecial ->
                    checkConstructor(checker, memberRef);

                case REF_invokeStatic, REF_invokeSpecial ->
                    checkMethod(checker, memberRef);

                case REF_invokeVirtual, REF_invokeInterface ->
                    checkVirtualMethod(checker, memberRef);

                case REF_getField, REF_getStatic, REF_putField, REF_putStatic ->
                    checkField(checker, memberRef);
            };

            if (transform == null) {
                return;
            }

            // This point is reached if a transform is required.

            byte[] mutableBuffer = prepareTransformation();
            byte[] descriptor = memberRef.compatibleMethodDescriptor(kind);
            int referenceIndex = addDenyHookMethod(transform, descriptor);

            // Change the reference_kind and reference_index.
            mutableBuffer[offset - 1] = REF_invokeStatic;
            encodeShortBE(mutableBuffer, offset, referenceIndex);
        });

        return mMethodsToTransform == null && !mConstantPool.hasBeenExtended();
    }

    /**
     * Returns a new class file buffer if any operations were denied, or else the original
     * buffer if no operations were denied.
     *
     * @throws IllegalClassFormatException if the redefined class has too many constants or
     * methods
     */
    public byte[] redefine() throws IllegalClassFormatException {
        byte[] buffer = mConstantPool.buffer();

        if (mConstantPool.hasBeenExtended()) try {
            var encoder = new BasicEncoder(buffer.length + 1000);

            // Write the magic number and version.
            encoder.write(buffer, 0, 8);

            mConstantPool.writeTo(encoder);

            // Write access_flags, this_class, super_class, interfaces_count, interfaces,
            // fields_count, fields, but don't write the methods.
            encoder.write(buffer, mAccessFlagsOffset, mMethodsOffset - mAccessFlagsOffset);

            // Write methods_count.
            {
                int count = mMethodsCount + mDenyHooks.size();
                if (mRethrowMethodName != null) {
                    count++;
                }
                if (count > 65535) {
                    throw new IllegalClassFormatException("Too many methods");
                }
                encoder.writeShort(count);
            }

            // Write the original methods, which have been altered.
            {
                int offset = mMethodsOffset + 2;
                encoder.write(buffer, offset, mAttributesOffset - offset);
            }

            // Write the new methods.
            mMethodsEncoder.writeTo(encoder);

            // Write the trailing attributes.
            encoder.write(buffer, mAttributesOffset, buffer.length - mAttributesOffset);

            buffer = encoder.toByteArray();
        } catch (IOException e) {
            // Not expected.
            throw new UncheckedIOException(e);
        }

        if (mMethodsToTransform == null) {
            return buffer;
        }

        ClassFile cf = ClassFile.of();
        ClassModel model = cf.parse(buffer);

        class Transformer implements Predicate<MethodModel>, CodeTransform {
            private int mByteCodeIndex;
            private Map<Integer, Transform> mTransforms;

            @Override
            public boolean test(MethodModel mm) {
                Map<String, Map<Integer, Transform>> descMap =
                    mMethodsToTransform.get(mm.methodName().stringValue());
                return descMap != null &&
                    (mTransforms = descMap.get(mm.methodType().stringValue())) != null;
            }

            @Override
            public void atStart(CodeBuilder cb) {
                mByteCodeIndex = 0;
            }

            @Override
            public void accept(CodeBuilder cb, CodeElement ce) {
                if (!(ce instanceof Instruction i)) {
                    cb.with(ce);
                    return;
                }

                Transform transform = mTransforms.get(mByteCodeIndex);

                if (transform == null || transform.apply(model, cb)) {
                    cb.with(ce);
                }

                mByteCodeIndex += i.sizeInBytes();
            }
        }

        var transformer = new Transformer();
        ClassTransform ct = ClassTransform.transformingMethodBodies(transformer, transformer);

        return cf.transformClass(model, ct);
    }


    private abstract sealed class Transform {
        /**
         * @return true if the original CodeElement should be kept
         */
        abstract boolean apply(ClassModel model, CodeBuilder cb);

        /**
         * Generates code to throw an exception which is on the stack.
         */
        void throwException(ClassModel model, CodeBuilder cb) {
            // Ideally I'd just directly throw the exception at this point, but the classfile
            // API has a bug. When using the PATCH_DEAD_CODE option, the LocalVariableTable
            // isn't patched, and so it might have illegal entries. When using the
            // KEEP_DEAD_CODE option, the stack map table cannot be generated. This workaround
            // throws the exception using a trampoline method, and so it doesn't interfere with
            // code flow analysis.
            cb.invokestatic(model.thisClass().asSymbol(), mRethrowMethodName,
                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                              Throwable.class.describeConstable().get()));
        }
    }

    private final class Deny extends Transform {
        @Override
        boolean apply(ClassModel model, CodeBuilder cb) {
            var exDesc = ClassDesc.of("java.lang.SecurityException");
            cb.new_(exDesc);
            cb.dup();
            cb.invokespecial(exDesc, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
            throwException(model, cb);
            return true;
        }
    }

    private final class ClassNotFound extends Transform {
        private final String mName;

        ClassNotFound(String name) {
            if (name != null && name.isEmpty()) {
                name = null;
            }
            mName = name;
        }

        @Override
        boolean apply(ClassModel model, CodeBuilder cb) {
            var exDesc = ClassDesc.of("java.lang.NoClassDefFoundError");
            cb.new_(exDesc);
            cb.dup();
            MethodTypeDesc mtd;
            if (mName == null) {
                mtd = MethodTypeDesc.of(ConstantDescs.CD_void);
            } else {
                mtd = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);
                cb.ldc(mName);
            }
            cb.invokespecial(exDesc, "<init>", mtd);
            throwException(model, cb);
            return true;
        }
    }

    private void checkCode(final int name_index, final int descriptor_index,
                           final Checker checker, final int startOffset, final long length)
        throws IOException, IllegalClassFormatException
    {
        int offset = startOffset;

        final int endOffset;
        {
            long endL = offset + length;
            if (endL > Integer.MAX_VALUE) {
                throw new IllegalClassFormatException();
            }
            endOffset = (int) endL;
        }

        final byte[] buffer = mDecoder.buffer();
        final MemberRef memberRef = mMemberRef;

        while (offset < endOffset) {
            final int opOffset = offset;
            int op = buffer[offset++] & 0xff;

            Transform transform;

            switch (op) {
            default: throw new IllegalClassFormatException();

                // Operations which are checked...

            case 178: // GETSTATIC
            case 179: // PUTSTATIC
            case 180: // GETFIELD
            case 181: // PUTFIELD
                mConstantPool.decodeFieldRef(decodeUnsignedShortBE(buffer, offset), memberRef);
                offset += 2;
                transform = checkField(checker, memberRef);
                break;

            case 182: // INVOKEVIRTUAL
            case 183: // INVOKESPECIAL
            case 184: // INVOKESTATIC
                mConstantPool.decodeMethodRef(decodeUnsignedShortBE(buffer, offset), memberRef);
                offset += 2;
                if (memberRef.isConstructor()) {
                    transform = checkConstructor(checker, memberRef);
                } else if (op != 182) { // !INVOKEVIRTUAL
                    transform = checkMethod(checker, memberRef);
                } else {
                    transform = checkVirtualMethod(checker, memberRef);
                }
                break;

            case 185: // INVOKEINTERFACE
                mConstantPool.decodeMethodRef(decodeUnsignedShortBE(buffer, offset), memberRef);
                offset += 4;
                transform = checkVirtualMethod(checker, memberRef);
                break;

                // Unchecked operations with no operands...

            case 0: // NOP
            case 1: // ACONST_NULL
            case 2: // ICONST_M1
            case 3: // ICONST_0
            case 4: // ICONST_1
            case 5: // ICONST_2
            case 6: // ICONST_3
            case 7: // ICONST_4
            case 8: // ICONST_5
            case 9: // LCONST_0
            case 10: // LCONST_1
            case 11: // FCONST_0
            case 12: // FCONST_1
            case 13: // FCONST_2
            case 14: // DCONST_0
            case 15: // DCONST_1
            case 26: // ILOAD_0
            case 27: // ILOAD_1
            case 28: // ILOAD_2
            case 29: // ILOAD_3
            case 30: // LLOAD_0
            case 31: // LLOAD_1
            case 32: // LLOAD_2
            case 33: // LLOAD_3
            case 34: // FLOAD_0
            case 35: // FLOAD_1
            case 36: // FLOAD_2
            case 37: // FLOAD_3
            case 38: // DLOAD_0
            case 39: // DLOAD_1
            case 40: // DLOAD_2
            case 41: // DLOAD_3
            case 42: // ALOAD_0
            case 43: // ALOAD_1
            case 44: // ALOAD_2
            case 45: // ALOAD_3
            case 46: // IALOAD
            case 47: // LALOAD
            case 48: // FALOAD
            case 49: // DALOAD
            case 50: // AALOAD
            case 51: // BALOAD
            case 52: // CALOAD
            case 53: // SALOAD
            case 59: // ISTORE_0
            case 60: // ISTORE_1
            case 61: // ISTORE_2
            case 62: // ISTORE_3
            case 63: // LSTORE_0
            case 64: // LSTORE_1
            case 65: // LSTORE_2
            case 66: // LSTORE_3
            case 67: // FSTORE_0
            case 68: // FSTORE_1
            case 69: // FSTORE_2
            case 70: // FSTORE_3
            case 71: // DSTORE_0
            case 72: // DSTORE_1
            case 73: // DSTORE_2
            case 74: // DSTORE_3
            case 75: // ASTORE_0
            case 76: // ASTORE_1
            case 77: // ASTORE_2
            case 78: // ASTORE_3
            case 79: // IASTORE
            case 80: // LASTORE
            case 81: // FASTORE
            case 82: // DASTORE
            case 83: // AASTORE
            case 84: // BASTORE
            case 85: // CASTORE
            case 86: // SASTORE
            case 87: // POP
            case 88: // POP2
            case 89: // DUP
            case 90: // DUP_X1
            case 91: // DUP_X2
            case 92: // DUP2
            case 93: // DUP2_X1
            case 94: // DUP2_X2
            case 95: // SWAP
            case 96: // IADD
            case 97: // LADD
            case 98: // FADD
            case 99: // DADD
            case 100: // ISUB
            case 101: // LSUB
            case 102: // FSUB
            case 103: // DSUB
            case 104: // IMUL
            case 105: // LMUL
            case 106: // FMUL
            case 107: // DMUL
            case 108: // IDIV
            case 109: // LDIV
            case 110: // FDIV
            case 111: // DDIV
            case 112: // IREM
            case 113: // LREM
            case 114: // FREM
            case 115: // DREM
            case 116: // INEG
            case 117: // LNEG
            case 118: // FNEG
            case 119: // DNEG
            case 120: // ISHL
            case 121: // LSHL
            case 122: // ISHR
            case 123: // LSHR
            case 124: // IUSHR
            case 125: // LUSHR
            case 126: // IAND
            case 127: // LAND
            case 128: // IOR
            case 129: // LOR
            case 130: // IXOR
            case 131: // LXOR
            case 133: // I2L
            case 134: // I2F
            case 135: // I2D
            case 136: // L2I
            case 137: // L2F
            case 138: // L2D
            case 139: // F2I
            case 140: // F2L
            case 141: // F2D
            case 142: // D2I
            case 143: // D2L
            case 144: // D2F
            case 145: // I2B
            case 146: // I2C
            case 147: // I2S
            case 148: // LCMP
            case 149: // FCMPL
            case 150: // FCMPG
            case 151: // DCMPL
            case 152: // DCMPG
            case 172: // IRETURN
            case 173: // LRETURN
            case 174: // FRETURN
            case 175: // DRETURN
            case 176: // ARETURN
            case 177: // RETURN
            case 190: // ARRAYLENGTH
            case 191: // ATHROW
            case 194: // MONITORENTER
            case 195: // MONITOREXIT
            case 202: // BREAKPOINT
                continue;

                // Unchecked operations with one operand byte...

            case 16: // BIPUSH
            case 18: // LDC
            case 21: // ILOAD
            case 22: // LLOAD
            case 23: // FLOAD
            case 24: // DLOAD
            case 25: // ALOAD
            case 54: // ISTORE
            case 55: // LSTORE
            case 56: // FSTORE
            case 57: // DSTORE
            case 58: // ASTORE
            case 169: // RET
            case 188: // NEWARRAY
                offset++;
                continue;

                // Unchecked operations with two operand bytes...

            case 17: // SIPUSH
            case 19: // LDC_W
            case 20: // LDC2_W
            case 132: // IINC
            case 153: // IFEQ
            case 154: // IFNE
            case 155: // IFLT
            case 156: // IFGE
            case 157: // IFGT
            case 158: // IFLE
            case 159: // IF_ICMPEQ
            case 160: // IF_ICMPNE
            case 161: // IF_ICMPLT
            case 162: // IF_ICMPGE
            case 163: // IF_ICMPGT
            case 164: // IF_ICMPLE
            case 165: // IF_ACMPEQ
            case 166: // IF_ACMPNE
            case 167: // GOTO
            case 168: // JSR
            case 187: // NEW
            case 189: // ANEWARRAY
            case 192: // CHECKCAST
            case 193: // INSTANCEOF
            case 198: // IFNULL
            case 199: // IFNONNULL
                offset += 2;
                continue;

                // Unchecked operations with three operand bytes...

            case 197: // MULTIANEWARRAY
                offset += 3;
                continue;

                // Unchecked operations with four operand bytes...

            case 186: // INVOKEDYNAMIC
            case 200: // GOTO_W
            case 201: // JSR_W
                offset += 4;
                continue;

                // Unchecked operation with three or five operand bytes.

            case 196: // WIDE
                op = buffer[++offset];
                offset += 2;
                if (op == 132) { // IINC
                    offset += 2;
                }
                continue;

                // Unchecked operations with a complex amount of operand bytes.

            case 170: // TABLESWITCH
                offset += pad(offset - startOffset);
                offset += 4; // skip the default jump target
                int lowValue = decodeIntBE(buffer, offset); offset += 4;
                int highValue = decodeIntBE(buffer, offset); offset += 4;
                // skip the jump targets
                offset = skip(offset, ((long) highValue - (long) lowValue + 1L) * 4L);
                continue;

            case 171: // LOOKUPSWITCH
                offset += pad(offset - startOffset);
                offset += 4; // skip the default jump target
                // skip the value and jump target pairs
                offset = skip(offset, decodeIntBE(buffer, offset) * 8L);
                continue;
            }

            if (transform == null) {
                continue;
            }

            // This point is reached if a transform is required.

            String methodName, descriptor;

            final int originalOffset = mDecoder.offset();
            try {
                mDecoder.offset(mConstantPool.offsetOf(name_index, 1)); // CONSTANT_Utf8
                methodName = mDecoder.readUTF();
                mDecoder.offset(mConstantPool.offsetOf(descriptor_index, 1)); // CONSTANT_Utf8
                descriptor = mDecoder.readUTF();
            } finally {
                mDecoder.offset(originalOffset);
            }

            if (mMethodsToTransform == null) {
                mMethodsToTransform = new HashMap<>();
            }

            mMethodsToTransform
                .computeIfAbsent(methodName, k -> new HashMap<>())
                .computeIfAbsent(descriptor, k -> new HashMap<>())
                .put(opOffset - startOffset, transform);
        }
    }

    /*
      Note regarding the ClassNotFoundException behavor:

      If a dependent class isn't found, then it might be assumed that upon being loaded the
      class being checked will throw a NoClassDefFoundError. This is risky, because the
      ClassLoader implementation might not consistently throw a ClassNotFoundException for a
      given class name. For this reason, a ClassNotFoundException during checking is treated as
      a denial which throws a NoClassDefFoundError.
     */

    /**
     * @return null if allowed
     */
    private Transform checkConstructor(Checker checker, MemberRef ctorRef) {
        try {
            if (checker.isConstructorAllowed(ctorRef)) {
                return null;
            }
        } catch (ClassNotFoundException e) {
            return new ClassNotFound(e.getMessage());
        }

        return new Deny();
    }

    /**
     * @return null if allowed
     */
    private Transform checkMethod(Checker checker, MemberRef methodRef) {
        try {
            if (checker.isMethodAllowed(methodRef)) {
                return null;
            }
        } catch (ClassNotFoundException e) {
            return new ClassNotFound(e.getMessage());
        }

        return new Deny();
    }

    /**
     * @return null if allowed
     */
    private Transform checkVirtualMethod(Checker checker, MemberRef methodRef) {
        try {
            if (checker.isVirtualMethodAllowed(methodRef)) {
                return null;
            }
        } catch (ClassNotFoundException e) {
            return new ClassNotFound(e.getMessage());
        }

        return new Deny();
    }

    /**
     * @return null if allowed
     */
    private Transform checkField(Checker checker, MemberRef fieldRef) {
        try {
            if (checker.isFieldAllowed(fieldRef)) {
                return null;
            }
        } catch (ClassNotFoundException e) {
            return new ClassNotFound(e.getMessage());
        }

        return new Deny();
    }

    /**
     * @return a mutable class file buffer
     */
    private byte[] prepareTransformation() throws IllegalClassFormatException {
        byte[] buffer = mConstantPool.extend();
        if (mDenyHooks == null) {
            mDenyHooks = new TreeMap<>(Arrays::compare);
            mMethodsEncoder = new BasicEncoder(100);
        }
        return buffer;
    }

    /**
     * @return CONSTANT_Methodref index
     */
    private int addDenyHookMethod(Transform transform, byte[] descriptor) {
        byte prefix;
        byte[] key;

        switch (transform) {
            case Deny _ -> {
                prefix = (byte) 'd';
                key = descriptor;
            }

            case ClassNotFound c -> {
                prefix = (byte) 'c';
                try {
                    BasicEncoder encoder = UTFEncoder.localEncoder();
                    encoder.writeUTF(c.mName == null ? "" : c.mName);
                    encoder.write(descriptor);
                    key = encoder.toByteArray();
                } catch (IOException e) {
                    // Not expected.
                    throw new UncheckedIOException(e);
                }
            }
        };

        return mDenyHooks.computeIfAbsent(key, _ -> {
            try {
                return doAddDenyHookMethod(transform, mConstantPool.addUniqueMethod
                                           ((byte) prefix, mThisClassIndex, descriptor, null));
            } catch (IOException e) {
                // Not expected.
                throw new UncheckedIOException(e);
            }
        });
    }

    private int doAddDenyHookMethod(Transform transform, ConstantPool.C_MemberRef ref)
        throws IOException
    {
        BasicEncoder encoder = mMethodsEncoder;

        // method_info
        encoder.writeShort(0x02 | 0x08 | 0x1000); // access_flags: private static synthetic
        encoder.writeShort(ref.mNameAndType.mName.mIndex);
        encoder.writeShort(ref.mNameAndType.mTypeDesc.mIndex);
        encoder.writeShort(1); // attributes_count

        Class<?> exClass;
        String desc, message;

        switch (transform) {
            case Deny _ -> {
                exClass = SecurityException.class;
                desc = null;
                message = null;
            }

            case ClassNotFound c -> {
                exClass = NoClassDefFoundError.class;
                message = c.mName;
                desc = message == null ? null : '(' + String.class.descriptorString() + ')' + 'V';
            }
        };

        // Code_attribute
        encoder.writeShort(mConstantPool.codeStrIndex());
        encoder.writeInt(message == null ? 20 : 23); // attribute_length
        encoder.writeShort(message == null ? 2 : 3); // max_stack
        encoder.writeShort(ref.argCount());          // max_locals
        encoder.writeInt(message == null ? 8 : 11);  // code_length
        encoder.writeByte(187); // NEW
        ConstantPool.C_Class exConstant = mConstantPool.addClass(exClass);
        encoder.writeShort(exConstant.mIndex);
        encoder.writeByte(89);  // DUP
        if (message != null) {
            encoder.writeByte(19); // LDC_W
            encoder.writeShort(mConstantPool.strIndex(message));
        }
        encoder.writeByte(183); // INVOKESPECIAL
        encoder.writeShort(mConstantPool.ctorInitStr(exConstant, desc).mIndex);
        encoder.writeByte(191); // ATHROW
        encoder.writeShort(0);  // exception_table_length
        encoder.writeShort(0);  // attributes_count

        return ref.mIndex;
    }

    /**
     * Note: does nothing if the method has already been added
     */
    private void addRethrowMethod() {
        if (mRethrowMethodName != null) {
            return;
        }

        byte[] desc = UTFEncoder.encode("" + '(' + Throwable.class.descriptorString() + ')' + 'V');

        try {
            var nameRef = new String[1];
            doAddRethrowMethod(mConstantPool.addUniqueMethod
                               ((byte) 'x', mThisClassIndex, desc, nameRef));
            mRethrowMethodName = nameRef[0];
        } catch (IOException e) {
            // Not expected.
            throw new UncheckedIOException(e);
        }
    }

    private void doAddRethrowMethod(ConstantPool.C_MemberRef ref) throws IOException {
        BasicEncoder encoder = mMethodsEncoder;

        // method_info
        encoder.writeShort(0x02 | 0x08 | 0x1000); // access_flags: private static synthetic
        encoder.writeShort(ref.mNameAndType.mName.mIndex);
        encoder.writeShort(ref.mNameAndType.mTypeDesc.mIndex);
        encoder.writeShort(1); // attributes_count

        // Code_attribute
        encoder.writeShort(mConstantPool.codeStrIndex());
        encoder.writeInt(14);   // attribute_length
        encoder.writeShort(1);  // max_stack
        encoder.writeShort(1);  // max_locals
        encoder.writeInt(2);    // code_length
        encoder.writeByte(42);  // ALOAD_0
        encoder.writeByte(191); // ATHROW
        encoder.writeShort(0);  // exception_table_length
        encoder.writeShort(0);  // attributes_count
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
    private static int skip(int offset, long amount) throws IllegalClassFormatException {
        long updated = offset + amount;
        if (updated < offset || updated > Integer.MAX_VALUE) {
            throw new IllegalClassFormatException();
        }
        return (int) updated;
    }

    private static void skipAttributes(BasicDecoder decoder) throws IOException {
        for (int i = decoder.readUnsignedShort(); --i >= 0;) {
            decoder.readUnsignedShort(); // attribute_name_index
            decoder.skipNBytes(decoder.readUnsignedInt());
        }
    }
}
