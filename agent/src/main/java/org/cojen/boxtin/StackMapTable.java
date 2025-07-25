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

import java.lang.reflect.Modifier;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import static org.cojen.boxtin.ConstantPool.*;
import static org.cojen.boxtin.Opcodes.*;
import static org.cojen.boxtin.Utils.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class StackMapTable {
    static final int TAG_INT = 1, TAG_FLOAT = 2, TAG_DOUBLE = 3, TAG_LONG = 4,
        TAG_NULL = 5, TAG_UNINIT_THIS = 6, TAG_OBJECT = 7, TAG_UNINIT = 8;

    static boolean isWideType(int tag) {
        return tag == TAG_DOUBLE || tag == TAG_LONG;
    }

    private static final IntArray EMPTY = new IntArray(0);

    record Entry(IntArray localTypes, IntArray stackTypes) {
        /**
         * Returns the stack size for this entry, whereby long/double types occupy two slots.
         */
        int stackSize() {
            int length = stackTypes.length();
            int size = length;
            for (int i=0; i<length; i++) {
                if (isWideType(stackTypes.get(i))) {
                    size++;
                }
            }
            return size;
        }

        private void writeTo(BufferEncoder encoder, int offsetDelta, Entry prev)
            throws IOException
        {
            if (stackTypes.length() <= 1) {
                int localsDiff = diff(prev.localTypes, localTypes);
                if (localsDiff == 0) {
                    if (offsetDelta < 64) {
                        if (stackTypes.length() == 0) {
                            // same_frame
                            encoder.writeByte(offsetDelta);
                        } else {
                            // same_locals_1_stack_item_frame
                            encoder.writeByte(offsetDelta + 64);
                            writeType(encoder, stackTypes.get(0));
                        }
                    } else {
                        if (stackTypes.length() == 0) {
                            // same_frame_extended
                            encoder.writeByte(251);
                            encoder.writeShort(offsetDelta);
                        } else {
                            // same_locals_1_stack_item_frame_extended
                            encoder.writeByte(247);
                            encoder.writeShort(offsetDelta);
                            writeType(encoder, stackTypes.get(0));
                        }
                    }
                    return;
                } else if (-3 <= localsDiff && localsDiff <= 3) {
                    if (stackTypes.length() == 0) {
                        // chop_frame or append_frame
                        encoder.writeByte(251 + localsDiff);
                        encoder.writeShort(offsetDelta);
                        if (localsDiff > 0) {
                            int i = localTypes.length() - localsDiff;
                            for (; i < localTypes.length(); i++) {
                                writeType(encoder, localTypes.get(i));
                            }
                        }
                        return;
                    }
                }
            }

            // full_frame
            encoder.writeByte(255);
            encoder.writeShort(offsetDelta);
            writeTypes(encoder, localTypes);
            writeTypes(encoder, stackTypes);
        }

        private static void writeTypes(BufferEncoder encoder, IntArray types) throws IOException {
            int length = types.length();
            encoder.writeShort(length);
            for (int i=0; i<length; i++) {
                writeType(encoder, types.get(i));
            }
        }

        private static void writeType(BufferEncoder encoder, int type) throws IOException {
            int smTag = type & 0xffff;
            encoder.writeByte(smTag);
            if (smTag >= TAG_OBJECT) {
                encoder.writeShort(type >>> 16);
            }
        }

        /**
         * @return MIN_VALUE if mismatched; 0 if the same, -n if chopped, +n if appended
         */
        private static int diff(IntArray from, IntArray to) {
            if (from.length() == 0) {
                return to.length();
            }
            if (to.length() == 0) {
                return -from.length();
            }
            int mismatch = from.mismatch(to);
            if (mismatch < 0) {
                return 0;
            }
            if (mismatch >= from.length() || mismatch >= to.length()) {
                return to.length() - from.length();
            }
            return Integer.MIN_VALUE;
        }
    }

    private final Entry mInitialEntry;

    // Maps bytecode addresses to corresponding entries.
    private final TreeMap<Integer, Entry> mEntries;

    /**
     * Decodes a StackMapTable attribute for a method.
     *
     * @param insertion increment all addresses but the initial one by this amount
     * @param buffer refers to the attribute data to decode; pass null if none
     * @param offset buffer offset to the number_of_entries field
     */
    StackMapTable(ConstantPool cp, int thisClassIndex, CodeAttr method, int insertion,
                  byte[] buffer, int offset)
    {
        mEntries = new TreeMap<>();

        // Make an entry for the initial frame.
        Entry prevEntry;
        {
            MethodTypeDesc desc = cp.findConstantUTF8(method.descIndex).asMethodTypeDesc();

            int count = desc.parameterCount();
            IntArray localTypes;

            if (Modifier.isStatic(method.accessFlags)) {
                localTypes = count == 0 ? EMPTY : new IntArray(count);
            } else {
                localTypes = new IntArray(count + 1);
                int tag;
                if (cp.findConstantUTF8(method.nameIndex).isConstructor()) {
                    tag = TAG_UNINIT_THIS;
                } else {
                    // Store cpool_index in the upper word.
                    tag = TAG_OBJECT | (thisClassIndex << 16);
                }
                localTypes.push(tag);
            }

            for (int i=0; i<count; i++) {
                localTypes.push(tagForType(cp, desc.parameterType(i)));
            }

            mEntries.put(0, mInitialEntry = prevEntry = new Entry(localTypes, EMPTY));
        }

        if (buffer == null) {
            return;
        }

        int numEntries = decodeUnsignedShortBE(buffer, offset);
        offset += 2;

        int address = -1;

        for (int i=0; i<numEntries; i++) {
            int frameType = buffer[offset++] & 0xff;

            IntArray localTypes, stackTypes;

            if (frameType < 64) { // same_frame
                address += frameType + 1;
                localTypes = prevEntry.localTypes();
                stackTypes = EMPTY;
            } else if (frameType < 128) { // same_locals_1_stack_item_frame
                address += frameType - 63;
                localTypes = prevEntry.localTypes();
                stackTypes = new IntArray(1);
                offset = decodeTypeInfo(buffer, offset, stackTypes, 0);
            } else if (frameType < 247) {
                throw new ClassFormatException();
            } else {
                address += decodeUnsignedShortBE(buffer, offset) + 1; offset += 2;

                if (frameType == 247) { // same_locals_1_stack_item_frame_extended
                    localTypes = prevEntry.localTypes();
                    stackTypes = new IntArray(1);
                    offset = decodeTypeInfo(buffer, offset, stackTypes, 0);
                } else if (frameType <= 250) { // chop_frame
                    localTypes = prevEntry.localTypes();
                    localTypes = localTypes.chop(251 - frameType);
                    stackTypes = EMPTY;
                } else if (frameType == 251) { // same_frame_extended
                    localTypes = prevEntry.localTypes();
                    stackTypes = EMPTY;
                } else if (frameType <= 254) { // append_frame
                    localTypes = prevEntry.localTypes();
                    int length = localTypes.length();
                    int amount = frameType - 251;
                    localTypes = localTypes.append(amount);
                    offset = decodeTypeInfos(buffer, offset, localTypes, length, amount);
                    stackTypes = EMPTY;
                } else { // full_frame
                    int amount = decodeUnsignedShortBE(buffer, offset); offset += 2;
                    localTypes = amount == 0 ? EMPTY : new IntArray(amount);
                    offset = decodeTypeInfos(buffer, offset, localTypes, 0, amount);
                    amount = decodeUnsignedShortBE(buffer, offset); offset += 2;
                    stackTypes = amount == 0 ? EMPTY : new IntArray(amount);
                    offset = decodeTypeInfos(buffer, offset, stackTypes, 0, amount);
                }
            }

            mEntries.put(address + insertion, prevEntry = new Entry(localTypes, stackTypes));
        }
    }

    /**
     * @param types result is stored here
     * @param index index into types array
     * @return updated offset
     */
    private static int decodeTypeInfo(byte[] buffer, int offset, IntArray types, int index)
        throws ClassFormatException
    {
        int type = buffer[offset++] & 0xff;

        if (type >= 7) { // Object_variable_info or Uninitialized_variable_info
            if (type > 8) {
                throw new ClassFormatException();
            }
            // Store cpool_index or offset in the upper word.
            type |= decodeUnsignedShortBE(buffer, offset) << 16;
            offset += 2;
        }

        types.set(index, type);

        return offset;
    }

    /**
     * @param types result is stored here
     * @param index first index into types array
     * @param amount amount to decode
     * @return updated offest
     */
    private static int decodeTypeInfos(byte[] buffer, int offset, IntArray types, int index,
                                       int amount)
        throws ClassFormatException
    {
        while (--amount >= 0) {
            offset = decodeTypeInfo(buffer, offset, types, index++);
        }
        return offset;
    }

    static int tagForType(ConstantPool cp, ClassDesc type) {
        String desc = type.descriptorString();

        return switch (desc) {
            default -> {
                if (!type.isArray()) {
                    // Drop the "L" prefix and ";" suffix.
                    desc = desc.substring(1, desc.length() - 1);
                }
                // Store cpool_index in the upper word.
                yield tagForType(cp.addClass(desc));
            }
            case "B", "C", "I", "S", "Z" -> TAG_INT;
            case "F" -> TAG_FLOAT;
            case "D" -> TAG_DOUBLE;
            case "J" -> TAG_LONG;
        };
    }

    static int tagForType(C_Class type) {
        return tagForType(type, TAG_OBJECT);
    }

    private static int tagForType(C_Class type, int tagType) {
        // Store cpool_index in the upper word.
        return tagType | (type.mIndex << 16);
    }

    void writeTo(BufferEncoder encoder) throws IOException {
        int start = encoder.length();
        encoder.writeInt(2); // attribute_length; to be filled in properly later

        Iterator<Map.Entry<Integer, Entry>> it = mEntries.entrySet().iterator();

        int prevAddress = 0;
        Entry prev = mEntries.get(0);

        if (prev == mInitialEntry) {
            encoder.writeShort(mEntries.size() - 1); // number_of_entries
            prev = it.next().getValue();
        } else {
            encoder.writeShort(mEntries.size()); // number_of_entries
            prev = mInitialEntry;
        }

        while (it.hasNext()) {
            Map.Entry<Integer, Entry> mapEntry = it.next();
            int address = mapEntry.getKey();
            Entry entry = mapEntry.getValue();
            int offsetDelta = prev == mInitialEntry ? address : (address - prevAddress - 1);
            entry.writeTo(encoder, offsetDelta, prev);
            prevAddress = address;
            prev = entry;
        }

        encodeIntBE(encoder.buffer(), start, encoder.length() - start - 4);
    }


    Entry getEntry(int address) {
        return mEntries.get(address);
    }

    Entry removeEntry(int address) {
        return mEntries.remove(address);
    }

    void putEntry(int address, Entry entry) {
        if (entry == mInitialEntry) {
            entry = new Entry(entry.localTypes, entry.stackTypes);
        }
        mEntries.put(address, entry);
    }

    /**
     * Returns a copy of the given array such that wide types occupy two slots.
     */
    private static IntArray expandTypes(IntArray types) {
        int length = types.length();

        for (int i=0; i<length; i++) {
            if (!isWideType(types.get(i))) {
                continue;
            }

            IntArray newTypes = types.copy(length << 1);

            for (int j=i; i<length; i++) {
                int type = types.get(i);
                newTypes.set(j++, type);
                if (isWideType(type)) {
                    newTypes.set(j++, type);
                }
            }

            return newTypes;
        }

        // No wide types.
        return types.copy();
    }

    /**
     * Updates the given array of types such that wide types occupy one slot.
     */
    private static IntArray collapseTypes(IntArray types) {
        int length = types.length();
        int j = 0;

        for (int i=0; i<length; i++) {
            int type = types.get(i);
            types.set(j++, type);
            if (isWideType(type)) {
                i++;
            }
        }

        types.length(j);

        return types;
    }

    /**
     * Inserts an entry at the given address using bytecode examination, but only if no entry
     * currently exists at the given address.
     *
     * @param thisClassIndex used for TAG_UNINIT_THIS
     * @param address address to insert an entry at
     * @param insertion amount of bytes inserted before the first original operation
     * @param buffer refers to the bytecode
     * @param offset buffer offset to the first bytecode operation
     * @return false if an entry already existed
     */
    boolean insertEntry(ConstantPool cp, int thisClassIndex, int address,
                        int insertion, byte[] buffer, int offset)
    {
        Integer key = address;
        Map.Entry<Integer, Entry> e = mEntries.floorEntry(key); // find less than or equal
        Integer prevAddress = e.getKey();

        if (prevAddress.equals(key)) {
            return false;
        }

        int endOffset = offset + (address - insertion);
        int startOffset = offset;
        offset = Math.max(startOffset, offset + (prevAddress - insertion));

        Entry prevEntry = e.getValue();
        IntArray localTypes = expandTypes(prevEntry.localTypes());
        IntArray stackTypes = prevEntry.stackTypes().copy(4);

        while (offset < endOffset) {
            byte op = buffer[offset++];

            switch (op) {
            default -> {
                throw new ClassFormatException();
            }

            // Operations which don't affect the entry and have no operand bytes...

            case NOP, INEG, LNEG, FNEG, DNEG, I2B, I2C, I2S, RETURN -> { }

            // Operations which don't affect the entry and have one operand byte...

            case RET -> {
                offset++;
            }

            // Operations which don't affect the entry and have two operand bytes...

            case IINC, GOTO, JSR -> {
                offset += 2;
            }

            // Operations which don't affect the entry and have four operand bytes...

            case GOTO_W, JSR_W -> {
                offset += 4;
            }

            // Operations which push an int and have no operands...

            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3 ->
            {
                stackTypes.push(TAG_INT);
            }

            // Operations which push an int and have one operand byte...

            case BIPUSH, ILOAD -> {
                offset++;
                stackTypes.push(TAG_INT);
            }

            // Operations which push an int and have two operand bytes...

            case SIPUSH -> {
                offset += 2;
                stackTypes.push(TAG_INT);
            }

            // Operations which push a float and have no operands...

            case FCONST_0, FCONST_1, FCONST_2, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3 -> {
                stackTypes.push(TAG_FLOAT);
            }

            // Operations which push a float and have one operand byte...

            case FLOAD -> {
                offset++;
                stackTypes.push(TAG_FLOAT);
            }

            // Operations which push a double and have no operands...

            case DCONST_0, DCONST_1, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3 -> {
                stackTypes.push(TAG_DOUBLE);
            }

            // Operations which push a double and have one operand byte...

            case DLOAD -> {
                offset++;
                stackTypes.push(TAG_DOUBLE);
            }

            // Operations which push a long and have no operands...

            case LCONST_0, LCONST_1, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3 -> {
                stackTypes.push(TAG_LONG);
            }

            // Operations which push a long and have one operand byte...

            case LLOAD -> {
                offset++;
                stackTypes.push(TAG_LONG);
            }

            // Operations which pop one and have no operand bytes...

            case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, MONITORENTER, MONITOREXIT -> {
                stackTypes.pop();
            }

            // Operations which pop one and push one int...

            case ARRAYLENGTH -> {
                stackTypes.setLast(TAG_INT);
            }

            case INSTANCEOF -> {
                offset += 2;
                stackTypes.setLast(TAG_INT);
            }

            // Operations which pop two and push an int...

            case IADD, ISUB, IMUL, IDIV, IREM, ISHL, ISHR, IUSHR, IAND, IOR, IXOR,
                IALOAD, BALOAD, CALOAD, SALOAD,
                LCMP, FCMPL, FCMPG, DCMPL, DCMPG ->
            {
                stackTypes.pop(2);
                stackTypes.push(TAG_INT);
            }

            // Operations which pop two and push a float...

            case FADD, FSUB, FMUL, FDIV, FREM, FALOAD -> {
                stackTypes.pop(2);
                stackTypes.push(TAG_FLOAT);
            }

            // Operations which pop two and push a double...

            case DADD, DSUB, DMUL, DDIV, DREM, DALOAD -> {
                stackTypes.pop(2);
                stackTypes.push(TAG_DOUBLE);
            }

            // Operations which pop two and push a long...

            case LADD, LSUB, LMUL, LDIV, LREM, LSHL, LSHR, LUSHR, LAND, LOR, LXOR, LALOAD -> {
                stackTypes.pop(2);
                stackTypes.push(TAG_LONG);
            }

            // Operations which pop three and have no operand bytes...

            case IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE -> {
                stackTypes.pop(3);
            }

            // Operations which store to a local variable...

            case ISTORE_0, FSTORE_0, ASTORE_0, LSTORE_0, DSTORE_0  -> {
                localTypes.set(0, stackTypes.pop());
            }

            case ISTORE_1, FSTORE_1, ASTORE_1, LSTORE_1, DSTORE_1  -> {
                localTypes.set(1, stackTypes.pop());
            }

            case ISTORE_2, FSTORE_2, ASTORE_2, LSTORE_2, DSTORE_2  -> {
                localTypes.set(2, stackTypes.pop());
            }

            case ISTORE_3, FSTORE_3, ASTORE_3, LSTORE_3, DSTORE_3  -> {
                localTypes.set(3, stackTypes.pop());
            }

            case ISTORE, FSTORE, ASTORE, LSTORE, DSTORE -> {
                int index = buffer[offset++] & 0xff;
                localTypes.set(index, stackTypes.pop());
            }

            // Operations which load to an object variable...

            case ALOAD_0 -> {
                stackTypes.push(localTypes.get(0));
            }

            case ALOAD_1 -> {
                stackTypes.push(localTypes.get(1));
            }

            case ALOAD_2 -> {
                stackTypes.push(localTypes.get(2));
            }

            case ALOAD_3 -> {
                stackTypes.push(localTypes.get(3));
            }

            case ALOAD -> {
                int index = buffer[offset++] & 0xff;
                stackTypes.push(localTypes.get(index));
            }

            // Conversions...

            case L2I, F2I, D2I -> { stackTypes.setLast(TAG_INT); }
            case I2F, L2F, D2F -> { stackTypes.setLast(TAG_FLOAT); }
            case I2L, F2L, D2L -> { stackTypes.setLast(TAG_LONG); }
            case I2D, L2D, F2D -> { stackTypes.setLast(TAG_DOUBLE); }

            // Comparison operations which pop one and have two operand bytes...

            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL, PUTSTATIC -> {
                offset += 2;
                stackTypes.pop();
            }

            // Comparison operations which pop two and have two operand bytes...

            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                IF_ACMPEQ, IF_ACMPNE, PUTFIELD ->
            {
                offset += 2;
                stackTypes.pop(2);
            }

            // Loading constants...

            case LDC -> {
                int index = buffer[offset++] & 0xff;
                stackTypes.push(cp.findConstant(index).smTag(cp));
            }

            case LDC_W, LDC2_W -> {
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;
                stackTypes.push(cp.findConstant(index).smTag(cp));
            }

            // Stack manipulation...

            case POP -> {
                stackTypes.pop();
            }

            case POP2 -> {
                int tag = stackTypes.pop();
                if (!isWideType(tag)) {
                    stackTypes.pop();
                }
            }

            case DUP -> {
                stackTypes.push(stackTypes.getLast());
            }

            case DUP_X1 -> {
                int tag1 = stackTypes.pop();
                int tag2 = stackTypes.pop();
                stackTypes.push(tag1);
                stackTypes.push(tag2);
                stackTypes.push(tag1);
            }

            case DUP_X2 -> {
                int tag1 = stackTypes.pop();
                int tag2 = stackTypes.pop();
                if (isWideType(tag2)) {
                    // Form 2.
                    stackTypes.push(tag1);
                } else {
                    // Form 1.
                    int tag3 = stackTypes.pop();
                    stackTypes.push(tag1);
                    stackTypes.push(tag3);
                }
                stackTypes.push(tag2);
                stackTypes.push(tag1);
            }

            case DUP2 -> {
                int tag1 = stackTypes.pop();
                if (isWideType(tag1)) {
                    // Form 2.
                    stackTypes.push(tag1);
                } else {
                    // Form 1.
                    int tag2 = stackTypes.pop();
                    stackTypes.push(tag2);
                    stackTypes.push(tag1);
                    stackTypes.push(tag2);
                }
                stackTypes.push(tag1);
            }

            case DUP2_X1 -> {
                int tag1 = stackTypes.pop();
                int tag2 = stackTypes.pop();
                if (isWideType(tag1)) {
                    // Form 2.
                    stackTypes.push(tag1);
                } else {
                    // Form 1.
                    int tag3 = stackTypes.pop();
                    stackTypes.push(tag2);
                    stackTypes.push(tag1);
                    stackTypes.push(tag3);
                }
                stackTypes.push(tag2);
                stackTypes.push(tag1);
            }

            case DUP2_X2 -> {
                int tag1 = stackTypes.pop();
                int tag2 = stackTypes.pop();
                if (isWideType(tag1) && isWideType(tag2)) {
                    // Form 4.
                    stackTypes.push(tag1);
                } else {
                    int tag3 = stackTypes.pop();
                    if (isWideType(tag3)) {
                        // Form 3.
                        stackTypes.push(tag2);
                        stackTypes.push(tag1);
                    } else if (isWideType(tag1)) {
                        // Form 2.
                        stackTypes.push(tag1);
                    } else {
                        // Form 1.
                        int tag4 = stackTypes.pop();
                        stackTypes.push(tag2);
                        stackTypes.push(tag1);
                        stackTypes.push(tag4);
                    }
                    stackTypes.push(tag3);
                }
                stackTypes.push(tag2);
                stackTypes.push(tag1);
            }

            case SWAP -> {
                int tag1 = stackTypes.pop();
                int tag2 = stackTypes.pop();
                stackTypes.push(tag1);
                stackTypes.push(tag2);
            }

            // Unclassified operations...

            case ACONST_NULL -> {
                stackTypes.push(TAG_NULL);
            }

            case AALOAD -> {
                stackTypes.pop(); // pop the array index
                int tag = stackTypes.pop(); // pop the array type (assume TAG_OBJECT)
                C_Class type = cp.findConstantClass(tag >>> 16); // extract cpool_index
                ClassDesc componentType = type.mValue.asClassDesc().componentType();
                stackTypes.push(tagForType(cp, componentType));
            }

            case TABLESWITCH -> {
                offset += switchPad(offset - startOffset);
                offset += 4; // skip the default jump target
                long lowValue = decodeIntBE(buffer, offset); offset += 4;
                long highValue = decodeIntBE(buffer, offset); offset += 4;
                // skip the jump targets
                offset = Math.toIntExact(offset + (highValue - lowValue + 1L) * 4L);
                stackTypes.pop();
            }

            case LOOKUPSWITCH -> {
                offset += switchPad(offset - startOffset);
                offset += 4; // skip the default jump target
                // skip the value and jump target pairs
                int npairs = decodeIntBE(buffer, offset); offset += 4;
                offset = Math.toIntExact(offset + npairs * 8L);
                stackTypes.pop();
            }

            case GETSTATIC, GETFIELD -> {
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;
                if (op == GETFIELD) {
                    stackTypes.pop();
                }
                C_NameAndType nat = cp.findConstant(index, C_MemberRef.class).mNameAndType;
                ClassDesc type = nat.mTypeDesc.asClassDesc();
                stackTypes.push(tagForType(cp, type));
            }

            case INVOKEVIRTUAL, INVOKESTATIC, INVOKEINTERFACE, INVOKEDYNAMIC -> {
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;
                if (op == INVOKEINTERFACE || op == INVOKEDYNAMIC) {
                    offset += 2; // skip the extra fields
                }
                C_NameAndType nat;
                if (op != INVOKEDYNAMIC) {
                    nat = cp.findConstant(index, C_MemberRef.class).mNameAndType;
                } else {
                    nat = cp.findConstant(index, C_Dynamic.class).mNameAndType;
                }
                MethodTypeDesc desc = nat.mTypeDesc.asMethodTypeDesc();
                int count = desc.parameterCount();
                if (op != INVOKESTATIC && op != INVOKEDYNAMIC) {
                    count++;
                }
                stackTypes.pop(count);
                ClassDesc retTypeDesc = desc.returnType();
                if (!"V".equals(retTypeDesc.descriptorString())) {
                    stackTypes.push(tagForType(cp, retTypeDesc));
                }
            }

            case INVOKESPECIAL -> {
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;
                C_NameAndType nat = cp.findConstant(index, C_MemberRef.class).mNameAndType;
                MethodTypeDesc desc = nat.mTypeDesc.asMethodTypeDesc();
                stackTypes.pop(desc.parameterCount());
                int instanceType = stackTypes.pop();
                ClassDesc retTypeDesc = desc.returnType();
                if (!"V".equals(retTypeDesc.descriptorString())) {
                    stackTypes.push(tagForType(cp, retTypeDesc));
                }
                if (nat.mName.isConstructor()) {
                    int tag = instanceType & 0xffff;
                    if (tag == TAG_UNINIT_THIS) {
                        if (localTypes.length() > 0 && localTypes.get(0) == TAG_UNINIT_THIS) {
                            localTypes.set(0, TAG_OBJECT | (thisClassIndex << 16));
                        }
                    } else if (tag == TAG_UNINIT) {
                        int newTypeOffset = startOffset + (instanceType >>> 16) + 1;
                        int newTypeIndex = decodeUnsignedShortBE(buffer, newTypeOffset);
                        // Store cpool_index in the upper word.
                        stackTypes.replaceAll(instanceType, TAG_OBJECT | (newTypeIndex << 16));
                    }
                }
            }

            case NEW -> {
                int newOpAddress = offset - startOffset - 1;
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;
                stackTypes.push(TAG_UNINIT | (newOpAddress << 16));
            }

            case NEWARRAY -> {
                int type = buffer[offset++] & 0xff;
                char desc = switch (type) {
                    default -> throw new ClassFormatException();
                    case 4 -> 'Z'; case 5 -> 'C'; case  6 -> 'F'; case  7 -> 'D';
                    case 8 -> 'B'; case 9 -> 'S'; case 10 -> 'I'; case 11 -> 'J';
                };
                stackTypes.setLast(tagForType(cp.addClass("" + '[' + desc)));
            }

            case ANEWARRAY -> {
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;
                String type = cp.findConstantClass(index).mValue.str();
                if (type.charAt(0) == '[') {
                    type = '[' + type;
                } else {
                    type = "" + '[' + 'L' + type + ';';
                }
                stackTypes.setLast(tagForType(cp.addClass(type)));
            }

            case ATHROW -> {
                stackTypes.length(0);
            }

            case CHECKCAST -> {
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;
                stackTypes.setLast(tagForType(cp.findConstantClass(index)));
            }

            case WIDE -> {
                op = buffer[offset++];
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;

                doWide: {
                    int tag;
                    switch (op) {
                        default -> throw new ClassFormatException();
                        case IINC -> {
                            offset += 2;
                            break doWide;
                        }
                        case RET -> {
                            offset++;
                            break doWide;
                        }
                        case ISTORE, FSTORE, ASTORE, LSTORE, DSTORE -> {
                            localTypes.set(index, stackTypes.pop());
                            break doWide;
                        }
                        case ILOAD -> { tag = TAG_INT; }
                        case FLOAD -> { tag = TAG_FLOAT; }
                        case LLOAD -> { tag = TAG_LONG; }
                        case DLOAD -> { tag = TAG_DOUBLE; }
                        case ALOAD -> { tag = localTypes.get(index); }
                    }

                    stackTypes.push(tag);
                }
            }

            case MULTIANEWARRAY -> {
                int index = decodeUnsignedShortBE(buffer, offset); offset += 2;
                int dims = buffer[offset++] & 0xff;
                stackTypes.pop(dims);
                stackTypes.push(tagForType(cp.findConstantClass(index)));
            }

            }
        }

        mEntries.put(key, new Entry(collapseTypes(localTypes), stackTypes));

        return true;
    }
}
