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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * Checks if access to a class member is allowed or denied, and caches the result.
 *
 * @author Brian S. O'Neill
 */
public abstract class Checker {
    private final Module mModule;

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

            return Class.forName(name.replace('/', '.'), false, mModule.getClassLoader());
        }
    };

    private final MemberRefCache<Boolean, ClassNotFoundException> mConstructorCache =
        new MemberRefCache<>()
    {
        @Override
        protected Boolean newValue(MemberRef ctorRef, byte[] newKey)
            throws ClassNotFoundException
        {
            return findAndCheckConstructorAccess(ctorRef);
        }
    };

    private final MemberRefCache<Boolean, ClassNotFoundException> mMethodCache =
        new MemberRefCache<>()
    {
        @Override
        protected Boolean newValue(MemberRef methodRef, byte[] newKey)
            throws ClassNotFoundException
        {
            return findAndCheckMethodAccess(methodRef, false);
        }
    };

    private final MemberRefCache<Boolean, ClassNotFoundException> mVirtualMethodCache =
        new MemberRefCache<>()
    {
        @Override
        protected Boolean newValue(MemberRef methodRef, byte[] newKey)
            throws ClassNotFoundException
        {
            return findAndCheckMethodAccess(methodRef, true);
        }
    };

    private final MemberRefCache<Boolean, ClassNotFoundException> mFieldCache =
        new MemberRefCache<>()
    {
        @Override
        protected Boolean newValue(MemberRef fieldRef, byte[] newKey)
            throws ClassNotFoundException
        {
            return findAndCheckFieldAccess(fieldRef);
        }
    };

    /**
     * @param module the caller module
     */
    Checker(Module module) {
        mModule = module;
    }

    // Note: The public methods are intended for testing, and they're not at fast as the
    // variants that act directly on a MemberRef, which are used by ClassFileProcessor.

    public final boolean isConstructorAllowed(String className, String descriptor)
        throws ClassNotFoundException
    {
        return isConstructorAllowed(new MemberRef(className, "<init>", descriptor));
    }

    public final boolean isConstructorAllowed(Class clazz, Class<?>... paramTypes)
        throws ClassNotFoundException
    {
        return isConstructorAllowed(clazz.getName(),
                                    Utils.fullDescriptorFor(void.class, paramTypes));
    }

    public final boolean isMethodAllowed(String className, String descriptor, String name)
        throws ClassNotFoundException
    {
        return isMethodAllowed(new MemberRef(className, name, descriptor));
    }

    public final boolean isMethodAllowed(Class clazz, Class<?> returnType, String name,
                                         Class<?>... paramTypes)
        throws ClassNotFoundException
    {
        return isMethodAllowed(clazz.getName(),
                               Utils.fullDescriptorFor(returnType, paramTypes), name);
    }

    public final boolean isVirtualMethodAllowed(String className, String descriptor, String name)
        throws ClassNotFoundException
    {
        return isVirtualMethodAllowed(new MemberRef(className, name, descriptor));
    }

    public final boolean isVirtualMethodAllowed(Class clazz, Class<?> returnType, String name,
                                                Class<?>... paramTypes)
        throws ClassNotFoundException
    {
        return isVirtualMethodAllowed(clazz.getName(),
                                      Utils.fullDescriptorFor(returnType, paramTypes), name);
    }

    public final boolean isFieldAllowed(String className, String typeDesc, String name)
        throws ClassNotFoundException
    {
        return isFieldAllowed(new MemberRef(className, name, typeDesc));
    }

    public final boolean isFieldAllowed(Class clazz, Class type, String name)
        throws ClassNotFoundException
    {
        return isFieldAllowed(clazz.getName(), type.descriptorString(), name);
    }

    final boolean isConstructorAllowed(MemberRef ctorRef) throws ClassNotFoundException {
        // See findAndCheckConstructorAccess.
        return mConstructorCache.obtain(ctorRef);
    }

    final boolean isMethodAllowed(MemberRef methodRef) throws ClassNotFoundException {
        // See findAndCheckMethodAccess.
        return mMethodCache.obtain(methodRef);
    }

    final boolean isVirtualMethodAllowed(MemberRef methodRef) throws ClassNotFoundException {
        // See findAndCheckMethodAccess.
        return mVirtualMethodCache.obtain(methodRef);
    }

    final boolean isFieldAllowed(MemberRef fieldRef) throws ClassNotFoundException {
        // See findAndCheckFieldAccess.
        return mFieldCache.obtain(fieldRef);
    }

    /**
     * @return true if allowed, false if denied
     * @throws ClassNotFoundException if a required class wasn't found, and so the constructor
     * is implicitly denied
     */
    abstract boolean checkConstructorAccess(MemberRef ctorRef) throws ClassNotFoundException;

    /**
     * @return true if allowed, false if denied
     * @throws ClassNotFoundException if a required class wasn't found, and so the method is
     * implicitly denied
     */
    abstract boolean checkMethodAccess(MemberRef methodRef) throws ClassNotFoundException;

    /**
     * @return true if allowed, false if denied
     * @throws ClassNotFoundException if a required class wasn't found, and so the field is
     * implicitly denied
     */
    abstract boolean checkFieldAccess(MemberRef fieldRef) throws ClassNotFoundException;

    private Class<?> findClass(MemberRef ref) throws ClassNotFoundException {
        return mClassCache.obtain(ref);
    }

    /**
     * Returns true if the class is in the caller's module.
     */
    private boolean sameModule(Class<?> clazz) {
        return clazz.getModule() == mModule;
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

        MemberFinder finder = MemberFinder.forClass(clazz);
        if (finder.get(methodRef) == Boolean.TRUE ||
            (virtual && findSignaturePolymorphic(methodRef, clazz, finder)))
        {
            boolean allowed = checkMethodAccess(methodRef);

            // For virtual methods, being denied where it's declared isn't sufficient. The
            // method can still be allowed by inheritence.

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

     private boolean findSignaturePolymorphic(MemberRef methodRef,
                                              Class<?> clazz, MemberFinder finder)
     {
        // Special handling for signature polymorphic methods in VarHandle and MethodHandle.
        // They will have been stored in the finder with an empty descriptor.

        if (clazz == VarHandle.class || clazz == MethodHandle.class) {
            final int originalLength = methodRef.descriptorLength();
            try {
                methodRef.descriptorLength(0);
                return finder.get(methodRef) == Boolean.TRUE;
            } finally {
                methodRef.descriptorLength(originalLength);
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
        BasicEncoder classEncoder = UTFEncoder.localEncoder();
        try {
            classEncoder.writeUTF(className.replace('.', '/'));
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
