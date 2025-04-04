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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.security.ProtectionDomain;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class SecurityAgent implements ClassFileTransformer {
    public static void premain(String agentArgs, Instrumentation inst) {
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
            controllerClass = Class.forName(controllerName);
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

        inst.addTransformer(new SecurityAgent(controller), true);
    }

    private final Controller mController;

    public SecurityAgent(Controller contoller) {
        mController = contoller;
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
            return doTransform(module, className, classBuffer);
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

    private byte[] doTransform(Module module, String className, byte[] classBuffer)
        throws Throwable
    {
        Checker checker = mController.checkerFor(module);

        if (checker != null) {
            var processor = ClassFileProcessor.begin(classBuffer);
            if (!processor.check(checker)) {
                return processor.redefine();
            }
        }

        return null;
    }
}
