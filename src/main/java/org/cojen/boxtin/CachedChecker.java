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

/**
 * Checks if access to a class member is allowed or denied, and caches the result.
 *
 * @author Brian S. O'Neill
 */
public abstract class CachedChecker implements Checker {
    protected final Module module;
    protected final ClassLoader loader;

    private final MemberRefClassCache<Class<?>, ClassNotFoundException> mClassCache =
        new MemberRefClassCache<>()
    {
        @Override
        protected Class<?> newValue(MemberRef lookupKey, byte[] newKey)
            throws ClassNotFoundException
        {
            String name;
            try {
                name = lookupKey.decodeClassString();
            } catch (IOException e) {
                throw new ClassNotFoundException(e.toString());
            }

            return Class.forName(name.replace('/', '.'), false, loader);
        }
    };

    private final MemberRefCache<Boolean, ClassNotFoundException> mConstructorCache =
        new MemberRefCache<>()
    {
        protected Boolean newValue(MemberRef ctorRef, byte[] newKey)
            throws ClassNotFoundException
        {
            return findAndCheckConstructorAccess(ctorRef);
        }
    };

    private final MemberRefCache<Boolean, ClassNotFoundException> mMethodCache =
        new MemberRefCache<>()
    {
        protected Boolean newValue(MemberRef methodRef, byte[] newKey)
            throws ClassNotFoundException
        {
            return findAndCheckMethodAccess(methodRef, false);
        }
    };

    private final MemberRefCache<Boolean, ClassNotFoundException> mVirtualMethodCache =
        new MemberRefCache<>()
    {
        protected Boolean newValue(MemberRef methodRef, byte[] newKey)
            throws ClassNotFoundException
        {
            return findAndCheckMethodAccess(methodRef, true);
        }
    };

    private final MemberRefCache<Boolean, ClassNotFoundException> mFieldCache =
        new MemberRefCache<>()
    {
        protected Boolean newValue(MemberRef fieldRef, byte[] newKey)
            throws ClassNotFoundException
        {
            return findAndCheckFieldAccess(fieldRef);
        }
    };

    /**
     * @param module the caller module
     * @param loader the caller class loader; is used to check for inherited members; can be
     * null for the bootstrap loader
     */
    public CachedChecker(Module module, ClassLoader loader) {
        this.module = module;
        this.loader = loader;
    }

    /*
      Note regarding the ClassNotFoundException behavor:

      If a dependent class isn't found, then it might be assumed that upon being loaded the
      class being checked will throw a NoClassDefFoundError. By allowing access, the correct
      error will be thrown. This is risky, because the ClassLoader implementation might not
      consistently throw a ClassNotFoundException for a given class name.

      For this reason, a ClassNotFoundException results in a denial, and thus a
      SecurityException is thrown instead. This might be confusing, and so at some point it
      might make sense to include additional information in the SecurityException message.
     */

    @Override
    public final boolean isConstructorAllowed(MemberRef ctorRef) {
        // See findAndCheckConstructorAccess.
        try {
            return mConstructorCache.obtain(ctorRef);
        } catch (ClassNotFoundException e) {
            // Deny access, but don't cache the result.
            return false;
        }
    }

    @Override
    public final boolean isMethodAllowed(MemberRef methodRef) {
        // See findAndCheckMethodAccess.
        try {
            return mMethodCache.obtain(methodRef);
        } catch (ClassNotFoundException e) {
            // Deny access, but don't cache the result.
            return false;
        }
    }

    @Override
    public final boolean isVirtualMethodAllowed(MemberRef methodRef) {
        // See findAndCheckMethodAccess.
        try {
            return mVirtualMethodCache.obtain(methodRef);
        } catch (ClassNotFoundException e) {
            // Deny access, but don't cache the result.
            return false;
        }
    }

    @Override
    public final boolean isFieldAllowed(MemberRef fieldRef) {
        // See findAndCheckFieldAccess.
        try {
            return mFieldCache.obtain(fieldRef);
        } catch (ClassNotFoundException e) {
            // Deny access, but don't cache the result.
            return false;
        }
    }

    /**
     * @return true if allowed, false if denied
     * @throws ClassNotFoundException if a required class wasn't found, and so the constructor
     * is implicitly denied
     */
    protected abstract boolean checkConstructorAccess(MemberRef ctorRef)
        throws ClassNotFoundException;

    /**
     * @return true if allowed, false if denied
     * @throws ClassNotFoundException if a required class wasn't found, and so the method is
     * implicitly denied
     */
    protected abstract boolean checkMethodAccess(MemberRef methodRef)
        throws ClassNotFoundException;

    /**
     * @return true if allowed, false if denied
     * @throws ClassNotFoundException if a required class wasn't found, and so the field is
     * implicitly denied
     */
    protected abstract boolean checkFieldAccess(MemberRef fieldRef)
        throws ClassNotFoundException;

