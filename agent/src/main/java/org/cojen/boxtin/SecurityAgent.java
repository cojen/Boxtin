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
import java.lang.instrument.UnmodifiableClassException;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.security.ProtectionDomain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.synchronizedMap;

/**
 * The {@code SecurityAgent} is an instrumentation agent which transforms classes such that
 * access checks are enforced. For operations which are denied, a {@link SecurityException} is
 * thrown at runtime.
 *
 * <p>The agent can be launched with a custom {@link Controller} as follows:
 *
 * <pre>
 * java -javaagent:Boxtin.jar=my.app.SecurityController ...
 * </pre>
 * 
 * <p>If the controller is specified as "default", then a default one is selected which only
 * allows limited access to the {@link RulesApplier#java_base java.base} module.
 *
 * <p>The controller must have a public constructor which has no arguments, or it must have a
 * public constructor which accepts a single {@code String} argument. To supply a string value,
 * append it after the controller name:
 *
 * <pre>
 * java -javaagent:Boxtin.jar=my.app.SecurityController=custom.value ...
 * </pre>
 *
 * <p>If no controller is specified, then then the {@link #activate activate} method must be
 * called later, preferably from the main method.
 *
 * @author Brian S. O'Neill
 */
public final class SecurityAgent implements ClassFileTransformer {
    /**
     * @hidden
     */
    public static final StackWalker WALKER;

    static {
        EnumSet<StackWalker.Option> options;

        if (Runtime.version().feature() < 22) {
            options = EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        } else {
            StackWalker.Option dropMethodInfo;
            try {
                dropMethodInfo = (StackWalker.Option)
                    StackWalker.Option.class.getField("DROP_METHOD_INFO").get(null);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
            options = EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE, dropMethodInfo);
        }

        WALKER = StackWalker.getInstance(options);
    }

    static final String NATIVE_PREFIX = "$boxtin$_";

    static final String CLASS_NAME, BOXTIN_PACKAGE;

    private static Instrumentation cInst;

    private static volatile SecurityAgent cAgent;

    private static final VarHandle AGENT_H;

