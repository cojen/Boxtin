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
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.security.ProtectionDomain;

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
    static final boolean DEBUG = Boolean.getBoolean(SecurityAgent.class.getName() + ".DEBUG");

    private static final ClassLoader ALT_LOADER;

    private static Instrumentation cInst;

    private static volatile SecurityAgent cAgent;

    static {
        if (SecurityAgent.class.getClassLoader() == null) {
            ALT_LOADER = ClassLoader.getSystemClassLoader();
        } else {
            ALT_LOADER = null;
        }
    }

    /**
     * The premain method should only be called by the instrumentation layer, when the JVM
     * starts.
     *
     * @hidden
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
            return new DefaultController();
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
    static SecurityAgent testActivate(Controller controller) {
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
            controller = new DefaultController();
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

        inst.addTransformer(agent.newTransformer(), true);

        try {
            // Transform the defineHiddenClass methods in order for them to apply transforms to
            // hidden classes. See #doTransform.
            inst.retransformClasses(MethodHandles.Lookup.class);
        } catch (UnmodifiableClassException e) {
            logException(e);
        }

        return true;
    }

    public static boolean isActivated() {
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

    private SecurityAgent(Controller controller) {
        mController = controller;
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
            {
                return SecurityAgent.this.transform(module, className, classBuffer);
            }
        };
    }

    private byte[] transform(Module module, String className, byte[] classBuffer) {
        try {
            return doTransform(module, className, classBuffer);
        } catch (Throwable e) {
            if (e instanceof ClassFormatException cfe && cfe.ignore) {
                return classBuffer;
            }

            logException("Failed to transform class: " + className, e);

            // Any exception thrown from this method is discarded, and the class won't be
            // transformed. Instead, return a fake class to be fail-secure.

            try {
                return EmptyClassMaker.make(className);
            } catch (Throwable e2) {
                // Everything is broken.
                logException(e2);
                Runtime.getRuntime().halt(1);
                throw e2;
            }
        }
    }

    // Is package-private for testing.
    byte[] doTransform(Module module, String className, byte[] classBuffer) throws Throwable {
        if (module.getClassLoader() == null) {
            // Classes loaded by the bootstrap class loader are allowed to call anything.

            if ("java/lang/invoke/MethodHandles$Lookup".equals(className)) {
                // The transform method isn't called for hidden classes. As a workaround,
                // transform the defineHiddenClass methods to directly call into the
                // SecurityAgent, which can then transform it.
                return ClassFileProcessor.begin(classBuffer).transformHiddenClassCreation();
            }

            return null;
        }

        Rules rules = mController.rulesForCaller(module);
        return rules == null ? null : ClassFileProcessor.begin(classBuffer).transform(rules);
    }

    /**
     * Is called by a reflection access method. An exception is thrown if the caller and target
     * modules differ, and the corresponding rule set denies access.
     *
     * @param caller the class which is calling the target
     * @param target the class which has an operation which is potentially denied for the caller
     * @param name target method name, or null/"<init>" if a constructor
     * @param desc partial descriptor for target method or constructor
     */
    static void check(Class<?> caller, Class<?> target, String name, String desc)
        throws SecurityException
    {
        if (!tryCheck(caller, target, name, desc)) {
            throw new SecurityException();
        }
    }

    /**
     * Is called by a reflection access method. True is returned if the caller and target
     * modules are the same or if the corresponding rule set allows access.
     *
     * @param caller the class which is calling the target
     * @param target the class which has an operation which is potentially denied for the caller
     * @param name target method name, or null/"<init>" if a constructor
     * @param desc partial descriptor for target method or constructor
     */
    static boolean tryCheck(Class<?> caller, Class<?> target, String name, String desc) {
        return caller.getModule() == target.getModule() || isAllowed(caller, target, name, desc);
    }

    /**
     * Note: Module comparison should be performed as a prerequisite.
     */
    static boolean isAllowed(Class<?> caller, Class<?> target, String name, String desc) {
        return Proxy.isAllowed(caller, target, name, desc);
    }

    /**
     * Note: Must be public because it needs to be accessed from the MethodHandles.Lookup class.
     *
     * @hidden
     */
    public static byte[] transformHiddenClass(MethodHandles.Lookup lookup, byte[] classBuffer) {
        return Proxy.transformHiddenClass(lookup, classBuffer);
    }

    /**
     * The agent should be loaded by the bootstrap ClassLoader, as specified by the
     * Boot-Class-Path option in the MANIFEST.MF file. The jar file can also be specified on
     * the module path, in which case it will ALSO be loaded by the system ClassLoader. All the
     * magic stuff going on in the static initializer of this class is intended to find the real
     * SecurityAgent instance, which should be invoked by the isAllowed method.
     */
    private static class Proxy {
        private static final MethodHandle IS_ALLOWED_H, TRANSFORM_H;

        static {
            var lookup = MethodHandles.lookup();

            var mt1 = MethodType.methodType
                (boolean.class, Class.class, Class.class, String.class, String.class);
            var mt2 = MethodType.methodType
                (byte[].class, MethodHandles.Lookup.class, byte[].class);

            MethodHandle mh1, mh2;

            try {
                Object agent = agent();

                if (agent != null) {
                    mh1 = lookup.findVirtual(SecurityAgent.class, "isAllowed2", mt1);
                    mh2 = lookup.findVirtual(SecurityAgent.class, "transformHiddenClass2", mt2);
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
                            mh2 = lookup.findVirtual(altClass, "transformHiddenClass2", mt2);
                        } else {
                            mh1 = lookup.findStatic(altClass, "isAllowed3", mt1);
                            mh2 = lookup.findStatic(altClass, "transformHiddenClass2", mt2);
                        }
                    } catch (ClassNotFoundException e) {
                        agent = null;
                        mh1 = lookup.findStatic(SecurityAgent.class, "isAllowed3", mt1);
                        mh2 = lookup.findStatic(SecurityAgent.class, "transformHiddenClass3", mt2);
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
            TRANSFORM_H = mh2;
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

        static byte[] transformHiddenClass(MethodHandles.Lookup lookup, byte[] classBuffer) {
            try {
                return (byte[]) TRANSFORM_H.invokeExact(lookup, classBuffer);
            } catch (Throwable e) {
                throw new SecurityException(e);
            }
        }
    }

    /**
     * Note: Must be public because it can be accessed from a different ClassLoader.
     *
     * @hidden
     */
    public static SecurityAgent agent() {
        return cAgent;
    }

    /**
     * Note: Must be public because it can be accessed from a different ClassLoader.
     *
     * Note: Module comparison should be performed as a prerequisite.
     *
     * @hidden
     */
    public boolean isAllowed2(Class<?> caller, Class<?> target, String name, String desc) {
        if (!target.getModule().isNamed()) {
            // Unnamed modules cannot have rules defined.
            return true;
        }

        Rules rules = mController.rulesForCaller(caller.getModule());

        if (rules == null) {
            return true;
        }

        Rules.ForClass forClass = rules.forClass(target);

        Rule rule;
        if (name == null || name.equals("<init>")) {
            rule = forClass.ruleForConstructor(desc);
        } else {
            rule = forClass.ruleForMethod(name, desc);
        }

        return rule.isAllowed();
    }

    /**
     * Note: Module comparison should be performed as a prerequisite.
     */
    private static boolean isAllowed3(Class<?> caller, Class<?> target, String name, String desc) {
        SecurityAgent agent = agent();
        return agent != null && agent.isAllowed2(caller, target, name, desc);
    }

    /**
     * Note: Must be public because it can be accessed from a different ClassLoader.
     *
     * @hidden
     */
    public byte[] transformHiddenClass2(MethodHandles.Lookup lookup, byte[] classBuffer) {
        byte[] bytes = agent().transform(lookup.lookupClass().getModule(), null, classBuffer);
        return bytes == null ? classBuffer : bytes;
    }

    private static byte[] transformHiddenClass3(MethodHandles.Lookup lookup, byte[] classBuffer) {
        SecurityAgent agent = agent();
        return agent == null ? classBuffer : agent.transformHiddenClass2(lookup, classBuffer);
    }
}
