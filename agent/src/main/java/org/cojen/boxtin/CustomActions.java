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

/**
 * Used by JavaBaseApplier for selecting custom deny actions. The methods must be accessible,
 * which is why a separate class is used.
 * 
 * @author Brian S. O'Neill
 * @hidden
 */
public final class CustomActions {
    // Custom deny action for Integer.getInteger.
    public static Integer intValue(String name, int value) {
        return value;
    }

    // Custom deny action for Integer.getInteger.
    public static Integer intValue(String name, Integer value) {
        return value;
    }

    // Custom deny action for Long.getLong.
    public static Long longValue(String name, long value) {
        return value;
    }

    // Custom deny action for Long.getLong.
    public static Long longValue(String name, Long value) {
        return value;
    }

    // Custom deny action for System.getProperty.
    public static String stringValue(String name, String value) {
        return value;
    }

    // Check for ClassLoader.defineClass.
    public static boolean checkDefineClass(Class<?> caller, ClassLoader loader,
                                           String name, byte[] b, int off, int len,
                                           ProtectionDomain protectionDomain)
    {
        // Denied when attempting to specify a ProtectionDomain.
        return protectionDomain != null;
    }

    // Check for ClassLoader.defineClass.
    public static boolean checkDefineClass(Class<?> caller, ClassLoader loader,
                                           String name, ByteBuffer b,
                                           ProtectionDomain protectionDomain)
    {
        // Denied when attempting to specify a ProtectionDomain.
        return protectionDomain != null;
    }

    // Check for Class.getResource and getResourceAsStream.
    public static boolean checkGetResource(Class<?> caller, Class<?> clazz, String name) {
        Module module = clazz.getModule();
        return module.isNamed() ? checkGetResource(caller, module, name)
            : checkGetResource(caller, clazz.getClassLoader(), name);
    }

    // Check for ClassLoader.getResource, getResourceAsStream, getResources, and resources.
    public static boolean checkGetResource(Class<?> caller, ClassLoader loader, String name) {
        // Deny the caller ClassLoader is different than the ClassLoader being invoked.
        return caller.getClassLoader() != loader;
    }

    // Check for Module.getResourceAsStream.
    public static boolean checkGetResource(Class<?> caller, Module module, String name) {
        // Denied when the caller Module is different than the Module being invoked.
        return caller.getModule() != module;
    }
}
