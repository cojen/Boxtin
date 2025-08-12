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
import java.io.UncheckedIOException;

import java.lang.reflect.Modifier;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class EmptyClassMaker {
    static byte[] make(String name) {
        try {
            return doMake(name);
        } catch (IOException e) {
            // Not expected.
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] doMake(String name) throws IOException {
        var cp = new ConstantPool();

        ConstantPool.C_Class this_class = cp.addClass(name.replace('.', '/'));
        ConstantPool.C_Class super_class = cp.addClass(Object.class.getName().replace('.', '/'));

        var encoder = new BufferEncoder(100);

        encoder.writeInt(0xCAFEBABE);
        encoder.writeShort(0); // minor_version
        encoder.writeShort(51); // major_version: Java 7

        cp.writeTo(encoder);

        encoder.writeShort(Modifier.PUBLIC); // access_flags
        encoder.writeShort(this_class.mIndex);
        encoder.writeShort(super_class.mIndex);
        encoder.writeShort(0); // interfaces_count
        encoder.writeShort(0); // fields_count
        encoder.writeShort(0); // methods_count
        encoder.writeShort(0); // attributes_count

        return encoder.toByteArray();
    }
}
