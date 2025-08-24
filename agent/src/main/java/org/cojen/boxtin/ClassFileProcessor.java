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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

import java.lang.reflect.Modifier;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import java.util.function.Predicate;

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
        try {
            if (decoder.readInt() != 0xCAFEBABE) {
                // Can be ignored because the class cannot be loaded anyhow.
                throw new ClassFormatException(true);
            }

            int minor = decoder.readUnsignedShort();
            int major = decoder.readUnsignedShort();

            // Require Java 1.4+ for rejecting overlong UTF-8 strings, require Java 5+ for
            // supporting ldc of classes, require Java 6+ for the StackMapTable attribute, and
            // require Java 7+ for supporting CONSTANT_MethodHandle.
            if (major < 51) {
                throw new ClassFormatException("Unsupported class version: " + major);
            }
        } catch (EOFException e) {
            // Can be ignored because the class cannot be loaded anyhow.
            throw new ClassFormatException(true);
        }

        return new ClassFileProcessor(decoder);
    }

    private final ConstantPool mConstantPool;
    private final int mThisClassIndex;
    private final int mSuperClassOffset, mSuperClassIndex;
    private final int[] mInterfaceIndexes;
    private final BufferDecoder mDecoder;
    private final int mMethodsStartOffset;
    private final int mMethodsCount;

    private Module mModule;
    private Rules mRules;

    private MethodMap mDeclaredMethods;

    private DenyAction.Exception mDenyConstruction;

    private TreeMap<Integer, RegionReplacement> mReplacements;

    private Map<Integer, ProxyMethod> mProxyMethods;

    // Keys are original code offsets.
    private Map<Integer, ProxyMethod> mReplacedMethodHandles;

    // Work objects used by the rulesForClass method.
    private ConstantPool.C_UTF8 mPackageName, mClassName;

    private ClassFileProcessor(BufferDecoder decoder) throws IOException {
        mConstantPool = ConstantPool.decode(decoder);

        int accessFlags = decoder.readUnsignedShort();
        mThisClassIndex = decoder.readUnsignedShort();

        mSuperClassOffset = decoder.offset();
        mSuperClassIndex = decoder.readUnsignedShort();

        {
            int numIfaces = decoder.readUnsignedShort();
            if (numIfaces == 0) {
                mInterfaceIndexes = null;
            } else {
                mInterfaceIndexes = new int[numIfaces];
                for (int i=0; i<numIfaces; i++) {
                    mInterfaceIndexes[i] = decoder.readUnsignedShort();
                }
            }
        }

        mDecoder = decoder;

        int fieldsCount = decoder.readUnsignedShort();

        // Skip the fields.
        for (int i=0; i<fieldsCount; i++) {
            // Skip access_flags, name_index, and descriptor_index.
            decoder.skipNBytes(2 + 2 + 2);
            decoder.skipAttributes();
        }

        mMethodsStartOffset = decoder.offset();
        mMethodsCount = decoder.readUnsignedShort();
    }

    /**
     * Transforms the class or else returns null when no transformation is necessary.
     *
     * @param module the module which contains the class being transformed
     */
    public byte[] transform(Module module, Rules rules) throws IOException, ClassFormatException {
        return check(module, rules) ? redefine() : null;
    }

    /**
     * Examines the class to see if any constructors or methods need to be modified for
     * supporting runtime checks, and begins making modifications if so.
     *
     * @param module the module which contains the class being transformed
     * @return true if the class requires modification
     */
    private boolean check(Module module, Rules rules) throws IOException, ClassFormatException {
        mModule = module;
        mRules = rules;

        final ConstantPool cp = mConstantPool;
        final BufferDecoder decoder = mDecoder;

        // Gather all the method declarations, to be used to detect if a method call is local.

        mDeclaredMethods = new MethodMap(mMethodsCount);

        final int methodsOffset = decoder.offset();

        for (int i = mMethodsCount; --i >= 0; ) {
            int access_flags = decoder.readUnsignedShort();
            ConstantPool.C_UTF8 name = cp.findConstantUTF8(decoder.readUnsignedShort());
            ConstantPool.C_UTF8 desc = cp.findConstantUTF8(decoder.readUnsignedShort());
            mDeclaredMethods.put(name, desc);
            decoder.skipAttributes();
        }

        // Restore the offset for checking the methods again later.
        decoder.offset(methodsOffset);

        // Check if any super interfaces restrict subtyping.

        if (mInterfaceIndexes != null) {
            for (int i=0; i<mInterfaceIndexes.length; i++) {
                Rules.ForClass forClass = rulesForClass(cp.findConstantClass(mInterfaceIndexes[i]));
                if (forClass.isConstructionDenied()) {
                    // Denying construction denies access to the inherited instance methods.
                    DenyAction denyAction = forClass.ruleForConstructor("()").denyAction();
                    if (denyAction instanceof DenyAction.Exception ex) {
                        mDenyConstruction = ex;
                    } else {
                        mDenyConstruction = DenyAction.Standard.THE;
                    }
                    break;
                }
            }
        }

        // Check if any superclasses restrict subtyping.

        if (mSuperClassIndex != 0) {
            C_Class superClass = cp.findConstantClass(mSuperClassIndex);
            Rules.ForClass forClass = rulesForClass(superClass);

            if (forClass.isConstructionDenied()) {
                // No need to explicitly deny because the super class constructors will.
                mDenyConstruction = null;

                // Access to the inherited static methods must be explicitly denied.

                Map<String, Module> packageToModule =
                    PackageToModule.packageMapFor(rules.moduleLayer());

                ClassInfo superInfo = ClassInfo.find(superClass.mValue.str(), packageToModule);

                superInfo.forAllStaticMethods(packageToModule, entry -> {
                    String name = entry.getKey();
                    String desc = entry.getValue().fullDescriptor();

                    C_NameAndType nat = cp.addNameAndType(name, desc);

                    if (mDeclaredMethods.find(nat) != null) {
                        // Is declared locally, so nothing to do.
                        return true;
                    }

                    DenyAction action = forClass.ruleForMethod(name, desc).denyAction();

                    if (action == null) {
                        // Not expected.
                        action = DenyAction.standard();
                    }

                    C_MemberRef superMethod = cp.addMethodRef(superClass, nat);

                    int accessFlags = entry.getValue().accessFlags() | 0x1000; // | synthetic

                    try {
                        addProxyMethod(INVOKESTATIC, superMethod, action, accessFlags, name);
                    } catch (IOException e) {
                        throw Utils.rethrow(e);
                    }

                    return true;
                });
            }
        }

        // Check the MethodHandle constants.

        cp.visitMethodHandleRefs((kind, offset, methodRef) -> {
            Rule rule;
            byte op;

            if (kind == REF_newInvokeSpecial) {
                rule = ruleForConstructor(methodRef);
                if (rule.isAllowed()) {
                    return;
                }
                op = NEW;
            } else {
                rule = ruleForMethod(methodRef);
                if (rule.isAllowed()) {
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

            ProxyMethod proxyMethod = addProxyMethod(op, methodRef, rule.denyAction());

            if (mReplacedMethodHandles == null) {
                mReplacedMethodHandles = new HashMap<>();
            }

            mReplacedMethodHandles.put(offset, proxyMethod);
        });

        // Check the methods.

        forAllMethods(null, this::insertCallerChecks);

        int methodsEndOffset = decoder.offset();

        if (mProxyMethods != null) {
            // Append the new methods by "replacing" what is logically an empty method just
            // after the method table.
            storeReplacement(methodsEndOffset, new CompositeReplacement(mProxyMethods.values()));
        }

        return mReplacements != null;
    }

    private void storeReplacement(int originalOffset, RegionReplacement replacement) {
        if (replacement != null) {
            if (mReplacements == null) {
                mReplacements = new TreeMap<>();
            }
            mReplacements.put(originalOffset, replacement);
        }
    }

    /**
     * Transforms the methods the MethodHandles.Lookup class which define hidden classes such
     * that the new hidden class has SecurityAgent transforms applied to it.
     */
    public byte[] transformHiddenClassCreation() throws IOException, ClassFormatException {
        forAllMethods(name -> name.equals("defineHiddenClass") ||
                      name.equals("defineHiddenClassWithClassData"),
                      this::insertHiddenClassTransform);

        return redefine();
    }

    private void insertHiddenClassTransform(CodeAttr method)
        throws IOException, ClassFormatException
    {
        ConstantPool cp = mConstantPool;

        // Require 6 bytes to encode the transform operation.
        method.prepareForModification(cp, mThisClassIndex, cp.buffer(), 6, 0);

        C_Class agentClass = cp.addClass(SecurityAgent.class);
        C_MemberRef ref = cp.addMethodRef(agentClass, "transformHiddenClass",
                                          "(Ljava/lang/invoke/MethodHandles$Lookup;[B)[B");

        byte[] buffer = method.codeEncoder.buffer();

        int offset = 0;
        buffer[offset++] = ALOAD_0;
        buffer[offset++] = ALOAD_1;
        buffer[offset++] = INVOKESTATIC;
        encodeShortBE(buffer, offset, ref.mIndex); offset += 2;
        buffer[offset++] = ASTORE_1;
        method.stackPushPop(2);

        storeReplacement(method.attrOffset, method);
    }

    private static interface MethodConsumer {
        void accept(CodeAttr method) throws IOException;
    }

    /**
     * When called, the decoder must be at the first method_info offset.
     *
     * @param nameFilter if provided, return true if the name is accepted
     */
    private void forAllMethods(Predicate<ConstantPool.C_UTF8> nameFilter,
                               MethodConsumer methodConsumer)
        throws IOException
    {
        final BufferDecoder decoder = mDecoder;
        final ConstantPool cp = mConstantPool;
        final byte[] cpBuffer = cp.buffer();

        for (int i = mMethodsCount; --i >= 0; ) {
            int methodInfoOffset = decoder.offset();

            int accessFlags = decoder.readUnsignedShort();
            int nameIndex = decoder.readUnsignedShort();
            int descIndex = decoder.readUnsignedShort();

            if (nameFilter != null && !nameFilter.test(cp.findConstantUTF8(nameIndex))) {
                decoder.skipAttributes();
                continue;
            }

            // Only attempt to modify methods which have a code attribute.

            for (int j = decoder.readUnsignedShort(); --j >= 0; ) {
                int attrNameIndex = decoder.readUnsignedShort();
                int attrOffset = decoder.offset();
                long attrLength = decoder.readUnsignedInt();
                int cpOffset = cp.findConstantOffset(attrNameIndex);
                int tag = cpBuffer[cpOffset++];

                examine: {
                    // CONSTANT_Utf8, 4 bytes
                    if (tag == 1 && decodeUnsignedShortBE(cpBuffer, cpOffset) == 4) {
                        int attrName = decodeIntBE(cpBuffer, cpOffset + 2);
                        if (attrName == 0x436f6465) { // ASCII value for "Code"
                            break examine;
                        }
                    }
                    // Skip the non-code attribute.
                    decoder.skipNBytes(attrLength);
                    continue;
                }

                // Prepare to modify the method.

                var method = new CodeAttr();

                method.attrOffset = attrOffset;
                method.attrLength = attrLength + 4; // +4 for the attribute_length field itself

                method.accessFlags = accessFlags;
                method.nameIndex = nameIndex;
                method.descIndex = descIndex;

                method.maxStack = decoder.readUnsignedShort();
                method.maxLocals = decoder.readUnsignedShort();

                long code_length = decoder.readUnsignedInt();
                if (code_length < 0 || code_length > Integer.MAX_VALUE) {
                    throw new ClassFormatException();
                }
                method.codeLength = (int) code_length;

                method.codeOffset = decoder.offset();

                // Skip past all the method attributes.
                decoder.offset(methodInfoOffset + (2 + 2 + 2));
                decoder.skipAttributes();

                methodConsumer.accept(method);

                break;
            }
        }
    }

    /**
     * Finishes modifications and returns a new class file buffer.
     *
     * @return null if no modifications were made
     * @throws ClassFormatException if the redefined class has too many constants or methods
     */
    private byte[] redefine() throws IOException, ClassFormatException {
        if (!mConstantPool.hasBeenExtended() && mReplacements == null) {
            return null;
        }

        BufferDecoder decoder = mDecoder;
        long capacity = decoder.buffer().length;

        if (mReplacements != null) {
            for (RegionReplacement r : mReplacements.values()) {
                long newLength = r.finish(mConstantPool, decoder.buffer());
                capacity += newLength - r.originalLength();
            }
        }

        long cpGrowth = mConstantPool.growth();
        capacity += cpGrowth;

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
            for (Map.Entry<Integer, RegionReplacement> e : mReplacements.entrySet()) {
                int repOffset = e.getKey();
                RegionReplacement rep = e.getValue();

                int offset = decoder.offset();
                if (offset < repOffset) {
                    decoder.transferTo(encoder, repOffset - offset);
                } else if (offset > repOffset) {
                    throw new AssertionError();
                }

                decoder.skipNBytes(rep.originalLength());
                rep.writeTo(encoder);
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

        if (mProxyMethods != null) {
            // Update the methods_count field.
            int numMethods = mMethodsCount + mProxyMethods.size();
            if (numMethods >= 65536 || numMethods < 0) {
                throw new ClassFormatException();
            }
            int methodsStartOffset = mMethodsStartOffset + (int) cpGrowth;
            encodeShortBE(buffer, methodsStartOffset, numMethods);
        }

        if (mReplacedMethodHandles != null) {
            // Update the MethodHandle constants, changing reference_kind and reference_index.
            for (Map.Entry<Integer, ProxyMethod> e : mReplacedMethodHandles.entrySet()) {
                int offset = e.getKey();
                buffer[offset - 1] = REF_invokeStatic;
                encodeShortBE(buffer, offset, e.getValue().mMethodRef.mIndex);
            }
        }

        if (SecurityAgent.DEBUG) {
            DebugWriter.write(mConstantPool, mThisClassIndex, buffer);
        }

        return buffer;
    }

    /**
     * Inserts caller-side checks into the bytecode if necessary. Affected code sections
     * are modified such that a check is inserted:
     *
     * private void someMethod() throws IOException {
     *     ...
     *
     *     // new deny code
     *     if (thisClass.getModule() != File.class.getModule()) {
     *         throw new SecurityException();
     *     }
     *
     *     // original code
     *     File f = File.open(path);
     *     ...
     * }
     *
     * The reason for the runtime check is that calls within the same module are always
     * allowed. The Module instances aren't available until the classes are loaded, and they
     * cannot be loaded yet.
     *
     * Inserting code is complicated because it requires that jump offsets be modified as
     * well. To simplify things, the deny code is actually inserted at the end, like so:
     *
     * private void someMethod() throws IOException {
     *     ...
     *
     *     load 'path'
     *     // original invocation was here
     *     goto check1;
     *     resume1:
     *     File f = store
     *     ...
     *
     *     check1:
     *     if (thisClass.getModule() != File.class.getModule()) {
     *         throw new SecurityException();
     *     }
     *     invoke File.open
     *     goto resume1;
     *
     *     // more checks might follow
     * }
     *
     * Most invocation operations occupy three bytes, and so the GOTO operation is limited to a
     * short jump, and so methods which exceed 32768 bytes in length might not be modifiable. A
     * ClassFormatException is thrown instead. With INVOKEINTERFACE, five bytes are available,
     * and so a GOTO_W operation is used instead.
     */
    private void insertCallerChecks(CodeAttr caller) throws IOException, ClassFormatException {
        final ConstantPool cp = mConstantPool;

        if (mDenyConstruction != null && cp.findConstantUTF8(caller.nameIndex).isConstructor()) {
            // Just throw an exception.
            var ctor = new ReplacedMethod(caller);
            ctor.prepareForModification(cp, mThisClassIndex, null);
            encodeExceptionAction(ctor, mDenyConstruction);
            storeReplacement(ctor.attrOffset, ctor);
            return;
        }

        int offset = caller.codeOffset;

        final int endOffset;
        {
            long endL = (long) offset + (long) caller.codeLength;
            if (endL > Integer.MAX_VALUE) {
                throw new ClassFormatException();
            }
            endOffset = (int) endL;
        }

        final byte[] buffer = cp.buffer();

        while (offset < endOffset) {
            final int opOffset = offset;
            byte op = buffer[offset++];

            C_MemberRef methodRef;
            Rule rule;
            boolean hasInstance, maybeNull;

            switch (op) {
                default -> {
                    throw new ClassFormatException();
                }

                // Operations which are checked...

                case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE -> {
                    int methodRefIndex = decodeUnsignedShortBE(buffer, offset);
                    methodRef = cp.findConstant(methodRefIndex, C_MemberRef.class);
                    offset += op != INVOKEINTERFACE ? 2 : 4;

                    if (methodRef.mNameAndType.mName.isConstructor()) {
                        rule = ruleForConstructor(methodRef);
                        hasInstance = op == INVOKEVIRTUAL || op == INVOKEINTERFACE;
                        maybeNull = false;
                    } else {
                        rule = ruleForMethod(methodRef);
                        hasInstance = hasInstance(op);
                        maybeNull = true;
                    }

                    if (rule.isAllowed()) {
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
                    offset += switchPad(offset - caller.codeOffset);
                    offset += 4; // skip the default jump target
                    long lowValue = decodeIntBE(buffer, offset); offset += 4;
                    long highValue = decodeIntBE(buffer, offset); offset += 4;
                    // skip the jump targets
                    offset = Math.toIntExact(offset + (highValue - lowValue + 1L) * 4L);
                    continue;
                }

                case LOOKUPSWITCH -> {
                    offset += switchPad(offset - caller.codeOffset);
                    offset += 4; // skip the default jump target
                    // skip the value and jump target pairs
                    int npairs = decodeIntBE(buffer, offset); offset += 4;
                    offset = Math.toIntExact(offset + npairs * 8L);
                    continue;
                }
            } // end switch

            // This point is reached if the code needs to be modified.

            if (caller.codeEncoder == null) {
                caller.prepareForModification(cp, mThisClassIndex, buffer);
            }

            int codeOffset = caller.codeOffset;
            int insertion = caller.insertion;

            // Address of the op being replaced.
            int opAddress = opOffset - codeOffset + insertion;
            // Address after the op being replaced.
            int resumeAddress = offset - codeOffset + insertion;

            // Insert an entry at the operation being replaced, which is then moved or copied
            // to the address of the deny code.
            boolean inserted = caller.smt.insertEntry
                (cp, mThisClassIndex, opAddress, insertion, buffer, codeOffset);

            // The deny code will jump back to the address immediately after the operation
            // being replaced, so a stack map table entry is needed for it.
            caller.smt.insertEntry
                (cp, mThisClassIndex, resumeAddress, insertion, buffer, codeOffset);

            BufferEncoder encoder = caller.codeEncoder;
            byte[] encoderBuffer = encoder.buffer();

            // Replace the original operation to instead branch to the deny code to be added...

            int branchDelta = encoder.length() - opAddress;
            int opSize = resumeAddress - opAddress;

            switch (opSize) {
                default -> {
                    throw new AssertionError();
                }
                case 3 -> {
                    if (branchDelta > 32767) {
                        throw new ClassFormatException("Method is too large");
                    }
                    encoderBuffer[opAddress] = GOTO;
                    encodeShortBE(encoderBuffer, opAddress + 1, branchDelta);
                }
                case 5 -> {
                    encoderBuffer[opAddress] = GOTO_W;
                    encodeIntBE(encoderBuffer, opAddress + 1, branchDelta);
                }
            }

            // Need a StackMapTable entry at the address of the deny check code.
            StackMapTable.Entry denyEntry;
            if (inserted) {
                denyEntry = caller.smt.removeEntry(opAddress);
            } else {
                denyEntry = caller.smt.getEntry(opAddress);
            }
            caller.smt.putEntry(encoder.length(), denyEntry);

            caller.stackReset(denyEntry.stackSize());

            int checkStartAddress = encoder.length();

            CodeAttr.StoredArgs args = caller.storeArgs(encoder, denyEntry, hasInstance, methodRef);

            encodeDenyAction(caller, hasInstance, maybeNull, methodRef, methodRef.mClass,
                             rule.denyAction(), resumeAddress,
                             args.argSlots(), 0, args.withArgs(), null);

            caller.loadArgs(encoder, hasInstance, methodRef, args.argSlots());

            // Copy the original invoke operation.
            encoder.write(buffer, opOffset, opSize);

            // Branch back to the resumeAddress.
            encodeBranch(encoder, resumeAddress);

            caller.replaced(opAddress, checkStartAddress, encoder.length());

            storeReplacement(caller.attrOffset, caller);
        }
    }

    /**
     * Return a rule from the calling side, to a target.
     *
     * @param methodRef target constructor
     */
    private Rule ruleForConstructor(C_MemberRef methodRef) {
        // Note: Any changes to this method might need to be reflected in the
        // SecurityAgent.isAllowed2 method.

        if (isInvokingLocalMethod(methodRef)) {
            return Rule.allow();
        }

        return rulesForClass(methodRef.mClass).ruleForConstructor(methodRef.mNameAndType.mTypeDesc);
    }

    /**
     * Return a rule from the calling side, to a target.
     *
     * @param methodRef target method
     */
    private Rule ruleForMethod(C_MemberRef methodRef) {
        // Note: Any changes to this method might need to be reflected in the
        // SecurityAgent.isAllowed2 method.

        if (isInvokingLocalMethod(methodRef)) {
            return Rule.allow();
        }

        if (methodRef.mClass.mValue.isArray()) {
            return Rule.allow();
        }

        C_NameAndType nat = methodRef.mNameAndType;
        Rule rule = rulesForClass(methodRef.mClass).ruleForMethod(nat.mName, nat.mTypeDesc);

        Map<String, DenyAction> denials = mRules.denialsForMethod(nat.mName, nat.mTypeDesc);

        if (denials.isEmpty() || denials.containsKey(methodRef.mClass.mValue)) {
            return rule;
        }

        return Rule.deny(new DenyAction.Multi(denials));
    }

    /**
     * Returns ForClass rules from the calling side, to a target.
     *
     * @param target target class
     */
    private Rules.ForClass rulesForClass(C_Class target) {
        // Note: Any changes to this method might need to be reflected in the
        // SecurityAgent.isAllowed2 method.

        ConstantPool.C_UTF8 packageName = mPackageName;
        ConstantPool.C_UTF8 className = mClassName;

        if (packageName == null) {
            mPackageName = packageName = mConstantPool.new C_UTF8();
            mClassName = className = mConstantPool.new C_UTF8();
        }

        target.split(packageName, className);

        if (packageName.equals("org/cojen/boxtin") && className.equals("SecurityAgent")) {
            return Rule.deny();
        }

        return mRules.forClass(mModule, packageName, className);
    }

    private boolean isInvokingLocalMethod(C_MemberRef methodRef) {
        return isInvokingThisClass(methodRef) && mDeclaredMethods.find(methodRef) != null;
    }

    private boolean isInvokingThisClass(C_MemberRef methodRef) {
        int thisIndex = mThisClassIndex;
        int otherIndex = methodRef.mClass.mIndex;
        return (thisIndex == otherIndex ||
            mConstantPool.findConstant(thisIndex).equals(mConstantPool.findConstant(otherIndex)));
    }

    private static void encodeBranch(BufferEncoder encoder, int address) throws IOException {
        int branchDelta = address - encoder.length();
        if (((short) branchDelta) == branchDelta) {
            encoder.write(GOTO);
            encoder.writeShort(branchDelta);
        } else {
            encoder.write(GOTO_W);
            encoder.writeInt(branchDelta);
        }
    }

    /**
     * @param returnOp must be a return operation
     */
    private static void encodeBranchOrReturn(CodeAttr method, int address, byte returnOp)
        throws IOException
    {
        if (address >= 0) {
            encodeBranch(method.codeEncoder, address);
        } else {
            method.codeEncoder.write(returnOp);
            method.stackPop(popSizeForReturnOp(returnOp));
        }
    }

    private static void encodeReturnPop(BufferEncoder encoder, byte returnOp) throws IOException {
        int size = popSizeForReturnOp(returnOp);
        if (size == 1) {
            encoder.write(POP);
        } else if (size == 2) {
            encoder.write(POP2);
        }
    }

    private static int popSizeForReturnOp(byte returnOp) {
        return switch (returnOp) {
            default -> 1;
            case RETURN -> 0;
            case DRETURN, LRETURN -> 2;
        };
    }

    /**
     * @param op an INVOKE* or NEW operation
     */
    private static boolean hasInstance(byte op) {
        return op == INVOKEVIRTUAL || op == INVOKEINTERFACE || op == INVOKESPECIAL;
    }

    /**
     * @param caller the method being modified
     * @param maybeNull only applicable if hasInstance is true
     * @param methodRef the denied method being called
     * @param targetClass can be different than methodRef.mClass if isAssignableFrom is called
     * @param resumeAddress branch to this location if a value was generated; if negative then
     * return from the caller
     * @param argSlots method arguments as local variables
     * @param castArg0 when non-zero, the first argument should be cast to the type represented
     * by this index when passed to a checked or custom method handle
     * @param withArgs smt entry with the argSlots defined as local variables
     * @param denyAddresses addresses where deny actions have been encoded; initially null
     */
    private void encodeDenyAction(CodeAttr caller, boolean hasInstance, boolean maybeNull,
                                  C_MemberRef methodRef, C_Class targetClass,
                                  DenyAction action, int resumeAddress,
                                  int[] argSlots, int castArg0, StackMapTable.Entry withArgs,
                                  Map<DenyAction, Integer> denyAddresses)
        throws IOException
    {
        BufferEncoder encoder = caller.codeEncoder;

        if (action instanceof DenyAction.Multi mu) {
            C_MemberRef isAssignableFrom = null;

            Map<String, DenyAction> matches = mu.matches;

            if (matches.size() > 1 && denyAddresses == null) {
                // Use this to reduce code duplication.
                denyAddresses = new HashMap<>();
            }

            for (Map.Entry<String, DenyAction> e : matches.entrySet()) {
                targetClass = mConstantPool.addClass(e.getKey());
                int doCastArg0 = castArg0;

                if (hasInstance) {
                    CodeAttr.encodeVarOp(encoder, ALOAD, argSlots[0]);
                    encoder.write(INSTANCEOF);
                    encoder.writeShort(targetClass.mIndex);
                    doCastArg0 = targetClass.mIndex;
                } else {
                    if (isAssignableFrom == null) {
                        isAssignableFrom = mConstantPool.addMethodRef
                            ("java/lang/Class", "isAssignableFrom", "(Ljava/lang/Class;)Z");
                    }
                    encoder.write(LDC_W);
                    encoder.writeShort(targetClass.mIndex);
                    encoder.write(LDC_W);
                    encoder.writeShort(methodRef.mClass.mIndex);
                    encoder.write(INVOKEVIRTUAL);
                    encoder.writeShort(isAssignableFrom.mIndex);
                }

                encoder.write(IFEQ); // branch if false
                int offset = encoder.length();
                encoder.writeShort(0); // branch offset; to be filled in properly later
                caller.stackPushPop(2);

                // Note that maybeNull is now false because of the instanceof check.

                encodeDenyAction(caller, hasInstance, false, methodRef, targetClass,
                                 e.getValue(), resumeAddress,
                                 argSlots, doCastArg0, withArgs, denyAddresses);

                caller.smt.putEntry(encoder.length(), withArgs);
                encodeBranchTarget(encoder, offset);
            }

            return;
        }

        // Insert a check like so:
        // if (thisClass.getModule() != targetClass.getModule()) {
        //     perform the deny action...

        int offset;
        if (targetClass.mValue.isProhibitedPackage() &&
            !mConstantPool.findConstantClass(mThisClassIndex).mValue.isProhibitedPackage())
        {
            // Module check will always yield false, so always deny the action.
            offset = 0;
        } else {
            int getModuleIndex = mConstantPool.addMethodRef
                ("java/lang/Class", "getModule", "()Ljava/lang/Module;").mIndex;

            encoder.writeByte(LDC_W);
            encoder.writeShort(mThisClassIndex);
            encoder.writeByte(INVOKEVIRTUAL);
            encoder.writeShort(getModuleIndex);
            encoder.writeByte(LDC_W);
            encoder.writeShort(targetClass.mIndex);
            encoder.writeByte(INVOKEVIRTUAL);
            encoder.writeShort(getModuleIndex);
            encoder.writeByte(IF_ACMPEQ);
            offset = encoder.length();
            encoder.writeShort(0); // branch offset; to be filled in properly later
            caller.stackPushPop(2);
        }

        int nullCheckOffset = 0;

        if (hasInstance && maybeNull) {
            CodeAttr.encodeVarOp(encoder, ALOAD, argSlots[0]);
            encoder.write(IFNULL);
            caller.stackPushPop(1);
            nullCheckOffset = encoder.length();
            encoder.writeShort(0); // branch offset; to be filled in properly later
        }

        int checkedOffset = 0;

        if (action instanceof DenyAction.Checked checked) {
            int returnOp = encodeMethodHandleInvoke(caller, argSlots, castArg0, checked.predicate);
            if (returnOp == 0) {
                action = DenyAction.Standard.THE;
            } else {
                encoder.write(IFNE); // if true, then the operation is actually allowed
                caller.stackPop(1);
                checkedOffset = encoder.length();
                encoder.writeShort(0); // branch offset; to be filled in properly later
                action = checked.action;
            }
        }

        encode: {
            if (denyAddresses != null) {
                // If a matching deny action has already been encoded, just jump to it instead
                // of encoding it again.
                Integer address = denyAddresses.putIfAbsent(action, encoder.length());
                if (address != null) {
                    caller.smt.putEntry(address, withArgs);
                    encodeBranch(encoder, address);
                    break encode;
                }
            }

            DenyAction.Exception exAction;

            if (action instanceof DenyAction.Exception) {
                exAction = (DenyAction.Exception) action;
            } else {
                C_NameAndType nat = methodRef.mNameAndType;

                if (nat.mName.isConstructor()) {
                    if (action instanceof DenyAction.Custom cu) {
                        byte returnOp = encodeMethodHandleInvoke
                            (caller, argSlots, castArg0, cu.mhi);
                        if (returnOp != 0) {
                            encodeReturnPop(encoder, returnOp);
                        }
                    }
                    // Denied constructors must always throw an exception, so fall through.
                } else doReturn: {
                    byte returnOp;

                    if (action instanceof DenyAction.Value va) {
                        returnOp = encodeValue(caller, va.value, nat.mTypeDesc);
                    } else if (action instanceof DenyAction.Empty) {
                        returnOp = encodeEmpty(caller, nat.mTypeDesc);
                    } else if (action instanceof DenyAction.Custom cu) {
                        returnOp = encodeMethodHandleInvoke(caller, argSlots, castArg0, cu.mhi);
                    } else {
                        break doReturn;
                    }

                    if (returnOp != 0) {
                        encodeBranchOrReturn(caller, resumeAddress, returnOp);
                        break encode;
                    }
                }

                // Make sure an exception is always thrown.
                exAction = DenyAction.Standard.THE;
            }

            // Note: no need to pop the arguments because an exception will always be thrown.

            encodeExceptionAction(caller, exAction);
        }

        if (nullCheckOffset != 0) {
            caller.smt.putEntry(encoder.length(), withArgs);
            encodeBranchTarget(encoder, nullCheckOffset);
        }

        if (checkedOffset != 0) {
            caller.smt.putEntry(encoder.length(), withArgs);
            encodeBranchTarget(encoder, checkedOffset);
        }

        // When the module check isn't generated (the result would always be true), dead code
        // might be still generated for handling the allowed case. Regardless, a stack map
        // table is entry is required to make the verifier happy.
        caller.smt.putEntry(encoder.length(), withArgs);

        if (offset != 0) {
            encodeBranchTarget(encoder, offset);
        }
    }

    /**
     * Encode a branch to the end of the code encoder.
     *
     * @param fromOffset offset to the branch operation to be filled in
     */
    private static void encodeBranchTarget(BufferEncoder encoder, int fromOffset) {
        encodeShortBE(encoder.buffer(), fromOffset, encoder.length() - fromOffset + 1);
    }

    private void encodeExceptionAction(CodeAttr caller, DenyAction.Exception exAction)
        throws IOException
    {
        ConstantPool cp = mConstantPool;
        int exClassIndex = cp.addClass(exAction.className).mIndex;
        ConstantPool.C_MemberRef exInitRef;
        BufferEncoder encoder = caller.codeEncoder;

        if (exAction instanceof DenyAction.WithMessage wm) {
            exInitRef = cp.addMethodRef(exAction.className, "<init>", "(Ljava/lang/String;)V");
            encoder.writeByte(NEW);
            encoder.writeShort(exClassIndex);
            encoder.writeByte(DUP);
            encoder.writeByte(LDC_W);
            encoder.writeShort(cp.addString(wm.message).mIndex);
            caller.stackPushPop(3);
        } else {
            exInitRef = cp.addMethodRef(exAction.className, "<init>", "()V");
            encoder.writeByte(NEW);
            encoder.writeShort(exClassIndex);
            encoder.writeByte(DUP);
            caller.stackPushPop(2);
        }

        encoder.writeByte(INVOKESPECIAL);
        encoder.writeShort(exInitRef.mIndex);
        encoder.writeByte(ATHROW);
    }

    /**
     * @return a return opcode
     */
    private byte encodeValue(CodeAttr caller, Object value, ConstantPool.C_UTF8 desc)
        throws IOException
    {
        byte op = tryEncodeValue(caller, value, desc.charAt(desc.length() - 1));

        if (op == 0) {
            op = tryEncodeObjectValue(caller, value, desc);
            if (op == 0) {
                caller.codeEncoder.writeByte(ACONST_NULL);
                caller.stackPush(1);
                op = ARETURN;
            }
        }

        return op;
    }

    /**
     * Try to push a primitive value to the operand stack.
     *
     * @return zero if failed, or else a return opcode
     */
    private byte tryEncodeValue(CodeAttr caller, Object value, char type) throws IOException {
        BufferEncoder encoder = caller.codeEncoder;

        byte op;
        int push;

        switch (type) {
        default -> {
            return 0;
        }

        case 'V' -> {
            op = RETURN;
            push = 0;
        }

        case 'Z' -> {
            boolean v = value instanceof Boolean b && b;
            encoder.writeByte(v ? ICONST_1 : ICONST_0);
            op = IRETURN;
            push = 1;
        }

        case 'C' -> {
            char v = (value instanceof Character c) ? c :
                ((value instanceof Number n) ? ((char) n.intValue()) : 0);
            mConstantPool.pushInt(encoder, v);
            op = IRETURN;
            push = 1;
        }

        case 'B' -> {
            byte v = (value instanceof Number n) ? n.byteValue() : 0;
            mConstantPool.pushInt(encoder, v);
            op = IRETURN;
            push = 1;
        }

        case 'S' -> {
            short v = (value instanceof Number n) ? n.shortValue() : 0;
            mConstantPool.pushInt(encoder, v);
            op = IRETURN;
            push = 1;
        }

        case 'I' -> {
            int v = (value instanceof Number n) ? n.intValue() : 0;
            mConstantPool.pushInt(encoder, v);
            op = IRETURN;
            push = 1;
        }

        case 'J' -> {
            long v = (value instanceof Number n) ? n.longValue() : 0;
            mConstantPool.pushLong(encoder, v);
            op = LRETURN;
            push = 2;
        }

        case 'F' -> {
            float v = (value instanceof Number n) ? n.floatValue() : 0;
            mConstantPool.pushFloat(encoder, v);
            op = FRETURN;
            push = 1;
        }

        case 'D' -> {
            double v = (value instanceof Number n) ? n.doubleValue() : 0;
            mConstantPool.pushDouble(encoder, v);
            op = DRETURN;
            push = 2;
        }
        }

        caller.stackPush(push);

        return op;
    }

    /**
     * Try to push an object value to the operand stack.
     *
     * @return zero if failed, or else a return opcode
     */
    private byte tryEncodeObjectValue(CodeAttr caller, Object value, ConstantPool.C_UTF8 desc)
        throws IOException
    {
        if (value == null) {
            return 0;
        }

        BufferEncoder encoder = caller.codeEncoder;

        if (value instanceof String str) {
            if (isCompatible(desc, String.class)) {
                encoder.writeByte(LDC_W);
                encoder.writeShort(mConstantPool.addString(str).mIndex);
                return ARETURN;
            }
            return 0;
        }

        char primType;

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
                            mConstantPool.pushLong(encoder, v);
                            caller.stackPushPop(2);
                            break pushPrim;
                        }
                        return 0;
                    }

                    if (n instanceof Double v) {
                        if (isCompatible(desc, Double.class)) {
                            primType = 'D';
                            mConstantPool.pushDouble(encoder, v);
                            caller.stackPushPop(2);
                            break pushPrim;
                        }
                        return 0;
                    }

                    if (n instanceof Float v) {
                        if (isCompatible(desc, Float.class)) {
                            primType = 'F';
                            mConstantPool.pushFloat(encoder, v);
                            caller.stackPushPop(1);
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

            mConstantPool.pushInt(encoder, intValue);
            caller.stackPushPop(1);
        }

        String boxedClass = value.getClass().getName().replace('.', '/');

        C_MemberRef ref = mConstantPool.addMethodRef
            (boxedClass, "valueOf", "" + '(' + primType + ')' + 'L' + boxedClass + ';');

        encoder.writeByte(INVOKESTATIC);
        encoder.writeShort(ref.mIndex);

        caller.stackPush(1);

        return ARETURN;
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
     * @return a return opcode
     */
    private byte encodeEmpty(CodeAttr caller, ConstantPool.C_UTF8 desc) throws IOException {
        BufferEncoder encoder = caller.codeEncoder;

        char type = desc.charAt(desc.length() - 1);
        char prefix = desc.charAt(desc.length() - 2);

        if (prefix == '[') {
            type = prefix;
        }

        switch (type) {
        case 'V' -> {
            return RETURN;
        }

        case 'Z', 'C', 'B', 'S', 'I' -> {
            encoder.writeByte(ICONST_0);
            caller.stackPush(1);
            return IRETURN;
        }

        case 'J' -> {
            encoder.writeByte(LCONST_0);
            caller.stackPush(2);
            return LRETURN;
        }

        case 'F' -> {
            encoder.writeByte(FCONST_0);
            caller.stackPush(1);
            return FRETURN;
        }

        case 'D' -> {
            encoder.writeByte(DCONST_0);
            caller.stackPush(2);
            return DRETURN;
        }

        default -> {
            String descStr = desc.str();
            int ix = descStr.indexOf(')') + 1;
            if (ix < 2 || ix >= descStr.length()) {
                // Descriptor is broken.
                encoder.writeByte(ACONST_NULL);
            } else if (descStr.charAt(ix) == '[') {
                // Empty array.

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
                        caller.stackPop(1);
                        encoder.writeByte(ACONST_NULL);
                    }
                }
            } else {
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
                    caller.stackPushPop(2);
                    encoder.write(INVOKESPECIAL);
                    encoder.writeShort(init.mIndex);
                }
            }

            caller.stackPush(1);

            return ARETURN;
        }
        }
    }

    /**
     * @param argSlots method arguments as local variables
     * @param castArg0 when non-zero, the first argument should be cast to the type represented
     * by this index
     * @return a return opcode or else 0 if the method descriptor isn't compatible
     */
    private byte encodeMethodHandleInvoke(CodeAttr caller,
                                          int[] argSlots, int castArg0, MethodHandleInfo mhi)
        throws IOException
    {
        MethodType mt = mhi.getMethodType();
        int count = mt.parameterCount();
        int availableArgs = argSlots.length;

        if (count > 0 && mt.parameterType(0) == Caller.class) {
            availableArgs++;
        }

        if (count > availableArgs) {
            return 0;
        }

        ConstantPool cp = mConstantPool;
        BufferEncoder encoder = caller.codeEncoder;

        encoder.writeByte(LDC_W);
        encoder.writeShort(cp.addMethodHandle(mhi).mIndex);
        int pushed = 1;

        int slotNum = 0;

        for (int i=0; i<count; i++) {
            Class<?> type = mt.parameterType(i);
            if (type.isPrimitive()) {
                byte op;
                switch (type.descriptorString()) {
                    default -> {
                        op = ILOAD;
                        pushed++;
                    }
                    case "J" -> {
                        op = LLOAD;
                        pushed += 2;
                    }
                    case "F" -> {
                        op = FLOAD;
                        pushed++;
                    }
                    case "D" -> {
                        op = DLOAD;
                        pushed += 2;
                    }
                }
                CodeAttr.encodeVarOp(encoder, op, argSlots[slotNum++]);
            } else {
                if (i == 0 && type == Caller.class) {
                    // Pass the caller.
                    encoder.writeByte(LDC_W);
                    encoder.writeShort(mThisClassIndex);
                    encoder.writeByte(INVOKESTATIC);
                    C_Class agentClass = cp.addClass(SecurityAgent.class);
                    C_MemberRef ref = cp.addMethodRef(agentClass, "callerFor",
                                          "(Ljava/lang/Class;)Lorg/cojen/boxtin/Caller;");
                    encoder.writeShort(ref.mIndex);
                } else {
                    CodeAttr.encodeVarOp(encoder, ALOAD, argSlots[slotNum++]);
                    if (i == 0 && castArg0 != 0) {
                        encoder.writeByte(CHECKCAST);
                        encoder.writeShort(castArg0);
                    }
                }
                pushed++;
            }
        }

        caller.stackPushPop(pushed);

        MethodType lenient = lenient(mt);

        C_MemberRef invokeRef = cp.addMethodRef
            (MethodHandle.class.getName().replace('.', '/'), "invoke", lenient.descriptorString());

        encoder.writeByte(INVOKEVIRTUAL);
        encoder.writeShort(invokeRef.mIndex);

        Class<?> type = mt.returnType();

        byte op;
        int size;

        if (type.isPrimitive()) {
            switch (type.descriptorString()) {
                default -> {
                    op = IRETURN;
                    size = 1;
                }
                case "J" -> {
                    op = LRETURN;
                    size = 2;
                }
                case "F" -> {
                    op = FRETURN;
                    size = 1;
                }
                case "D" -> {
                    op = DRETURN;
                    size = 2;
                }
                case "V" -> {
                    op = RETURN;
                    size = 0;
                }
            }
        } else {
            op = ARETURN;
            size = 1;
        }

        caller.stackPush(size);

        return op;
    }

    /**
     * The denialsForMethod might mix static and instance methods together, which can cause
     * method signature incompatibilities, which can then cause a VerifyError. The
     * MethodHandle.invoke method by nature is lenient, so return a method type which accepts
     * plain Objects where appropriate.
     */
    private static MethodType lenient(MethodType mt) {
        Class<?>[] ptypes = mt.parameterArray();
        for (int i=0; i<ptypes.length; i++) {
            ptypes[i] = lenient(ptypes[i]);
        }
        return MethodType.methodType(mt.returnType(), ptypes);
    }

    private static Class<?> lenient(Class<?> ptype) {
        return ptype.isPrimitive()
            || ptype.isArray() || ptype == Caller.class ? ptype : Object.class;
    }

    /**
     * Define a proxy method to be used by replaced MethodHandle constants. The proxy method
     * performs the deny checks, and then it invokes the original method if not denied.
     *
     * @param op must be an INVOKE* or NEW operation (cannot be INVOKEDYNAMIC)
     * @param methodRef the denied method being called
     */
    private ProxyMethod addProxyMethod(byte op, C_MemberRef methodRef, DenyAction action)
        throws IOException
    {
        int accessFlags = Modifier.PRIVATE | Modifier.STATIC | 0x1000; // | synthetic
        return addProxyMethod(op, methodRef, action, accessFlags, null);
    }

    /**
     * @param proxyMethodName pass null to define a unique method name
     */
    private ProxyMethod addProxyMethod(byte op, C_MemberRef methodRef, DenyAction action,
                                       int accessFlags, String proxyMethodName)
        throws IOException
    {
        ConstantPool cp = mConstantPool;

        if (mProxyMethods == null) {
            mProxyMethods = new HashMap<>();
        }

        Integer key = (op << 24) | methodRef.mIndex;

        ProxyMethod proxyMethod = mProxyMethods.get(key);

        if (proxyMethod != null) {
            return proxyMethod;
        }

        C_Class instanceType;
        if (op != INVOKESPECIAL) {
            instanceType = methodRef.mClass;
        } else {
            instanceType = cp.findConstantClass(mThisClassIndex);
        }

        ConstantPool.C_UTF8 proxyDesc = cp.addWithFullSignature(op, instanceType, methodRef);

        C_Class thisClass = cp.findConstantClass(mThisClassIndex);
        C_MemberRef proxyMethodRef;

        if (proxyMethodName == null) {
            proxyMethodRef = cp.addUniqueMethod(thisClass, proxyDesc);
        } else {
            proxyMethodRef = cp.addMethodRef
                (thisClass, cp.addNameAndType(proxyMethodName, proxyDesc));
        }

        proxyMethod = new ProxyMethod(proxyMethodRef, accessFlags);
        proxyMethod.prepareForModification(cp, mThisClassIndex, null);
        mProxyMethods.put(key, proxyMethod);

        int[] argSlots = proxyMethod.staticParamArgs(proxyDesc.asMethodTypeDesc());

        boolean hasInstance = hasInstance(op);

        StackMapTable.Entry withArgs = proxyMethod.smt.getEntry(0);

        encodeDenyAction(proxyMethod, hasInstance, true, methodRef, methodRef.mClass,
                         action, -1, argSlots, 0, withArgs, null);

        BufferEncoder encoder = proxyMethod.codeEncoder;

        if (op == NEW) {
            encoder.write(NEW);
            encoder.writeShort(instanceType.mIndex);
            encoder.write(DUP);
            proxyMethod.stackPush(2);
            op = INVOKESPECIAL;
        }

        int pushed = proxyMethod.loadArgs(encoder, hasInstance, methodRef, argSlots);

        encoder.write(op);
        encoder.writeShort(methodRef.mIndex);

        if (op == INVOKEINTERFACE) {
            encoder.writeByte(1 + pushed);
            encoder.writeByte(0);
        }

        String returnDescStr = proxyDesc.asMethodTypeDesc().returnType().descriptorString();

        byte returnOp = switch (returnDescStr) {
            default -> ARETURN;
            case "V" -> RETURN;
            case "B", "C", "I", "S", "Z" -> IRETURN;
            case "F" -> FRETURN;
            case "D" -> DRETURN;
            case "J" -> LRETURN;
        };

        encoder.write(returnOp);

        return proxyMethod;
    }

    private static class ProxyMethod extends CodeAttr {
        final C_MemberRef mMethodRef;

        private int mCodeAttrNameIndex;

        ProxyMethod(C_MemberRef methodRef, int accessFlags) {
            mMethodRef = methodRef;
            this.accessFlags = accessFlags;
            nameIndex = methodRef.mNameAndType.mName.mIndex;
            descIndex = methodRef.mNameAndType.mTypeDesc.mIndex;
        }

        /**
         * @param originalBuffer ignored
         */
        @Override
        public long finish(ConstantPool cp, byte[] originalBuffer) throws IOException {
            if (mNewCodeLength == 0) {
                mNewCodeLength = codeEncoder.length();
                codeEncoder.writeShort(0); // exception_table_length
                codeEncoder.writeShort(1); // attributes_count (just StackMapTable)
                codeEncoder.writeShort(cp.addUTF8("StackMapTable").mIndex);
                smt.writeTo(codeEncoder);
                mCodeAttrNameIndex = cp.addUTF8("Code").mIndex;
            }

            // Length includes the method_info fields and the Code attribute_name_index field.
            return (2 + 2 + 2 + 2) + 2 + newLength();
        }

        @Override
        public void writeTo(BufferEncoder encoder) throws IOException {
            encoder.writeShort(accessFlags);
            encoder.writeShort(nameIndex);
            encoder.writeShort(descIndex);
            encoder.writeShort(1); // attributes_count (just Code)

            encoder.writeShort(mCodeAttrNameIndex);

            super.writeTo(encoder);
        }
    }

    private static class ReplacedMethod extends CodeAttr {
        ReplacedMethod(CodeAttr original) {
            attrOffset = original.attrOffset;
            attrLength = original.attrLength;
            accessFlags = original.accessFlags;
            nameIndex = original.nameIndex;
            descIndex = original.descIndex;
            maxLocals = original.maxLocals;
            codeOffset = original.codeOffset;
            codeLength = original.codeLength;
        }

        @Override
        public long finish(ConstantPool cp, byte[] originalBuffer) throws IOException {
            if (mNewCodeLength == 0) {
                mNewCodeLength = codeEncoder.length();
                codeEncoder.writeShort(0); // exception_table_length
                codeEncoder.writeShort(0); // attributes_count
            }
            return newLength();
        }
    }
}
