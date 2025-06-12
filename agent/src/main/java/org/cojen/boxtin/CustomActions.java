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

import java.nio.ByteBuffer;

import java.security.ProtectionDomain;

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

    // Custom deny action for System.getProperty.
    public static String stringValue(String name, String defaultValue) {
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
        Module module = caller.getModule();
        return module.isNamed() && module.isNativeAccessEnabled();
    }
}
