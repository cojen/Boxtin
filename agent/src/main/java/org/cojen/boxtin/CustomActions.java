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
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import java.nio.ByteBuffer;

import java.security.ProtectionDomain;

import java.util.Arrays;
import java.util.Properties;

/**
 * Used by JavaBaseApplier and ReflectionApplier for selecting custom deny actions. The methods
 * must be accessible, which is why a separate class is used.
 * 
 * @author Brian S. O'Neill
 * @hidden
 */
public final class CustomActions {
    static MethodType mt(Class<?> rtype, Class<?>... ptypes) {
        return MethodType.methodType(rtype, ptypes);
    }

    static MethodHandleInfo findMethod(MethodHandles.Lookup lookup, String name, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        return lookup.revealDirect(lookup.findStatic(CustomActions.class, name, mt));
    }

    static MethodHandleInfo findMethod(MethodHandles.Lookup lookup, String name,
                                       Class<?> rtype, Class<?>... ptypes)
        throws NoSuchMethodException, IllegalAccessException
    {
        return findMethod(lookup, name, mt(rtype, ptypes));
    }

    // Custom deny action for Integer.getInteger.
    public static Integer intValue(String name, int defaultValue) {
        return defaultValue;
    }

    // Custom deny action for Integer.getInteger.
    public static Integer intValue(String name, Integer defaultValue) {
        return defaultValue;
    }

    // Custom deny action for Long.getLong.
    public static Long longValue(String name, long defaultValue) {
        return defaultValue;
    }

    // Custom deny action for Long.getLong.
    public static Long longValue(String name, Long defaultValue) {
        return defaultValue;
    }

    // Custom deny action for System.getProperties.
    public static Properties getProperties(Caller caller) {
        return FilteredProperties.getProperties(caller.validate().getModule());
    }

    // Custom deny action for System.getProperty.
    public static String getProperty(Caller caller, String name) {
        return FilteredProperties.getProperty(caller.validate().getModule(), name);
    }

    // Custom deny action for System.getProperty.
    public static String getProperty(Caller caller, String name, String def) {
        return FilteredProperties.getProperty(caller.validate().getModule(), name, def);
    }

    // Custom deny action for System.setProperties.
    public static void setProperties(Caller caller, Properties props) {
        FilteredProperties.setProperties(caller.validate().getModule(), props);
    }

    // Custom deny action for System.setProperty.
    public static String setProperty(Caller caller, String name, String value) {
        return FilteredProperties.setProperty(caller.validate().getModule(), name, value);
    }

    // Custom deny action for System.clearProperty.
    public static String clearProperty(Caller caller, String name) {
        return FilteredProperties.clearProperty(caller.validate().getModule(), name);
    }

    // Check for various Thread operations.
    public static boolean checkThreadNew(Thread thread) {
        // Allowed when the thread is new and hasn't started yet.
        return thread.getState() == Thread.State.NEW;
    }

    // Check for ClassLoader.defineClass.
    public static boolean checkDefineClass(Caller caller, ClassLoader loader,
                                           String name, byte[] b, int off, int len,
                                           ProtectionDomain protectionDomain)
    {
        caller.validate();
        // Allowed when no ProtectionDomain is given.
        return protectionDomain == null;
    }

    // Check for ClassLoader.defineClass.
    public static boolean checkDefineClass(Caller caller, ClassLoader loader,
                                           String name, ByteBuffer b,
                                           ProtectionDomain protectionDomain)
    {
        caller.validate();
        // Allowed when no ProtectionDomain is given.
        return protectionDomain == null;
    }

    // Check for Class.forName(String, boolean, ClassLoader).
    public static boolean checkForName(Caller caller,
                                       String name, boolean initialize, ClassLoader loader)
    {
        Class<?> callerClass = caller.validate();
        // Allowed when not asked to initialize or when the caller's loader is the same.
        return !initialize || callerClass.getClassLoader() == loader;
    }

