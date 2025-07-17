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

import java.util.Arrays;

import static org.cojen.boxtin.ConstantPool.*;
import static org.cojen.boxtin.Opcodes.*;
import static org.cojen.boxtin.Utils.*;

/**
 * Supports replacing the code of a method_info.
 *
 * @author Brian S. O'Neill
 */
class CodeAttr implements RegionReplacement {
    // Offset and length of the original code attribute in the class file, starting at the
    // attribute_length field.
    int attrOffset;
    long attrLength;

    // Fields copied from method_info.
    int accessFlags, nameIndex, descIndex;

    int maxStack, maxLocals;

    // Offset and length of the original bytecode operations.
    int codeOffset, codeLength;

    // The remaining fields are defined when the method is modified.

    // Modified code.
    BufferEncoder codeEncoder;

    // Modified StackMapTable.
    StackMapTable smt;

    private boolean mHasNewSMT;

    private IntArray mReplacedOpAddresses;

    // Number of new temporary locals defined, to be added to maxLocals later.
    int maxTempLocals;

    // Work field for tracking the stack size as code is defined.
    int mCurrentStackSize;

    protected int mNewCodeLength;

    @Override
    public long finish(ConstantPool cp, byte[] originalBuffer) throws IOException {
        if (mNewCodeLength != 0) {
            return newLength();
        }

        if (codeEncoder == null) {
            throw new IllegalStateException();
        }

        mNewCodeLength = codeEncoder.length();

        var decoder = new BufferDecoder(originalBuffer);
        decoder.offset(attrOffset + (4 + 2 + 2 + 4) + codeLength); // at the exception table

        int exCount = decoder.readUnsignedShort();

        if (exCount == 0) {
            codeEncoder.writeShort(0);
        } else {
            // Update the exception handlers to cover the new deny checks and the moved invoke
            // operation.

            var exTable = new long[exCount];
            for (int i=0; i<exCount; i++) {
                // Read start_pc, end_pc, handler_pc, and catch_type.
                exTable[i] = decoder.readLong();
            }

            IntArray replaced = mReplacedOpAddresses;
            int numReplaced = replaced.length() / 3;

            IntArray newEntries = null;

            for (int i=0; i<numReplaced; i++) {
                int opAddress = replaced.get(i * 3);

                for (int ix = 0; ix < exTable.length; ix++) {
                    long entry = exTable[ix];
                    int start_pc = (int) (entry >>> 48);
                    if (start_pc <= opAddress) {
                        int end_pc = ((int) (entry >>> 32)) & 0xffff;
                        if (opAddress < end_pc) {
                            if (newEntries == null) {
                                newEntries = new IntArray(numReplaced * 2);
                            }
                            int newStartPc = replaced.get(i * 3 + 1);
                            int newEndPc = replaced.get(i * 3 + 2);
                            newEntries.push(newStartPc << 16 | newEndPc);
                            newEntries.push((int) entry);
                        }
                    }
                }
            }

            int newLen = exTable.length;

            if (newEntries != null) {
                newLen += newEntries.length() / 2;
            }

            codeEncoder.writeShort(newLen);

            for (long entry : exTable) {
                codeEncoder.writeLong(entry);
            }

            if (newEntries != null) {
                int numNew = newEntries.length();
                for (int i=0; i<numNew; i++) {
                    codeEncoder.writeInt(newEntries.get(i));
                }
            }
        }

        int attrsCount = decoder.readUnsignedShort();

        if (!mHasNewSMT) {
            // Expect to find an existing StackMapTable to replace.
            codeEncoder.writeShort(attrsCount);
        } else {
            codeEncoder.writeShort(attrsCount + 1);
            codeEncoder.writeShort(cp.addUTF8("StackMapTable").mIndex);
            smt.writeTo(codeEncoder);
        }

        for (int i=0; i<attrsCount; i++) {
            int attrNameIndex = decoder.readUnsignedShort();
            long attrLength = decoder.readUnsignedInt();

            codeEncoder.writeShort(attrNameIndex);

            ConstantPool.C_UTF8 attrName = cp.findConstantUTF8(attrNameIndex);

            if (attrName.equals("StackMapTable")) {
                // Replace the existing StackMapTable.
                smt.writeTo(codeEncoder);
                decoder.skipNBytes(attrLength);
                continue;
            }

            if (!attrName.equals("LineNumberTable")) {
                // Copy the original attribute.
                codeEncoder.writeInt((int) attrLength);
                decoder.transferTo(codeEncoder, attrLength);
                continue;
            }

            // Update the line numbers to cover the new deny checks and the moved invoke
            // operation.

            int numEntries = decoder.readUnsignedShort();
            var lnTable = new long[numEntries];
            for (int j=0; j<numEntries; j++) {
                lnTable[j] = decoder.readUnsignedInt();
            }

            Arrays.sort(lnTable);

            IntArray replaced = mReplacedOpAddresses;
            int numReplaced = replaced.length() / 3;

            IntArray newEntries = null;

            for (int j=0; j<numReplaced; j++) {
                int opAddress = replaced.get(j * 3);

                long key = (((long) opAddress) << 16) | 0xffff;
                int ix = Arrays.binarySearch(lnTable, key);
                if (ix < 0) {
                    ix = ~ix;
                }
                ix--;

                if (ix < 0) {
                    continue;
                }

                if (newEntries == null) {
                    newEntries = new IntArray(numReplaced);
                }

                newEntries.push((replaced.get(j * 3 + 1) << 16) | (int) (lnTable[ix] & 0xffff));
            }

            if (newEntries != null) {
                attrLength += newEntries.length() * 4L;
            }

            codeEncoder.writeInt((int) attrLength);
            codeEncoder.writeShort((int) (attrLength / 4));

            for (long entry : lnTable) {
                codeEncoder.writeInt((int) entry);
            }

            if (newEntries != null) {
                int numNew = newEntries.length();
                for (int j=0; j<numNew; j++) {
                    codeEncoder.writeInt(newEntries.get(j));
                }
            }
        }

        return newLength();
    }

