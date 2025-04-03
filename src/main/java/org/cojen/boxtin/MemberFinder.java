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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import java.util.stream.Stream;

/**
 * Defines an immutable map of accessible Class methods and fields which are locally declared.
 * To be accessible, it must be public or protected. The map values are always TRUE, indicating
 * that the member exists.
 *
 * <p>When finding entries in the map, only the name and descriptor portions of the MemberRef
 * are examined.
 *
 * @author Brian S. O'Neill
 */
final class MemberFinder extends ImmutableLookupMap<MemberRef, MemberFinder.Key, Boolean> {

    private static final SoftCache<Class<?>, MemberFinder> cCache = new SoftCache<>();

    /**
     * Returns a finder for the given class.
     */
    static MemberFinder forClass(Class<?> clazz) {
        MemberFinder finder = cCache.get(clazz);

        if (finder != null) {
            return finder;
        }

        synchronized (cCache) {
            finder = cCache.get(clazz);
            if (finder != null) {
                return finder;
            }
        }

        finder = make(clazz);
        cCache.put(clazz, finder);

        return finder;
    }

    private static MemberFinder make(Class<?> clazz) {
        List<Map.Entry<Key, Boolean>> methods = Stream.of(clazz.getDeclaredMethods())
            .filter(Utils::isAccessible).map(m -> Map.entry(new Key(m), true)).toList();

        List<Map.Entry<Key, Boolean>> fields = Stream.of(clazz.getDeclaredFields())
            .filter(Utils::isAccessible).map(f -> Map.entry(new Key(f), true)).toList();

        return new MemberFinder(methods.size() + fields.size(),
                                Stream.concat(methods.stream(), fields.stream()));
    }

    /**
     * @param capacity fixed amount of entries to hold
     * @param populator provides the map entries; must not have any duplicate keys
     */
    private MemberFinder(int capacity, Stream<Map.Entry<Key, Boolean>> populator) {
        super(capacity, populator);
    }

    @Override
    protected int lookupHash(MemberRef lookupKey) {
        // Examine the name and descriptor.
        int hash = Utils.hash
            (lookupKey.buffer(), lookupKey.nameOffset(), lookupKey.nameLength());
        return hash * 31 + Utils.hash
            (lookupKey.buffer(), lookupKey.descriptorOffset(), lookupKey.descriptorLength());
    }

    @Override
    protected int storedHash(Key storedKey) {
        return Utils.hash(storedKey.name) * 31 + Utils.hash(storedKey.descriptor);
    }

    @Override
    protected boolean matches(MemberRef lookupKey, Key existingKey) {
        // Examine the name and descriptor.

        int offset = lookupKey.nameOffset();

        if (!Arrays.equals(lookupKey.buffer(), offset, offset + lookupKey.nameLength(),
                           existingKey.name, 0, existingKey.name.length))
        {
            return false;
        }

        offset = lookupKey.descriptorOffset();

        return Arrays.equals(lookupKey.buffer(), offset, offset + lookupKey.descriptorLength(),
                             existingKey.descriptor, 0, existingKey.descriptor.length);
    }

    static final class Key {
        private final byte[] name, descriptor;

        private Key(String name, String descriptor) {
            this.name = UTFEncoder.encode(name);
            this.descriptor = UTFEncoder.encode(descriptor);
        }

        private Key(Method m) {
            this(m.getName(), methodDescriptor(m));
        }

        private Key(Field f) {
            this(f.getName(), f.getType().descriptorString());
        }

        private static String methodDescriptor(Method m) {
            // Special handling is required for signature polymorphic methods in VarHandle and
            // MethodHandle. Use an empty descriptor for those methods, and special handling is
            // also required when finding these methods.

            Class<?> clazz = m.getDeclaringClass();

            if (clazz == VarHandle.class) {
                if (onlyVarArgs(m)) {
                    return "";
                }
            } else if (clazz == MethodHandle.class) {
                if (onlyVarArgs(m) && !m.getName().equals("invokeWithArguments")) {
                    return "";
                }
            }

            return Utils.fullDescriptorFor(m.getReturnType(), m.getParameterTypes());
        }

        private static boolean onlyVarArgs(Method m) {
            return m.isVarArgs() && m.getParameterTypes().length == 1;
        }
    }
}