    // Check for Class.getResource and getResourceAsStream.
    public static boolean checkGetResource(Caller caller, Class<?> clazz) {
        Module module = clazz.getModule();
        return module.isNamed() ? checkGetResource(caller, module)
            : checkGetResource(caller, clazz.getClassLoader());
    }

    // Check for ClassLoader.getResource, getResourceAsStream, getResources, and resources.
    public static boolean checkGetResource(Caller caller, ClassLoader loader) {
        // Allowed when the caller ClassLoader is the same as the ClassLoader being invoked.
        return caller.validate().getClassLoader() == loader;
    }

    // Check for Module.getResourceAsStream.
    public static boolean checkGetResource(Caller caller, Module module) {
        // Allowed when the caller Module is the same as the Module being invoked.
        return caller.validate().getModule() == module;
    }

    // Check for @Restricted methods: ModuleLayer.enableNativeAccess,
    // AddressLayout.withTargetLayout, Linker.downcallHandle, Linker.upcallStub,
    // MemorySegment.reinterpret, and SymbolLookup.libraryLookup, Runtime.load,
    // Runtime.loadLibrary, System.load, and System.loadLibrary
    public static boolean checkNativeAccess(Caller caller) {
        Class<?> callerClass = caller.validate();
        // Allowed for named modules for which access has been granted using the
        // --enable-native-access option.
        if (Runtime.getRuntime().version().feature() < 22) {
            return false;
        }
        Module module = callerClass.getModule();
        return module.isNamed() && module.isNativeAccessEnabled();
    }