    static {
        CLASS_NAME = SecurityAgent.class.getName().replace('.', '/');
        BOXTIN_PACKAGE = SecurityAgent.class.getPackageName().replace('.', '/');


        try {
            AGENT_H = MethodHandles.lookup()
                .findStaticVarHandle(SecurityAgent.class, "cAgent", SecurityAgent.class);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * The premain method should only be called by the instrumentation layer, when the JVM
     * starts.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        synchronized (SecurityAgent.class) {
            if (cInst != null) {
                // Already called.
                throw new SecurityException();
            }
            if (inst == null) {
                throw new NullPointerException();
            }
            cInst = inst;
        }

        Controller controller = initController(agentArgs);

        if (controller != null) {
            activate(controller);
        }
    }

    private static Controller initController(String agentArgs) {
        if (agentArgs == null || agentArgs.isEmpty()) {
            return null;
        }

        if ("default".equals(agentArgs)) {
            return new DefaultController(true);
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

        try {
            if (ctor1 != null && (ctor2 == null || controllerArgs == null)) {
                return (Controller) ctor1.newInstance();
            } else {
                return (Controller) ctor2.newInstance(controllerArgs);
            }
        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof InvocationTargetException) {
                cause = e.getCause();
            }
            throw new IllegalStateException("Unable to construct the Controller", cause);
        }
    }

    /**
     * Activate the security agent if not already done so. Activation should be done as early
     * as possible, because some classes which have already been loaded might not be
     * transformable.
     *
     * @param controller if null, a default one is used which only allows limited access to the
     * {@link RulesApplier#java_base java.base} module
     * @throws IllegalStateException if the SecurityAgent wasn't loaded
     * @throws SecurityException if already activated
     */
    public static void activate(Controller controller) {
        if (!tryActivate(controller)) {
            throw new SecurityException();
        }
    }

    /**
     * Activate the security agent if not already done so. Activation should be done as early
     * as possible, because some classes which have already been loaded might not be
     * transformable.
     *
     * @param controller if null, a default one is used which only allows limited access to the
     * {@link RulesApplier#java_base java.base} module
     * @throws IllegalStateException if the SecurityAgent wasn't loaded
     * @return false if already activated
     */
    public static boolean tryActivate(Controller controller) {
        if (controller == null) {
            controller = new DefaultController(false);
        }

        Instrumentation inst;
        SecurityAgent agent;

        synchronized (SecurityAgent.class) {
            if ((inst = cInst) == null) {
                throw new IllegalStateException("SecurityAgent must be loaded using -javaagent");
            }
            if (cAgent != null) {
                // Already activated.
                return false;
            }
            cAgent = agent = new SecurityAgent(controller);
        }

        inst.addTransformer(agent, true);
        inst.setNativeMethodPrefix(agent, NATIVE_PREFIX);

        var toRetransform = new ArrayList<Class<?>>();

        for (Class<?> loaded : inst.getAllLoadedClasses()) {
            if (!agent.isSpecial(loaded) &&
                inst.isModifiableClass(loaded) && agent.isTargetChecked(loaded))
            {
                toRetransform.add(loaded);
            }
        }

        try {
            // FIXME: Check if any classes were loaded on demand and got skipped.
            inst.retransformClasses(toRetransform.toArray(Class[]::new));
        } catch (UnmodifiableClassException e) {
            // FIXME: How to report this? How to deal with classes that got skipped?
            e.printStackTrace();
        }

        return true;
    }

    private final Controller mController;

    private final Map<Module, Map<Class<?>, Map<String, Map<String, Boolean>>>> mCheckCache;

    private SecurityAgent(Controller controller) {
        mController = controller;
        mCheckCache = synchronizedMap(new WeakHashMap<>());
    }

    /**
     * Returns true if the given class might need to have target-side checks inserted.
     */
    private boolean isTargetChecked(Class<?> clazz) {
        if (Utils.isAccessible(clazz) && clazz.getModule().isExported(clazz.getPackageName())) {
            Checker checker = mController.checkerForTarget(clazz);
            if (checker != null) {
                return checker.forClass(clazz).isTargetChecked();
            }
        }
        return false;
    }

    private boolean isSpecial(Class<?> clazz) {
        // These classes won't be transformed.
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

        ClassLoader loader = module.getClassLoader();

        Checker forCaller;
        if (loader == null) {
            // Classes loaded by the bootstrap class loader are allowed to call anything.
            forCaller = Rule.ALLOW;
        } else {
            forCaller = mController.checkerForCaller(module, className);
            if (forCaller == null) {
                forCaller = Rule.ALLOW;
            }
        }

        Checker forTarget = mController.checkerForTarget(module, className);
        if (forTarget == null) {
            forTarget = Rule.ALLOW;
        }

        if (forCaller == Rule.ALLOW && forTarget == Rule.ALLOW) {
            return null;
        }

        final String packageName;

        {
            int index = className.lastIndexOf('/');
            packageName = index < 0 ? "" : className.substring(0, index);

            if (loader == null && packageName.equals(BOXTIN_PACKAGE)) {
                // No need to transform packages in this package.
                return null;
            }

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

        Checker.ForClass forTargetClass = forTarget.forClass(packageName, className);

        // Classes loaded by the bootstrap loader are allowed to perform reflection, and so
        // special transforms aren't needed. If enabled, it would likely cause issues anyhow.
        boolean reflectionChecks = loader != null;

        if (!processor.check(forCaller, forTargetClass, reflectionChecks)) {
            return null;
        }

        byte[] bytes = processor.redefine();

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
        if (caller.getModule() != target.getModule() && !isAllowed(caller, target, name, desc)) {
            throw new SecurityException();
        }
    }

    /**
     * Note: Module comparison should be performed as a prerequisite.
     */
    static boolean isAllowed(Class<?> caller, Class<?> target, String name, String desc) {
        var agent = (SecurityAgent) AGENT_H.getAcquire();

        if (agent == null) {
            throw new SecurityException();
        }

        Module callerModule = caller.getModule();

        return agent.mCheckCache
            // weak ref to target
            .computeIfAbsent(callerModule, k -> synchronizedMap(new WeakHashMap<>(4)))
            .computeIfAbsent(target, k -> new ConcurrentHashMap<>(4))
            .computeIfAbsent(name == null ? "<init>" : name, k -> new ConcurrentHashMap<>(4))
            .computeIfAbsent(desc, k -> {
                Checker checker = agent.mController.checkerForCaller(caller);
                if (checker == null) {
                    return true;
                }
                Checker.ForClass forClass = checker.forClass(target);
                if (name == null) {
                    return forClass.isConstructorAllowed(desc);
                } else {
                    return forClass.isMethodAllowed(name, desc);
                }
            });
    }

    /**
     * Returns a Reflection instance corresponding to the current caller.
     *
     * @hidden
     */
    public static Reflection reflection() {
        // The caller class cannot be passed in as a parameter, because this is a public
        // method, and anything can be passed in. Use the WALKER to get the real caller.
        return new Reflection(WALKER.getCallerClass());
    }
}
