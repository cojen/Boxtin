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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import static org.cojen.boxtin.CustomActions.findMethod;

/**
 * @author Brian S. O'Neill
 * @see RulesApplier#checkReflection
 */
final class ReflectionApplier implements RulesApplier {
    @Override
    public void applyRulesTo(RulesBuilder b) {
        // Custom deny actions used by the reflection methods.
        MethodHandleInfo cref1, cref2, cref3, cref4, cref5, cref6,
            cref7, cref8, cref9, cref10, cref11;

        // Custom deny actions used by the MethodHandle.Lookup methods.
        MethodHandleInfo cmh1, cmh2, cmh3, cmh4, cmh5;

        // Custom check used by Proxy.newProxyInstance.
        MethodHandleInfo cnpi;

        DenyAction inaccessible;

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            cref1 = findMethod(lookup, "getConstructor",
                               Constructor.class, Caller.class, Class.class, Class[].class);
            cref2 = findMethod(lookup, "getConstructors",
                               Constructor[].class, Caller.class, Class.class);
            cref3 = findMethod(lookup, "getDeclaredConstructor",
                               Constructor.class, Caller.class, Class.class, Class[].class);
            cref4 = findMethod(lookup, "getDeclaredConstructors",
                               Constructor[].class, Caller.class, Class.class);
            cref5 = findMethod(lookup, "getDeclaredMethod",
                               Method.class, Caller.class,
                               Class.class, String.class, Class[].class);
            cref6 = findMethod(lookup, "getDeclaredMethods",
                               Method[].class, Caller.class, Class.class);
            cref7 = findMethod(lookup, "getEnclosingConstructor",
                               Constructor.class, Caller.class, Class.class);
            cref8 = findMethod(lookup, "getEnclosingMethod",
                               Method.class, Caller.class, Class.class);
            cref9 = findMethod(lookup, "getMethod",
                               Method.class, Caller.class, Class.class,
                               String.class, Class[].class);
            cref10 = findMethod(lookup, "getMethods",
                                Method[].class, Caller.class, Class.class);
            cref11 = findMethod(lookup, "getRecordComponents",
                                RecordComponent[].class, Caller.class, Class.class);

            cmh1 = findMethod(lookup, "lookupBind",
                              MethodHandle.class, Caller.class, MethodHandles.Lookup.class,
                              Object.class, String.class, MethodType.class);
            cmh2 = findMethod(lookup, "lookupFindConstructor",
                              MethodHandle.class, Caller.class, MethodHandles.Lookup.class,
                                 Class.class, MethodType.class);
            cmh3 = findMethod(lookup, "lookupFindSpecial",
                              MethodHandle.class, Caller.class, MethodHandles.Lookup.class,
                                 Class.class, String.class, MethodType.class, Class.class);
            cmh4 = findMethod(lookup, "lookupFindStatic",
                              MethodHandle.class, Caller.class, MethodHandles.Lookup.class,
                              Class.class, String.class, MethodType.class);
            cmh5 = findMethod(lookup, "lookupFindVirtual",
                              MethodHandle.class, Caller.class, MethodHandles.Lookup.class,
                              Class.class, String.class, MethodType.class);

            cnpi = findMethod(lookup, "checkNewProxyInstance",
                              boolean.class, Caller.class, ClassLoader.class,
                              Class[].class, InvocationHandler.class);

            inaccessible = DenyAction.exception("java.lang.reflect.InaccessibleObjectException")
                .check(findMethod(lookup, "checkSetAccessible",
                                  boolean.class, Caller.class, Object.class, boolean.class));

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        b.forModule("java.base", null, "27")

            .forPackage("java.lang")

            .forClass("Class")
            .denyMethod(DenyAction.custom(cref1), "getConstructor")
            .denyMethod(DenyAction.custom(cref2), "getConstructors")
            .denyMethod(DenyAction.custom(cref3), "getDeclaredConstructor")
            .denyMethod(DenyAction.custom(cref4), "getDeclaredConstructors")
            .denyMethod(DenyAction.custom(cref5), "getDeclaredMethod")
            .denyMethod(DenyAction.custom(cref6), "getDeclaredMethods")
            .denyMethod(DenyAction.custom(cref7), "getEnclosingConstructor")
            .denyMethod(DenyAction.custom(cref8), "getEnclosingMethod")
            .denyMethod(DenyAction.custom(cref9), "getMethod")
            .denyMethod(DenyAction.custom(cref10), "getMethods")
            .denyMethod(DenyAction.custom(cref11), "getRecordComponents")


            .forPackage("java.lang.annotation")

            .forClass("AnnotationTypeMismatchException")
            .denyMethod("element")


            .forPackage("java.lang.invoke")

            .forClass("MethodHandles.Lookup")
            .denyMethod(DenyAction.custom(cmh1), "bind")
            .denyMethod(DenyAction.custom(cmh2), "findConstructor")
            .denyMethod(DenyAction.custom(cmh3), "findSpecial")
            .denyMethod(DenyAction.custom(cmh4), "findStatic")
            .denyMethod(DenyAction.custom(cmh5), "findVirtual")


            .forPackage("java.lang.reflect")

            .forClass("AccessibleObject")
            .denyMethod(inaccessible, "setAccessible")
            .denyMethod(DenyAction.value(false), "trySetAccessible")

            .forClass("Proxy")
            .denyMethod("getProxyClass") // deprecated
            // If any interfaces have any denials, then throw a SecurityException. Without this
            // check, an InvocationHandler could get access to a denied Method, bypassing the
            // basic reflection checks and thus allowing method calls on other instances.
            .denyMethod(DenyAction.standard().check(cnpi), "newProxyInstance")

            ;
    }
}
