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
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import java.nio.ByteBuffer;

import java.security.ProtectionDomain;

import java.util.Arrays;
import java.util.Properties;

/**
 * Used by JavaBaseApplier for selecting custom deny actions. The methods must be accessible,
 * which is why a separate class is used.
 * 
 * @author Brian S. O'Neill
 * @hidden
 */
public final class CustomActions {
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
    public static Properties getProperties(Class<?> caller) {
        return FilteredProperties.getProperties(caller.getModule());
    }

    // Custom deny action for System.getProperty.
    public static String getProperty(Class<?> caller, String name) {
        return FilteredProperties.getProperty(caller.getModule(), name);
    }

    // Custom deny action for System.getProperty.
    public static String getProperty(Class<?> caller, String name, String def) {
        return FilteredProperties.getProperty(caller.getModule(), name, def);
    }

    // Custom deny action for System.setProperties.
    public static void setProperties(Class<?> caller, Properties props) {
        FilteredProperties.setProperties(caller.getModule(), props);
    }

    // Custom deny action for System.setProperty.
    public static String setProperty(Class<?> caller, String name, String value) {
        return FilteredProperties.setProperty(caller.getModule(), name, value);
    }

    // Custom deny action for System.clearProperty.
    public static String clearProperty(Class<?> caller, String name) {
        return FilteredProperties.clearProperty(caller.getModule(), name);
    }

    // Check for ClassLoader.defineClass.
    public static boolean checkDefineClass(Class<?> caller, ClassLoader loader,
                                           String name, byte[] b, int off, int len,
                                           ProtectionDomain protectionDomain)
    {
        // Allowed when no ProtectionDomain is given.
        return protectionDomain == null;
    }

    // Check for ClassLoader.defineClass.
    public static boolean checkDefineClass(Class<?> caller, ClassLoader loader,
                                           String name, ByteBuffer b,
                                           ProtectionDomain protectionDomain)
    {
        // Allowed when no ProtectionDomain is given.
        return protectionDomain == null;
    }

    // Check for Class.forName(String, boolean, ClassLoader).
    public static boolean checkForName(Class<?> caller,
                                       String name, boolean initialize, ClassLoader loader)
    {
        // Allowed when not asked to initialize or when the caller's loader is the same.
        return !initialize || caller.getClassLoader() == loader;
    }

    // Check for Class.getResource and getResourceAsStream.
    public static boolean checkGetResource(Class<?> caller, Class<?> clazz) {
        Module module = clazz.getModule();
        return module.isNamed() ? checkGetResource(caller, module)
            : checkGetResource(caller, clazz.getClassLoader());
    }

    // Check for ClassLoader.getResource, getResourceAsStream, getResources, and resources.
    public static boolean checkGetResource(Class<?> caller, ClassLoader loader) {
        // Allowed when the caller ClassLoader is the same as the ClassLoader being invoked.
        return caller.getClassLoader() == loader;
    }

    // Check for Module.getResourceAsStream.
    public static boolean checkGetResource(Class<?> caller, Module module) {
        // Allowed when the caller Module is the same as the Module being invoked.
        return caller.getModule() == module;
    }

    // Check for @Restricted methods: ModuleLayer.enableNativeAccess,
    // AddressLayout.withTargetLayout, Linker.downcallHandle, Linker.upcallStub,
    // MemorySegment.reinterpret, and SymbolLookup.libraryLookup, Runtime.load,
    // Runtime.loadLibrary, System.load, and System.loadLibrary
    public static boolean checkNativeAccess(Class<?> caller) {
        // Allowed for named modules for which access has been granted using the
        // --enable-native-access option.
        if (Runtime.getRuntime().version().feature() < 22) {
            return false;
        }
        Module module = caller.getModule();
        return module.isNamed() && module.isNativeAccessEnabled();
    }

    // Check for AccessibleObject.setAccessible.
    public static boolean checkSetAccessible(Class<?> caller, Object obj, boolean set) {
        if (!set) {
            // Allowed when not enabling access.
            return true;
        }
        if (obj instanceof Member m) {
            // Allowed when the caller Module is the same as the Module being accessed.
            return caller.getModule() == m.getDeclaringClass().getModule();
        }
        return false;
    }

    // Custom deny action for Class.getConstructor.
    public static <T> Constructor<T> getConstructor(Class<?> caller,
                                                    Class<T> clazz, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Constructor<T> ctor = clazz.getConstructor(paramTypes);
        String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
        SecurityAgent.check(caller, clazz, null, desc);
        return ctor;
    }

    // Custom deny action for Class.getConstructors.
    public static Constructor<?>[] getConstructors(Class<?> caller, Class<?> clazz) {
        return filter(caller, clazz, clazz.getConstructors());
    }

    // Custom deny action for Class.getDeclaredConstructor.
    public static <T> Constructor<T> getDeclaredConstructor
        (Class<?> caller, Class<T> clazz, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Constructor<T> ctor = clazz.getDeclaredConstructor(paramTypes);
        String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
        SecurityAgent.check(caller, clazz, null, desc);
        return ctor;
    }

    // Custom deny action for Class.getDeclaredConstructors.
    public static Constructor<?>[] getDeclaredConstructors(Class<?> caller, Class<?> clazz) {
        return filter(caller, clazz, clazz.getDeclaredConstructors());
    }

    // Custom deny action for Class.getDeclaredMethod.
    public static Method getDeclaredMethod(Class<?> caller, Class<?> clazz,
                                           String name, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Method method = clazz.getDeclaredMethod(name, paramTypes);
        String desc = Utils.partialDescriptorFor(method.getParameterTypes());
        SecurityAgent.check(caller, clazz, name, desc);
        return method;
    }

