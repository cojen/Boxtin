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

/**
 * Defines a region of bytes in a class file which the ClassFileProcessor has replaced.
 *
 * @author Brian S. O'Neill
 */
interface RegionReplacement {
    /**
     * @throws IllegalStateException if nothing was actually replaced
     * @return the new length
     */
    long finish(ConstantPool cp, byte[] originalBuffer) throws IOException;

    long originalLength();

    /**
     * @throws IllegalStateException if not finished
     */
    void writeTo(BufferEncoder encoder) throws IOException;
}