    final long newLength() {
        // Length includes the attribute_length, max_stack, max_locals, and code_length fields.
        return (4 + 2 + 2 + 4) + codeEncoder.length();
    }

    @Override
    public long originalLength() {
        return attrLength;
    }

    @Override
    public void writeTo(BufferEncoder encoder) throws IOException {
        if (mNewCodeLength == 0) {
            throw new IllegalStateException();
        }

        encoder.writeInt((int) (newLength() - 4)); // attribute_length
        encoder.writeShort(maxStack);
        encoder.writeShort(maxLocals + maxTempLocals);
        encoder.writeInt(mNewCodeLength);

        // This writes the code, the exception table, and all the remaining attributes.
        encoder.write(codeEncoder.buffer(), 0, codeEncoder.length());
    }

    void prepareForModification(ConstantPool cp, int thisClassIndex, byte[] buffer)
        throws IOException
    {
        // Initial capacity should have some room for growth.
        codeEncoder = new BufferEncoder(Math.toIntExact(attrLength + 100));

        if (buffer != null) {
            // Copy the original code.
            codeEncoder.write(buffer, codeOffset, codeLength);

            // Skip the exception table.
            int offset = codeOffset + codeLength;
            offset += 2 + decodeUnsignedShortBE(buffer, offset) * 8;

            int attrsCount = decodeUnsignedShortBE(buffer, offset); offset += 2;

            for (int i=0; i<attrsCount; i++) {
                int attrNameIndex = decodeUnsignedShortBE(buffer, offset); offset += 2;
                int attrLength = decodeIntBE(buffer, offset); offset += 4;

                ConstantPool.C_UTF8 attrName = cp.findConstantUTF8(attrNameIndex);

                if (attrName.equals("StackMapTable") && smt == null) {
                    smt = new StackMapTable(cp, thisClassIndex, this, buffer, offset);
                }

                offset += attrLength;
            }
        }

        if (smt == null) {
            smt = new StackMapTable(cp, thisClassIndex, this, null, 0);
            mHasNewSMT = true;
        }

        mReplacedOpAddresses = new IntArray(4 * 3);
    }

    /**
     * Register an operation which was replaced with a deny check. When the code is finished,
     * the exception and line number tables are updated such that check code is also referenced
     * by the tables.
     *
     * @param opAddress invoke operation address
     * @param checkStartAddress start of the new deny check code
     * @param checkEndAddress end of the new deny check code (exclusive)
     */
    void replaced(int opAddress, int checkStartAddress, int checkEndAddress) {
        mReplacedOpAddresses.push(opAddress);
        mReplacedOpAddresses.push(checkStartAddress);
        mReplacedOpAddresses.push(checkEndAddress);
    }

    record StoredArgs(int[] argSlots, StackMapTable.Entry withArgs) { }