    // Check for AccessibleObject.setAccessible.
    public static boolean checkSetAccessible(Caller caller, Object obj, boolean set) {
        Class<?> callerClass = caller.validate();

        if (!set) {
            // Allowed when not enabling access.
            return true;
        }

        if (obj instanceof Member m) {
            return checkSetAccessible(callerClass, m);
        } else if (obj instanceof Object[] array) {
            for (Object o : array) {
                if (!(o instanceof Member m) || !checkSetAccessible(callerClass, m)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private static boolean checkSetAccessible(Class<?> callerClass, Member m) {
        // Allowed when the caller Module is the same as the Module being accessed.
        return callerClass.getModule() == m.getDeclaringClass().getModule();
    }

    // Custom deny action for Class.getConstructor.
    public static <T> Constructor<T> getConstructor(Caller caller,
                                                    Class<T> clazz, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Class<?> callerClass = caller.validate();
        Constructor<T> ctor = clazz.getConstructor(paramTypes);
        checkEx(callerClass, ctor, null);
        return ctor;
    }

    // Custom deny action for Class.getConstructors.
    public static Constructor<?>[] getConstructors(Caller caller, Class<?> clazz) {
        Class<?> callerClass = caller.validate();
        return filter(callerClass, clazz, clazz.getConstructors());
    }

    // Custom deny action for Class.getDeclaredConstructor.
    public static <T> Constructor<T> getDeclaredConstructor
        (Caller caller, Class<T> clazz, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Class<?> callerClass = caller.validate();
        Constructor<T> ctor = clazz.getDeclaredConstructor(paramTypes);
        checkEx(callerClass, ctor, null);
        return ctor;
    }

    // Custom deny action for Class.getDeclaredConstructors.
    public static Constructor<?>[] getDeclaredConstructors(Caller caller, Class<?> clazz) {
        Class<?> callerClass = caller.validate();
        return filter(callerClass, clazz, clazz.getDeclaredConstructors());
    }

    // Custom deny action for Class.getDeclaredMethod.
    public static Method getDeclaredMethod(Caller caller, Class<?> clazz,
                                           String name, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Class<?> callerClass = caller.validate();
        Method method = clazz.getDeclaredMethod(name, paramTypes);
        checkEx(callerClass, method, name);
        return method;
    }

    // Custom deny action for Class.getDeclaredMethods.
    public static Method[] getDeclaredMethods(Caller caller, Class<?> clazz) {
        Class<?> callerClass = caller.validate();
        return filter(callerClass, clazz, clazz.getDeclaredMethods());
    }

    // Custom deny action for Class.getEnclosingConstructor.
    public static Constructor<?> getEnclosingConstructor(Caller caller, Class<?> clazz) {
        Class<?> callerClass = caller.validate();
        Constructor<?> ctor = clazz.getEnclosingConstructor();
        if (ctor != null) {
            checkErr(callerClass, ctor.getDeclaringClass(), null,
                     Utils.partialDescriptorFor(ctor.getParameterTypes()));
        }
        return ctor;
    }

    // Custom deny action for Class.getEnclosingMethod.
    public static Method getEnclosingMethod(Caller caller, Class<?> clazz) {
        Class<?> callerClass = caller.validate();
        Method method = clazz.getEnclosingMethod();
        if (method != null) {
            checkErr(callerClass, method.getDeclaringClass(), method.getName(),
                     Utils.partialDescriptorFor(method.getParameterTypes()));
        }
        return method;
    }

    // Custom deny action for Class.getMethod.
    public static Method getMethod(Caller caller, Class<?> clazz,
                                   String name, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Class<?> callerClass = caller.validate();
        Method method = clazz.getMethod(name, paramTypes);
        checkEx(callerClass, method, name);
        return method;
    }

    // Custom deny action for Class.getMethods.
    public static Method[] getMethods(Caller caller, Class<?> clazz) {
        Class<?> callerClass = caller.validate();
        return filter(callerClass, null, clazz.getMethods());
    }

    // Custom deny action for Class.getRecordComponents.
    public static RecordComponent[] getRecordComponents(Caller caller, Class<?> clazz) {
        Class<?> callerClass = caller.validate();

        RecordComponent[] components = clazz.getRecordComponents();

        if (callerClass.getModule() == clazz.getModule()) {
            return components;
        }

        RecordComponent[] filtered = null;
        int fpos = 0;

        for (int i=0; i<components.length; i++) {
            RecordComponent rc = components[i];
            Method m = rc.getAccessor();
            String desc = Utils.partialDescriptorFor(m.getParameterTypes());
            if (SecurityAgent.isAllowed(callerClass, clazz, m.getName(), desc)) {
                if (filtered != null) {
                    filtered[fpos++] = rc;
                }
            } else if (filtered == null) {
                filtered = components;
                fpos = i;
            }
        }

        if (filtered != null) {
            components = Arrays.copyOfRange(filtered, 0, fpos);
        }

        return components;
    }

    // Custom deny action for MethodHandle.Lookup.bind.
    public static MethodHandle lookupBind(Caller caller, MethodHandles.Lookup lookup,
                                          Object receiver, String name, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        Class<?> callerClass = caller.validate();
        MethodHandle mh = lookup.bind(receiver, name, mt);
        checkEx(callerClass, receiver.getClass(), name, mh.type());
        return mh;
    }

    // Custom deny action for MethodHandle.Lookup.findConstructor
    public static MethodHandle lookupFindConstructor(Caller caller, MethodHandles.Lookup lookup,
                                                     Class<?> clazz, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        Class<?> callerClass = caller.validate();
        MethodHandle mh = lookup.findConstructor(clazz, mt);
        checkEx(callerClass, lookup, mh);
        return mh;
    }

    // Custom deny action for MethodHandle.Lookup.findSpecial
    public static MethodHandle lookupFindSpecial(Caller caller, MethodHandles.Lookup lookup,
                                                 Class<?> clazz, String name, MethodType mt,
                                                 Class<?> specialCaller)
        throws NoSuchMethodException, IllegalAccessException
    {
        Class<?> callerClass = caller.validate();
        MethodHandle mh = lookup.findSpecial(clazz, name, mt, specialCaller);
        checkEx(callerClass, lookup, mh);
        return mh;
    }

    // Custom deny action for MethodHandle.Lookup.findStatic
    public static MethodHandle lookupFindStatic(Caller caller, MethodHandles.Lookup lookup,
                                                Class<?> clazz, String name, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        Class<?> callerClass = caller.validate();
        MethodHandle mh = lookup.findStatic(clazz, name, mt);
        checkEx(callerClass, lookup, mh);
        return mh;
    }

    // Custom deny action for MethodHandle.Lookup.findVirtual
    public static MethodHandle lookupFindVirtual(Caller caller, MethodHandles.Lookup lookup,
                                                 Class<?> clazz, String name, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        Class<?> callerClass = caller.validate();
        MethodHandle mh = lookup.findVirtual(clazz, name, mt);
        checkEx(callerClass, lookup, mh);
        return mh;
    }

    private static void checkErr(Class<?> callerClass, Class<?> target, String name, String desc)
        throws NoSuchMethodError
    {
        if (callerClass.getModule() != target.getModule() &&
            !SecurityAgent.isAllowed(callerClass, target, name, desc))
        {
            throw new NoSuchMethodError();
        }
    }

    private static void checkEx(Class<?> callerClass, Class<?> target, String name, String desc)
        throws NoSuchMethodException
    {
        if (callerClass.getModule() != target.getModule() &&
            !SecurityAgent.isAllowed(callerClass, target, name, desc))
        {
            throw new NoSuchMethodException();
        }
    }

    private static void checkEx(Class<?> callerClass, Class<?> target, String name, MethodType mt)
        throws NoSuchMethodException
    {
        checkEx(callerClass, target, name, Utils.partialDescriptorFor(mt));
    }

    private static void checkEx(Class<?> callerClass, Executable exec, String name)
        throws NoSuchMethodException
    {
        checkEx(callerClass, exec.getDeclaringClass(), name,
                Utils.partialDescriptorFor(exec.getParameterTypes()));
    }

    private static void checkEx(Class<?> callerClass, MethodHandles.Lookup lookup, MethodHandle mh)
        throws NoSuchMethodException
    {
        MethodHandleInfo info = lookup.revealDirect(mh);
        checkEx(callerClass, info.getDeclaringClass(), info.getName(), info.getMethodType());
    }

    /**
     * @param ctors can be trashed as a side effect
     */
    private static Constructor<?>[] filter(Class<?> callerClass,
                                           Class<?> clazz, Constructor<?>[] ctors) {
        if (callerClass.getModule() == clazz.getModule()) {
            return ctors;
        }

        Constructor<?>[] filtered = null;
        int fpos = 0;

        for (int i=0; i<ctors.length; i++) {
            Constructor<?> ctor = ctors[i];
            String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
            if (SecurityAgent.isAllowed(callerClass, clazz, null, desc)) {
                if (filtered != null) {
                    filtered[fpos++] = ctor;
                }
            } else if (filtered == null) {
                filtered = ctors;
                fpos = i;
            }
        }

        if (filtered != null) {
            ctors = Arrays.copyOfRange(filtered, 0, fpos);
        }

        return ctors;
    }

    /**
     * @param clazz pass null if the declaring class of the methods can vary
     * @param methods can be trashed as a side effect
     */
    private static Method[] filter(Class<?> callerClass, Class<?> clazz, Method[] methods) {
        if (clazz != null && callerClass.getModule() == clazz.getModule()) {
            return methods;
        }

        Method[] filtered = null;
        int fpos = 0;

        for (int i=0; i<methods.length; i++) {
            Method m = methods[i];

            boolean allowed;
            check: {
                Class<?> target = clazz;

                if (target == null) {
                    target = m.getDeclaringClass();
                    if (callerClass.getModule() == target.getModule()) {
                        allowed = true;
                        break check;
                    }
                }

                String desc = Utils.partialDescriptorFor(m.getParameterTypes());
                allowed = SecurityAgent.isAllowed(callerClass, target, m.getName(), desc);
            }

            if (allowed) {
                if (filtered != null) {
                    filtered[fpos++] = m;
                }
            } else if (filtered == null) {
                filtered = methods;
                fpos = i;
            }
        }

        if (filtered != null) {
            methods = Arrays.copyOfRange(filtered, 0, fpos);
        }

        return methods;
    }
}