    // Custom deny action for Class.getDeclaredMethods.
    public static Method[] getDeclaredMethods(Class<?> caller, Class<?> clazz) {
        return filter(caller, clazz, clazz.getDeclaredMethods());
    }

    // Custom deny action for Class.getEnclosingConstructor.
    public static Constructor<?> getEnclosingConstructor(Class<?> caller, Class<?> clazz) {
        Constructor<?> ctor = clazz.getEnclosingConstructor();
        if (ctor != null) {
            String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
            SecurityAgent.check(caller, ctor.getDeclaringClass(), null, desc);
        }
        return ctor;
    }

    // Custom deny action for Class.getEnclosingMethod.
    public static Method getEnclosingMethod(Class<?> caller, Class<?> clazz) {
        Method method = clazz.getEnclosingMethod();
        if (method != null) {
            String desc = Utils.partialDescriptorFor(method.getParameterTypes());
            SecurityAgent.check(caller, method.getDeclaringClass(), method.getName(), desc);
        }
        return method;
    }

    // Custom deny action for Class.getMethod.
    public static Method getMethod(Class<?> caller, Class<?> clazz,
                                   String name, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Method method = clazz.getMethod(name, paramTypes);
        String desc = Utils.partialDescriptorFor(method.getParameterTypes());
        SecurityAgent.check(caller, method.getDeclaringClass(), name, desc);
        return method;
    }

    // Custom deny action for Class.getMethods.
    public static Method[] getMethods(Class<?> caller, Class<?> clazz) {
        return filter(caller, null, clazz.getMethods());
    }

    // Custom deny action for Class.getRecordComponents.
    public static RecordComponent[] getRecordComponents(Class<?> caller, Class<?> clazz) {
        RecordComponent[] components = clazz.getRecordComponents();

        if (caller.getModule() == clazz.getModule()) {
            return components;
        }

        RecordComponent[] filtered = null;
        int fpos = 0;

        for (int i=0; i<components.length; i++) {
            RecordComponent rc = components[i];
            Method m = rc.getAccessor();
            String desc = Utils.partialDescriptorFor(m.getParameterTypes());
            if (SecurityAgent.isAllowed(caller, clazz, m.getName(), desc)) {
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
    public static MethodHandle lookupBind(Class<?> caller, MethodHandles.Lookup lookup,
                                          Object receiver, String name, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        MethodHandle mh = lookup.bind(receiver, name, mt);
        return check(caller, mh, receiver.getClass(), name, mh.type());
    }

    // Custom deny action for MethodHandle.Lookup.findConstructor
    public static MethodHandle lookupFindConstructor(Class<?> caller, MethodHandles.Lookup lookup,
                                                     Class<?> clazz, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        return check(caller, lookup, lookup.findConstructor(clazz, mt));
    }

    // Custom deny action for MethodHandle.Lookup.findSpecial
    public static MethodHandle lookupFindSpecial(Class<?> caller, MethodHandles.Lookup lookup,
                                                 Class<?> clazz, String name, MethodType mt,
                                                 Class<?> specialCaller)
        throws NoSuchMethodException, IllegalAccessException
    {
        return check(caller, lookup, lookup.findSpecial(clazz, name, mt, specialCaller));
    }

    // Custom deny action for MethodHandle.Lookup.findStatic
    public static MethodHandle lookupFindStatic(Class<?> caller, MethodHandles.Lookup lookup,
                                                Class<?> clazz, String name, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        return check(caller, lookup, lookup.findStatic(clazz, name, mt));
    }

    // Custom deny action for MethodHandle.Lookup.findVirtual
    public static MethodHandle lookupFindVirtual(Class<?> caller, MethodHandles.Lookup lookup,
                                                 Class<?> clazz, String name, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        return check(caller, lookup, lookup.findVirtual(clazz, name, mt));
    }

    private static MethodHandle check(Class<?> caller,
                                      MethodHandle mh, Class<?> clazz, String name, MethodType mt)
    {
        SecurityAgent.check(caller, clazz, name, Utils.partialDescriptorFor(mt));
        return mh;
    }

    private static MethodHandle check(Class<?> caller,
                                      MethodHandles.Lookup lookup, MethodHandle mh)
    {
        MethodHandleInfo info = lookup.revealDirect(mh);
        return check(caller, mh, info.getDeclaringClass(), info.getName(), info.getMethodType());
    }

    /**
     * @param ctors can be trashed as a side effect
     */
    private static Constructor<?>[] filter(Class<?> caller,
                                           Class<?> clazz, Constructor<?>[] ctors) {
        if (caller.getModule() == clazz.getModule()) {
            return ctors;
        }

        Constructor<?>[] filtered = null;
        int fpos = 0;

        for (int i=0; i<ctors.length; i++) {
            Constructor<?> ctor = ctors[i];
            String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
            if (SecurityAgent.isAllowed(caller, clazz, null, desc)) {
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
    private static Method[] filter(Class<?> caller, Class<?> clazz, Method[] methods) {
        if (clazz != null && caller.getModule() == clazz.getModule()) {
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
                    if (caller.getModule() == target.getModule()) {
                        allowed = true;
                        break check;
                    }
                }

                String desc = Utils.partialDescriptorFor(m.getParameterTypes());
                allowed = SecurityAgent.isAllowed(caller, target, m.getName(), desc);
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
