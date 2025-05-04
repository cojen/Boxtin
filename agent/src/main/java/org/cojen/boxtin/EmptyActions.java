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

import java.lang.reflect.Method;

import java.util.*;

import java.util.stream.Stream;

import static org.cojen.boxtin.ConstantPool.C_NameAndType;

/**
 * 
 *
 * @author Brian S. O'Neill
 * @hidden
 */
public final class EmptyActions {
    static final String CLASS_NAME = EmptyActions.class.getName().replace('.', '/');

    /**
     * @param cp constant can be added into here
     * @param desc type descriptor 
     * @return method to call, or else null
     */
    static C_NameAndType findMethod(ConstantPool cp, String desc) {
        String name = desc.replace('/', '_');

        Method m;
        try {
            m = EmptyActions.class.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }

        return cp.addNameAndType(name, Utils.fullDescriptorFor(m));
    }

    public static Boolean java_lang_Boolean() {
        return false;
    }

    public static Byte java_lang_Byte() {
        return 0;
    }

    public static Character java_lang_Character() {
        return 0;
    }

    public static Short java_lang_Short() {
        return 0;
    }

    public static Integer java_lang_Integer() {
        return 0;
    }

    public static Long java_lang_Long() {
        return 0L;
    }

    public static Float java_lang_Float() {
        return 0.0f;
    }

    public static Double java_lang_Double() {
        return 0.0d;
    }

    public static String java_lang_String() {
        return "";
    }

    public static Iterable java_lang_Iterable() {
        return Collections.emptyList();
    }

    public static Optional java_util_Optional() {
        return Optional.empty();
    }

    public static OptionalDouble java_util_OptionalDouble() {
        return OptionalDouble.empty();
    }

    public static OptionalInt java_util_OptionalInt() {
        return OptionalInt.empty();
    }

    public static OptionalLong java_util_OptionalLong() {
        return OptionalLong.empty();
    }

    public static Collection java_util_Collection() {
        return Collections.emptyList();
    }

    public static Stream java_util_Stream() {
        return Stream.empty();
    }

    public static Enumeration java_util_Enumeration() {
        return Collections.emptyEnumeration();
    }

    public static Iterator java_util_Iterator() {
        return Collections.emptyIterator();
    }

    public static List java_util_List() {
        return Collections.emptyList();
    }

    public static ListIterator java_util_ListIterator() {
        return Collections.emptyListIterator();
    }

    public static Map java_util_Map() {
        return Collections.emptyMap();
    }

    public static NavigableMap java_util_NavigableMap() {
        return Collections.emptyNavigableMap();
    }

    public static NavigableSet java_util_NavigableSet() {
        return Collections.emptyNavigableSet();
    }

    public static Set java_util_Set() {
        return Collections.emptySet();
    }

    public static SortedMap java_util_SortedMap() {
        return Collections.emptySortedMap();
    }

    public static SortedSet java_util_SortedSet() {
        return Collections.emptySortedSet();
    }

    public static Spliterator java_util_Spliterator() {
        return Spliterators.emptySpliterator();
    }

    public static Spliterator.OfDouble java_util_Spliterator$OfDouble() {
        return Spliterators.emptyDoubleSpliterator();
    }

    public static Spliterator.OfInt java_util_Spliterator$OfInt() {
        return Spliterators.emptyIntSpliterator();
    }

    public static Spliterator.OfLong java_util_Spliterator$OfLong() {
        return Spliterators.emptyLongSpliterator();
    }
}
