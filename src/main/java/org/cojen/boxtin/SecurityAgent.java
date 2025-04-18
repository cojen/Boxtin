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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.security.ProtectionDomain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class SecurityAgent implements ClassFileTransformer {
    /**
     * @hidden
     */
    public static final StackWalker WALKER = StackWalker.getInstance
        (java.util.EnumSet.of(StackWalker.Option.DROP_METHOD_INFO,
                              StackWalker.Option.RETAIN_CLASS_REFERENCE));

    private static volatile SecurityAgent INSTANCE;

    private static final VarHandle INSTANCE_H;

    static {
        try {
            INSTANCE_H = MethodHandles.lookup()
                .findStaticVarHandle(SecurityAgent.class, "INSTANCE", SecurityAgent.class);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        if (inst == null) {
            throw new NullPointerException();
        }

        SecurityAgent agent = init(agentArgs);

        inst.addTransformer(agent, true);

        var toRetransform = new ArrayList<Class>();

        for (Class loaded : inst.getAllLoadedClasses()) {
            if (!agent.isSpecial(loaded) &&
                inst.isModifiableClass(loaded) && agent.isTargetChecked(loaded))
            {
                toRetransform.add(loaded);
            }
        }

        // FIXME: Check if any classes were loaded on demand and got skipped.
        inst.retransformClasses(toRetransform.toArray(Class[]::new));
    }

    private static synchronized SecurityAgent init(String agentArgs) throws Exception {
        if (INSTANCE != null) {
            throw new SecurityException();
        }

        if (agentArgs == null || agentArgs.isEmpty()) {
            throw new IllegalArgumentException("No Controller implementation is specified");
        }

        String controllerName, controllerArgs;

        int ix = agentArgs.indexOf('=');

        if (ix < 0) {
            controllerName = agentArgs;
            controllerArgs = null;
        } else {
            controllerName = agentArgs.substring(0, ix);
            controllerArgs = agentArgs.substring(ix + 1);
        }

        Class<?> controllerClass;

        try {
            controllerClass = ClassLoader.getSystemClassLoader().loadClass(controllerName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Controller class isn't found", e);
        }

        if (!Controller.class.isAssignableFrom(controllerClass)) {
            throw new IllegalArgumentException
                ("Controller class doesn't implement the Controller interface");
        }

        Constructor<?> ctor1, ctor2;

        try {
            ctor1 = controllerClass.getConstructor();
        } catch (NoSuchMethodException e) {
            ctor1 = null;
        }

        try {
            ctor2 = controllerClass.getConstructor(String.class);
        } catch (NoSuchMethodException e2) {
            ctor2 = null;
        }

        if (ctor1 == null && ctor2 == null) {
            throw new IllegalArgumentException
                ("Controller class doesn't have an appropriate public constructor");
        }

        Controller controller;

        try {
            if (ctor1 != null && (ctor2 == null || controllerArgs == null)) {
                controller = (Controller) ctor1.newInstance();
            } else {
                controller = (Controller) ctor2.newInstance(controllerArgs);
            }
        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof InvocationTargetException) {
                cause = e.getCause();
            }
            throw new IllegalStateException("Unable to construct the Controller", cause);
        }

        return INSTANCE = new SecurityAgent(controller);
    }

    private final Controller mController;

    private final Map<Module, Map<Class, Map<String, Map<String, Boolean>>>> mCheckCache;

    private SecurityAgent(Controller contoller) {
        mController = contoller;
        mCheckCache = new WeakHashMap<Module, Map<Class, Map<String, Map<String, Boolean>>>>();
    }

    /**
     * Returns true if the given class might need to have target-side checks inserted.
     */
    private boolean isTargetChecked(Class clazz) {
        if (Utils.isAccessible(clazz) && clazz.getModule().isExported(clazz.getPackageName())) {
            Checker checker = mController.checkerForTarget();
            if (checker != null) {
                return checker.forClass(clazz).isTargetChecked();
            }
        }
        return false;
    }

    private boolean isSpecial(Class clazz) {
        return clazz == SecurityAgent.class || clazz == mController.getClass();
    }

    @Override
    public byte[] transform(Module module,
                            ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classBuffer)
        throws IllegalClassFormatException
    {
        try {
            return doTransform(module, className, classBeingRedefined, classBuffer);
        } catch (Throwable e) {
            // FIXME: Any exception thrown from this method is discarded! That's bad! Instead,
            // return a new class which throws a SecurityException from every method and
            // constructor.
            System.out.println("***");
            e.printStackTrace();
            if (e instanceof IllegalClassFormatException ex) {
                throw ex;
            }
            throw new IllegalClassFormatException(e.toString());
        }
    }

    private byte[] doTransform(Module module, String className,
                               Class<?> classBeingRedefined, byte[] classBuffer)
        throws Throwable
    {
        if (isSpecial(classBeingRedefined)) {
            return null;
        }

        Checker forCaller;
        if (module.getClassLoader() == null) {
            // Classes loaded by the bootstrap class loader are allowed to call anything.
            forCaller = Rule.ALLOW;
        } else {
            forCaller = mController.checkerForCaller(module);
            if (forCaller == null) {
                forCaller = Rule.ALLOW;
            }
        }

        Checker forTarget = mController.checkerForTarget();
        if (forTarget == null) {
            forTarget = Rule.ALLOW;
        }

        if (forCaller == Rule.ALLOW && forTarget == Rule.ALLOW) {
            return null;
        }

        String packageName;
        {
            int index = className.lastIndexOf('/');
            packageName = index < 0 ? "" : className.substring(0, index);

            if (forTarget != Rule.ALLOW && !module.isExported(packageName.replace('/', '.'))) {
                // If the package isn't exported, then it cannot be called outside the module.
                // The only calls will be from within the module, which are always allowed.
                if (forCaller == Rule.ALLOW) {
                    // If no code changes are required as a caller, then no transformation is
                    // required at all.
                    return null;
                }
                forTarget = Rule.ALLOW;
            }

            className = className.substring(index + 1);
        }

        assert forCaller != Rule.ALLOW || forTarget != Rule.ALLOW;

        var processor = ClassFileProcessor.begin(classBuffer);

        if (!processor.check(forCaller, forTarget.forClass(packageName, className))) {
            return null;
        }

        byte[] bytes = processor.redefine();

        /*
        if (bytes != null) {
            System.out.println("  transformed: " + packageName + ", " + className);
        }
        */

        return bytes;
    }

    /**
     * Is called by modified target-side code. An exception is thrown if the caller and target
     * modules differ, and also if the corresponding checker denies access.
     *
     * @param caller the class which is calling the target
     * @param target the class which has an operation which potentially denied for the caller
     * @param name target method name, or null if a constructor
     * @param desc target method or constructor descriptor
     * @hidden
     */
    public static void check(Class<?> caller, Class<?> target, String name, String desc)
        throws SecurityException
    {
        Module callerModule = caller.getModule();
        if (callerModule != target.getModule()) {
            check(callerModule, target, name, desc);
        }
    }

    private static void check(Module callerModule, Class<?> target, String name, String desc)
        throws SecurityException
    {
        var agent = (SecurityAgent) INSTANCE_H.getAcquire();

        if (agent == null) {
            throw new SecurityException();
        }

        Boolean allowed = agent.mCheckCache
            .computeIfAbsent(callerModule, k -> new WeakHashMap<>()) // target weak ref
            .computeIfAbsent(target, k -> new HashMap<>())
            .computeIfAbsent(name, k -> new HashMap<>())
            .computeIfAbsent(desc, k -> {
                Checker.ForClass forClass = agent.mController
                    .checkerForCaller(callerModule).forClass(target);
                if (name == null) {
                    return forClass.isConstructorAllowed(desc);
                } else {
                    return forClass.isMethodAllowed(name, desc);
                }
            });

        /*
        System.out.println("check: " + callerModule + ", " + target + ", " + name + ", " + desc
                           + ", " + allowed);
        */

        if (!allowed) {
            throw new SecurityException();
        }
    }
}
