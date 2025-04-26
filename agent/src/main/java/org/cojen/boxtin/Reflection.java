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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.cojen.boxtin.ConstantPool.*;

/**
 * Provides checked access to reflection features. Attempting to access a constructor or method
 * which is denied causes an exception to be thrown. When accessing an array of constructors or
 * methods, the denied ones are filtered out -- no exception is thrown.
 *
 * @author Brian S. O'Neill
 * @hidden
 */
public final class Reflection {
    static final String CLASS_NAME = Reflection.class.getName().replace('.', '/');

    // Maps method names to descriptors.
    private static final Map<String, String> mMethods;

    static {
        var methods = new HashMap<String, String>(2);

        for (Method m : Reflection.class.getMethods()) {
            String desc = Utils.fullDescriptorFor(m.getReturnType(), m.getParameterTypes());
            methods.put(m.getName(), desc);
        }

        mMethods = methods;
    }

    /**
     * @param cp constant can be added into here
     * @return method to call, or else null
     */
    static C_NameAndType findMethod(ConstantPool cp, C_MemberRef methodRef) {
        ConstantPool.C_UTF8 className = methodRef.mClass.mValue;

        String classStr = className.toString();
        int ix = classStr.lastIndexOf('/');
        if (ix < 0) {
            return null;
        } else {
            classStr = classStr.substring(ix + 1);
        }

        C_NameAndType nat = methodRef.mNameAndType;
        ConstantPool.C_UTF8 methodName = nat.mName;

        String methodKey = classStr + '_' + methodName;
        String foundDesc = mMethods.get(methodKey);

        if (foundDesc == null) {
            return null;
        }

        ConstantPool.C_UTF8 desc = nat.mTypeDesc;

        if (!desc.tailMatches(foundDesc)) {
            return null;
        }

        if ((foundDesc.indexOf(';') + desc.length()) != foundDesc.length()) {
            return null;
        }

        return cp.addNameAndType(methodKey, foundDesc);
    }

    private final Class<?> mCaller;

    Reflection(Class<?> caller) {
        mCaller = caller;
    }

    public <T> Constructor<T> Class_getConstructor(Class<T> clazz, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Constructor<T> ctor = clazz.getConstructor(paramTypes);
        String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
        SecurityAgent.check(mCaller, clazz, null, desc);
        return ctor;
    }

    public Constructor<?>[] Class_getConstructors(Class<?> clazz) {
        return filter(clazz, clazz.getConstructors());
    }

    public <T> Constructor<T> Class_getDeclaredConstructor(Class<T> clazz, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Constructor<T> ctor = clazz.getDeclaredConstructor(paramTypes);
        String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
        SecurityAgent.check(mCaller, clazz, null, desc);
        return ctor;
    }

    public Constructor<?>[] Class_getDeclaredConstructors(Class<?> clazz) {
        return filter(clazz, clazz.getDeclaredConstructors());
    }

    public Method Class_getDeclaredMethod(Class<?> clazz, String name, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Method method = clazz.getDeclaredMethod(name, paramTypes);
        String desc = Utils.partialDescriptorFor(method.getParameterTypes());
        SecurityAgent.check(mCaller, clazz, name, desc);
        return method;
    }

    public Method[] Class_getDeclaredMethods(Class<?> clazz) {
        return filter(clazz, clazz.getDeclaredMethods());
    }

    public Constructor<?> Class_getEnclosingConstructor(Class<?> clazz) {
        Constructor<?> ctor = clazz.getEnclosingConstructor();
        if (ctor != null) {
            String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
            SecurityAgent.check(mCaller, ctor.getDeclaringClass(), null, desc);
        }
        return ctor;
    }

    public Method Class_getEnclosingMethod(Class<?> clazz) {
        Method method = clazz.getEnclosingMethod();
        if (method != null) {
            String desc = Utils.partialDescriptorFor(method.getParameterTypes());
            SecurityAgent.check(mCaller, method.getDeclaringClass(), method.getName(), desc);
        }
        return method;
    }

    public Method Class_getMethod(Class<?> clazz, String name, Class<?>... paramTypes)
        throws NoSuchMethodException
    {
        Method method = clazz.getMethod(name, paramTypes);
        String desc = Utils.partialDescriptorFor(method.getParameterTypes());
        SecurityAgent.check(mCaller, method.getDeclaringClass(), name, desc);
        return method;
    }

    public Method[] Class_getMethods(Class<?> clazz) {
        return filter(null, clazz.getMethods());
    }

    public RecordComponent[] Class_getRecordComponents(Class<?> clazz) {
        RecordComponent[] components = clazz.getRecordComponents();

        if (mCaller.getModule() == clazz.getModule()) {
            return components;
        }

        RecordComponent[] filtered = null;
        int fpos = 0;

        for (int i=0; i<components.length; i++) {
            RecordComponent rc = components[i];
            Method m = rc.getAccessor();
            String desc = Utils.partialDescriptorFor(m.getParameterTypes());
            if (SecurityAgent.isAllowed(mCaller, clazz, m.getName(), desc)) {
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

    /**
     * @param ctors can be trashed as a side effect
     */
    private Constructor<?>[] filter(Class<?> clazz, Constructor<?>[] ctors) {
        if (mCaller.getModule() == clazz.getModule()) {
            return ctors;
        }

        Constructor<?>[] filtered = null;
        int fpos = 0;

        for (int i=0; i<ctors.length; i++) {
            Constructor<?> ctor = ctors[i];
            String desc = Utils.partialDescriptorFor(ctor.getParameterTypes());
            if (SecurityAgent.isAllowed(mCaller, clazz, null, desc)) {
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
    private Method[] filter(Class<?> clazz, Method[] methods) {
        if (clazz != null && mCaller.getModule() == clazz.getModule()) {
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
                    if (mCaller.getModule() == target.getModule()) {
                        allowed = true;
                        break check;
                    }
                }

                String desc = Utils.partialDescriptorFor(m.getParameterTypes());
                allowed = SecurityAgent.isAllowed(mCaller, target, m.getName(), desc);
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