    protected Class<?> findClass(MemberRef ref) throws ClassNotFoundException {
        return mClassCache.obtain(ref);
    }

    /**
     * Returns true if the class is in the caller's module.
     */
    private boolean sameModule(Class<?> clazz) {
        return clazz.getModule() == module;
    }

    /**
     * @return true if allowed, false if denied
     */
    private boolean findAndCheckConstructorAccess(MemberRef methodRef)
        throws ClassNotFoundException
    {
        Class<?> clazz = findClass(methodRef);

        if (sameModule(clazz)) {
            // Security checks don't apply within the module itself.
            return true;
        }

        return checkConstructorAccess(methodRef);
    }

    /**
     * @param virtual when true, an access is also allowed when inherited as such
     * @return true if allowed, false if denied
     */
    private boolean findAndCheckMethodAccess(MemberRef methodRef, boolean virtual)
        throws ClassNotFoundException
    {
        Class<?> clazz = findClass(methodRef);

        if (sameModule(clazz)) {
            // Security checks don't apply within the module itself.
            return true;
        }

        // Find where the method is declared and check against that.

        if (MemberFinder.forClass(clazz).get(methodRef) == Boolean.TRUE) {
            boolean allowed = checkMethodAccess(methodRef);
            if (allowed || !virtual) {
                return allowed;
            }
        }

        MemberRef dstRef = null;

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            dstRef = withClass(dstRef, superclass.getName(), methodRef);
            if (isMethodAllowed(dstRef)) {
                return true;
            }
        }
 
        for (Class<?> iface : clazz.getInterfaces()) {
            dstRef = withClass(dstRef, iface.getName(), methodRef);
            if (isMethodAllowed(dstRef)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true if allowed, false if denied
     */
    private boolean findAndCheckFieldAccess(MemberRef fieldRef) throws ClassNotFoundException {
        Class<?> clazz = findClass(fieldRef);

        if (sameModule(clazz)) {
            // Security checks don't apply within the module itself.
            return true;
        }

        // Find where the field is declared and check against that.

        if (MemberFinder.forClass(clazz).get(fieldRef) == Boolean.TRUE) {
            return checkFieldAccess(fieldRef);
        }

        MemberRef dstRef = null;

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            dstRef = withClass(dstRef, superclass.getName(), fieldRef);
            if (isFieldAllowed(dstRef)) {
                return true;
            }
        }
 
        for (Class<?> iface : clazz.getInterfaces()) {
            dstRef = withClass(dstRef, iface.getName(), fieldRef);
            if (isFieldAllowed(dstRef)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Copies the name and descriptor to the given destination ref (initially), and copies the
     * given class name into the destination ref. Note that the name and descriptor are only
     * copied when the given destination ref is null, or if it needs to expand.
     *
     * @param dstRef destination ref can be null initially
     * @param ref reference to copy the name and descriptor from
     * @return new or updated destination ref
     */
    private static MemberRef withClass(MemberRef dstRef, String className, MemberRef ref) {
        BasicEncoder classEncoder;
        try {
            className = className.replace('.', '/');
            classEncoder = new BasicEncoder(className.length() + 10);
            classEncoder.writeUTF(className);
        } catch (IOException e) {
            // Not expected.
            throw new UncheckedIOException(e);
        }

        int classLen = classEncoder.length(); // includes the length prefix field

        int bufLen = ref.nameLength() + ref.descriptorLength() + classLen;

        byte[] dstBuffer;

        if (dstRef == null || bufLen > (dstBuffer = dstRef.buffer()).length) {
            dstBuffer = new byte[bufLen + (2 + 2) + 50]; // +50 for extra growth
            dstRef = new MemberRef(dstBuffer);

            byte[] srcBuffer = ref.buffer();

            dstRef.nameOffset(2); // just past the length prefix field
            int nameLength = ref.nameLength();
            System.arraycopy(srcBuffer, ref.nameOffset() - 2, dstBuffer, 0, nameLength + 2);
            int dstOffset = nameLength + 2;
            dstRef.nameLength(nameLength);

            dstRef.descriptorOffset(dstOffset + 2); // just past the length prefix field
            int descLength = ref.descriptorLength();
            System.arraycopy(srcBuffer, ref.descriptorOffset() - 2,
                             dstBuffer, dstOffset, descLength + 2);
            dstOffset += descLength + 2;
            dstRef.descriptorLength(descLength);

            dstRef.classOffset(dstOffset + 2); // just past the length prefix field
        }

        System.arraycopy(classEncoder.buffer(), 0, dstBuffer, dstRef.classOffset() - 2, classLen);
        dstRef.classLength(classLen - 2);

        return dstRef;
    }
}
