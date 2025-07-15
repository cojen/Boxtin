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

import java.io.File;
import java.io.FileOutputStream;

/**
 * Only used when SecurityAgent.DEBUG is true.
 *
 * @author Brian S. O'Neill
 */
class DebugWriter {
    static void write(ConstantPool cp, int classNameIndex, byte[] bytes) {
        String name = cp.findConstantClass(classNameIndex).mValue.str().replace('/', '.');
        File file = new File("Boxtin/" + name + ".class");
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            file = new File(tempDir, file.getPath());
            file.getParentFile().mkdirs();

            System.out.println("Boxtin writing to " + file);
            
            try (var out = new FileOutputStream(file)) {
                out.write(bytes);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
}
