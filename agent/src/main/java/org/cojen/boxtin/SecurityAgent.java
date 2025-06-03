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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.security.ProtectionDomain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
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
 * <p>If no controller is specified, then the {@link #activate activate} method must be
 * called later, preferably from the main method.
 *
 * @author Brian S. O'Neill
 */
public final class SecurityAgent {
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

    private static final ClassLoader ALT_LOADER;

    private static Instrumentation cInst;

    private static SecurityAgent cAgent;

    static {
        CLASS_NAME = SecurityAgent.class.getName().replace('.', '/').intern();
        BOXTIN_PACKAGE = SecurityAgent.class.getPackageName().replace('.', '/').intern();

        if (SecurityAgent.class.getClassLoader() == null) {
            ALT_LOADER = ClassLoader.getSystemClassLoader();
        } else {
            ALT_LOADER = null;
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
     * Should only be used by the tests. Pass null to deactivate.
     */
    static synchronized SecurityAgent testActivate(Controller controller) {
        return cAgent = controller == null ? null : new SecurityAgent(controller);
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

        ClassFileTransformer transformer = agent.newTransformer();

        inst.addTransformer(transformer, true);
        inst.setNativeMethodPrefix(transformer, NATIVE_PREFIX);

        var toRetransform = new ArrayList<Class<?>>();

        for (Class<?> loaded : inst.getAllLoadedClasses()) {
            if (agent.isSpecial(loaded)) {
                continue;
            }

            if (inst.isModifiableClass(loaded)) {
                if (agent.isTargetChecked(loaded)) {
                    toRetransform.add(loaded);
                }
                continue;
            }

            if (!loaded.isArray() && agent.isTargetChecked(loaded)) {
                log(System.Logger.Level.WARNING,
                    "Loaded class cannot be transformed: " + loaded.getName(), null);
            }
        }

        try {
            // FIXME: Check if any classes were loaded on demand and got skipped.
            inst.retransformClasses(toRetransform.toArray(Class[]::new));
        } catch (UnmodifiableClassException e) {
            logException(e);
        }

        return true;
    }

    public static synchronized boolean isActivated() {
        return cAgent != null;
    }

    private static void logException(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) {
            message = ex.toString();
        }
        logException(message, ex);
    }

    private static void logException(String message, Throwable ex) {
        log(System.Logger.Level.ERROR, message, ex);
    }

    private static void log(System.Logger.Level level, String message, Throwable ex) {
        try {
            System.getLogger(SecurityAgent.class.getName()).log(level, message, ex);
        } catch (Throwable e) {
            // Last resort.
            synchronized (System.err) {
                e.printStackTrace(System.err);
                if (message != null) {
                    System.err.println(message);
                }
                if (ex != null) {
                    ex.printStackTrace(System.err);
                }
            }
        }
    }

    private final Controller mController;

    private final Map<Module, Map<Class<?>, Map<String, Map<String, Rule>>>> mCheckCache;

    private SecurityAgent(Controller controller) {
        mController = controller;
        mCheckCache = synchronizedMap(new WeakHashMap<>(4));
    }

    /**
     * Returns true if the given class might need to have target-side checks inserted.
     */
    private boolean isTargetChecked(Class<?> clazz) {
        Module module;
        return Utils.isAccessible(clazz)
            && (module = clazz.getModule()).isNamed()
            && module.isExported(clazz.getPackageName())
            && isAnyDeniedAtTarget(clazz);
    }

    private boolean isAnyDeniedAtTarget(Class<?> clazz) {
        Set<Rules> allRules = mController.allRules();
        if (allRules != null) for (Rules rules : allRules) {
            if (rules != null && rules.forClass(clazz).isAnyDeniedAtTarget()) {
                return true;
            }
        }
        return false;
    }

    private boolean isSpecial(Class<?> clazz) {
        // These classes won't be transformed.
        return clazz == SecurityAgent.class || clazz == mController.getClass();
    }

    private ClassFileTransformer newTransformer() {
        return new ClassFileTransformer() {
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
                    logException("Failed to transform class: " + className, e);
                    // FIXME: Any exception thrown from this method is discarded! That's bad!
                    // Instead, return a new class which throws a SecurityException from every
                    // method and constructor.
                    throw new IllegalClassFormatException(e.toString());
                }
            }
        };
    }

    // Is package-private for testing.
    byte[] doTransform(Module module, String className,
                       Class<?> classBeingRedefined, byte[] classBuffer)
        throws Throwable
    {
        if (isSpecial(classBeingRedefined)) {
            return null;
        }

        ClassLoader loader = module.getClassLoader();

        Rules forCaller;
        if (loader == null) {
            // Classes loaded by the bootstrap class loader are allowed to call anything.
            forCaller = Rule.allow();
        } else {
            forCaller = mController.rulesForCaller(module);
            if (forCaller == null) {
                forCaller = Rule.allow();
            }
        }

        // Note that unnamed modules cannot have target security checks applied to them, since
        // they're not expected to implement sensitive operations.

        Rules forTarget = module.isNamed() ? MergedTargetRules.from(mController) : Rule.allow();

        if (forCaller.isAllAllowed() && forTarget.isAllAllowed()) {
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

            if (!forTarget.isAllAllowed() && !module.isExported(packageName.replace('/', '.'))) {
                // If the package isn't exported, then it cannot be called outside the module.
                // The only calls will be from within the module, which are always allowed.
                if (forCaller.isAllAllowed()) {
                    // If no code changes are required as a caller, then no transformation is
                    // required at all.
                    return null;
                }
                forTarget = Rule.allow();
            }

            className = className.substring(index + 1);
        }

        assert !forCaller.isAllAllowed() || forTarget.isAllAllowed();

        var processor = ClassFileProcessor.begin(classBuffer);

        Rules.ForClass forTargetClass = forTarget.forClass(packageName, className);

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
     * modules differ, and the corresponding rule set denies access.
     *
     * @param caller the class which is calling the target
     * @param target the class which has an operation which potentially denied for the caller
     * @param name target method name, or null/"<init>" if a constructor
     * @param desc target method or constructor descriptor
     * @hidden
     */
    public static void check(Class<?> caller, Class<?> target, String name, String desc)
        throws SecurityException
    {
        if (!tryCheck(caller, target, name, desc)) {
            throw new SecurityException();
        }
    }

    /**
     * Is called by modified target-side code. True is returned if the caller and target
     * modules are the same or if the corresponding rule set allows access.
     *
     * @param caller the class which is calling the target
     * @param target the class which has an operation which potentially denied for the caller
     * @param name target method name, or null/"<init>" if a constructor
     * @param desc target method or constructor descriptor
     * @hidden
     */
    public static boolean tryCheck(Class<?> caller, Class<?> target, String name, String desc) {
        return caller.getModule() == target.getModule() || isAllowed(caller, target, name, desc);
    }

    /**
     * Note: Module comparison should be performed as a prerequisite.
     */
    static boolean isAllowed(Class<?> caller, Class<?> target, String name, String desc) {
        return AllowCheck.isAllowed(caller, target, name, desc);
    }

    /**
     * @hidden
     */
    public static Object applyDenyAction(Class<?> caller, Class<?> target, String name, String desc,
                                         Class<?> returnType, Object[] args)
        throws Throwable
    {
        return AllowCheck.applyDenyAction(caller, target, name, desc, returnType, args);
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

    /**
     * The agent should be loaded by the bootstrap ClassLoader, as specified by the
     * Boot-Class-Path option in the MANIFEST.MF file. The jar file can also be specified on
     * the module path, in which case it will ALSO be loaded by the system ClassLoader. All the
     * magic stuff going on in the static intializer of this class is intended to find the real
     * SecurityAgent instance, which should be invoked by the isAllowed method.
     */
    private static class AllowCheck {
        private static final MethodHandle IS_ALLOWED_H, APPLY_DENY_ACTION_H;

        static {
            var lookup = MethodHandles.lookup();

            var mt1 = MethodType.methodType
                (boolean.class, Class.class, Class.class, String.class, String.class);

            var mt2 = MethodType.methodType
                (Object.class, Class.class, Class.class, String.class, String.class,
                 Class.class, Object[].class);

            MethodHandle mh1, mh2;

            try {
                Object agent = agent();

                if (agent != null) {
                    mh1 = lookup.findVirtual(SecurityAgent.class, "isAllowed2", mt1);
                    mh2 = lookup.findVirtual(SecurityAgent.class, "applyDenyAction2", mt2);
                } else {
                    try {
                        Class<?> altClass = Class.forName
                            (SecurityAgent.class.getName(), false, SecurityAgent.ALT_LOADER);

                        lookup = lookup.in(altClass);

                        agent = lookup.findStatic
                            (altClass, "agent", MethodType.methodType(altClass))
                            .invoke();

                        if (agent != null) {
                            mh1 = lookup.findVirtual(altClass, "isAllowed2", mt1);
                            mh2 = lookup.findVirtual(altClass, "applyDenyAction2", mt2);
                        } else {
                            mh1 = lookup.findStatic(altClass, "isAllowed3", mt1);
                            mh2 = lookup.findStatic(altClass, "applyDenyAction3", mt2);
                        }
                    } catch (ClassNotFoundException e) {
                        agent = null;
                        mh1 = lookup.findStatic(SecurityAgent.class, "isAllowed3", mt1);
                        mh2 = lookup.findStatic(SecurityAgent.class, "applyDenyAction3", mt2);
                    }
                }

                if (agent != null) {
                    mh1 = MethodHandles.insertArguments(mh1, 0, agent);
                    mh2 = MethodHandles.insertArguments(mh2, 0, agent);
                }
            } catch (ExceptionInInitializerError e) {
                throw e;
            } catch (Throwable e) {
                throw new ExceptionInInitializerError(e);
            }

            IS_ALLOWED_H = mh1;
            APPLY_DENY_ACTION_H = mh2;
        }

        static boolean isAllowed(Class<?> caller, Class<?> target, String name, String desc) {
            try {
                return (boolean) IS_ALLOWED_H.invokeExact(caller, target, name, desc);
            } catch (SecurityException e) {
                throw e;
            } catch (Throwable e) {
                throw new SecurityException(e);
            }
        }

        static Object applyDenyAction(Class<?> caller, Class<?> target, String name, String desc,
                                      Class<?> returnType, Object[] args)
            throws Throwable
        {
            return APPLY_DENY_ACTION_H.invokeExact(caller, target, name, desc, returnType, args);
        }
    }

    /**
     * Note: Must be public because it can be accessed from a different ClassLoader.
     *
     * @hidden
     */
    public static SecurityAgent agent() {
        SecurityAgent agent = cAgent;
        if (agent == null) {
            synchronized (SecurityAgent.class) {
                agent = cAgent;
            }
        }
        return agent;
    }

    /**
     * Note: Must be public because it can be accessed from a different ClassLoader.
     *
     * Note: Module comparison should be performed as a prerequisite.
     *
     * @hidden
     */
    public boolean isAllowed2(Class<?> caller, Class<?> target, String name, String desc) {
        return findRule(caller, target, name, desc).isAllowed();
    }

    /**
     * Note: Must be public because it can be accessed from a different ClassLoader.
     *
     * @hidden
     */
    public Object applyDenyAction2(Class<?> caller, Class<?> target, String name, String desc,
                                   Class<?> returnType, Object[] args)
        throws Throwable
    {
        DenyAction action = findRule(caller, target, name, desc).denyAction();

        if (action != null) {
            Object result = action.apply(caller, returnType, args);
            if (name != null) {
                return result;
            }
            // Constructors must always throw an exception.
        }

        throw new SecurityException();
    }

    private Rule findRule(Class<?> caller, Class<?> target, String name, String desc) {
        Module callerModule = caller.getModule();
        String fname = name == null ? "<init>" : name;

        return mCheckCache
            // weak ref to target
            .computeIfAbsent(callerModule, k -> synchronizedMap(new WeakHashMap<>(4)))
            .computeIfAbsent(target, k -> new ConcurrentHashMap<>(4))
            .computeIfAbsent(fname, k -> new ConcurrentHashMap<>(4))
            .computeIfAbsent(desc, k -> {
                Rules rules = mController.rulesForCaller(callerModule);
                if (rules == null) {
                    return Rule.allow();
                }
                Rules.ForClass forClass = rules.forClass(target);
                Rule rule;
                if (fname.equals("<init>")) {
                    rule = forClass.ruleForConstructor(desc);
                } else {
                    rule = forClass.ruleForMethod(name, desc);
                }
                return rule;
            });
    }

    /**
     * Note: Module comparison should be performed as a prerequisite.
     */
    private static boolean isAllowed3(Class<?> caller, Class<?> target, String name, String desc) {
        SecurityAgent agent = agent();
        return agent != null && agent.isAllowed2(caller, target, name, desc);
    }

    private static Object applyDenyAction3(Class<?> caller, Class<?> target,
                                           String name, String desc,
                                           Class<?> returnType, Object[] args)
        throws Throwable
    {
        SecurityAgent agent = agent();
        if (agent != null) {
            return agent.applyDenyAction2(caller, target, name, desc, returnType, args);
        }
        throw new SecurityException();
    }
}