    /**
     * Store method arguments (and the instance if applicable) on the operand stack to
     * temporary local variables.
     *
     * @param entry entry at the code location
     * @param methodRef the denied method being called
     * @return array of local variable slots
     */
    StoredArgs storeArgs(BufferEncoder encoder, StackMapTable.Entry entry,
                         boolean hasInstance, C_MemberRef methodRef)
        throws IOException, ClassFormatException
    {
        MethodTypeDesc desc = methodRef.mNameAndType.mTypeDesc.asMethodTypeDesc();
        int count = desc.parameterCount();

        int slotNum = count;
        if (hasInstance) {
            slotNum++;
        }

        var argSlots = new int[slotNum];

        IntArray localTypes = entry.localTypes().copy();
        IntArray stackTypes = entry.stackTypes().copy();

        int localNum = maxLocals;

        int entryLocalNum;
        {
            entryLocalNum = localNum;
            int len = localTypes.length();
            for (int i=0; i<len; i++) {
                if (StackMapTable.isWideType(localTypes.get(i))) {
                    entryLocalNum--;
                }
            }
        }

        for (int i=count; --i>=0; ) {
            int type = stackTypes.pop();
            localTypes.set(entryLocalNum++, type);

            byte op;
            int size;

            switch (type) {
                default -> {
                    op = ASTORE;
                    size = 1;
                }
                case StackMapTable.TAG_INT -> {
                    op = ISTORE;
                    size = 1;
                }
                case StackMapTable.TAG_FLOAT -> {
                    op = FSTORE;
                    size = 1;
                }
                case StackMapTable.TAG_DOUBLE -> {
                    op = DSTORE;
                    size = 2;
                }
                case StackMapTable.TAG_LONG -> {
                    op = LSTORE;
                    size = 2;
                }
            }

            encodeVarOp(encoder, op, localNum);
            stackPop(size);
            argSlots[--slotNum] = localNum;

            localNum += size;

            if (localNum > 65536) {
                throw new ClassFormatException("Too many local variables");
            }
        }

        if (slotNum != 0) {
            // Store the instance.
            localTypes.set(entryLocalNum++, stackTypes.pop());
            encodeVarOp(encoder, ASTORE, localNum);
            stackPop(1);
            argSlots[--slotNum] = localNum;
            localNum++;
            if (localNum > 65536) {
                throw new ClassFormatException("Too many local variables");
            }
        }

        maxTempLocals = Math.max(maxTempLocals, localNum - maxLocals);

        return new StoredArgs(argSlots, new StackMapTable.Entry(localTypes, stackTypes));
    }

    /**
     * Load temporary local variables to the operand stack.
     *
     * @param methodRef the denied method being called
     * @param argSlots array of local variable slots
     * @return number of stack slots pushed
     */
    int loadArgs(BufferEncoder encoder, boolean hasInstance, C_MemberRef methodRef, int[] argSlots)
        throws IOException
    {
        int slotNum = 0;

        if (hasInstance) {
            encodeVarOp(encoder, ALOAD, argSlots[slotNum++]);
            stackPush(1);
        }

        MethodTypeDesc desc = methodRef.mNameAndType.mTypeDesc.asMethodTypeDesc();
        int count = desc.parameterCount();
        int pushed = 0;

        for (int i=0; i<count; i++) {
            ClassDesc paramType = desc.parameterType(i);

            byte op;
            int size;

            switch (paramType.descriptorString()) {
                default -> {
                    op = ALOAD;
                    size = 1;
                }
                case "B", "C", "I", "S", "Z" -> {
                    op = ILOAD;
                    size = 1;
                }
                case "F" -> {
                    op = FLOAD;
                    size = 1;
                }
                case "D" -> {
                    op = DLOAD;
                    size = 2;
                }
                case "J" -> {
                    op = LLOAD;
                    size = 2;
                }
            }

            encodeVarOp(encoder, op, argSlots[slotNum++]);
            stackPush(size);
            pushed += size;
        }

        return pushed;
    }

    static void encodeVarOp(BufferEncoder encoder, byte op, int slot) throws IOException {
        if (slot <= 255) {
            encoder.writeByte(op);
            encoder.writeByte(slot);
        } else {
            encoder.writeByte(WIDE);
            encoder.writeByte(op);
            encoder.writeShort(slot);
        }
    }

    /**
     * Prepare method arguments as local variables, for a non-instance method.
     *
     * @param desc defines all the arguments
     * @return array of local variable slots
     */
    int[] staticParamArgs(MethodTypeDesc desc) {
        int count = desc.parameterCount();
        int localNum = 0;

        var argSlots = new int[count];
        int slotNum = 0;

        for (int i=0; i<count; i++) {
            argSlots[slotNum++] = localNum;
            String descString = desc.parameterType(i).descriptorString();
            localNum += descString.equals("D") || descString.equals("J") ? 2 : 1;
        }

        maxLocals = Math.max(maxLocals, localNum);

        return argSlots;
    }

    /**
     * Reset the current work stack size.
     */
    void stackReset(int size) {
        if (size > maxStack) {
            maxStack = size;
        }
        mCurrentStackSize = size;
    }

    /**
     * Increment the current work stack size, updating the max stack size if necessary.
     */
    void stackPush(int amount) {
        int size = mCurrentStackSize + amount;
        if (size > maxStack) {
            maxStack = size;
        }
        mCurrentStackSize = size;
    }

    /**
     * Decrement the current work stack size.
     */
    void stackPop(int amount) {
        int size = mCurrentStackSize - amount;
        if (size < 0) {
            throw new IllegalStateException();
        }
        mCurrentStackSize = size;
    }

    /**
     * Update the max stack size if necessary.
     */
    void stackPushPop(int amount) {
        int size = mCurrentStackSize + amount;
        if (size > maxStack) {
            maxStack = size;
        }
    }
}
