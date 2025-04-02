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

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class JavaBaseRules {
    public static void applyRules(RulesBuilder b) {
        b.forPackage("java.io")
            .allowAll()

            .forClass("File")
            .denyAllMethods()
            .allowMethod("compareTo")
            .allowMethod("getName")
            .allowMethod("getParent")
            .allowMethod("getParentFile")
            .allowMethod("getPath")
            .allowMethod("toPath")
            .end()

            .forClass("FileInputStream")
            .denyAllConstructors()
            .end()

            .forClass("FileOutputStream")
            .denyAllConstructors()
            .end()

            .forClass("ObjectInputFilter.Config")
            .denyMethod("setSerialFilter")
            .denyMethod("setSerialFilterFactory")
            .end()

            .forClass("ObjectInputStream")
            .denyAllConstructors()
            .allowVariant("Ljava/io/InputStream;")
            .denyMethod("enableResolveObject")
            .denyMethod("readUnshared")
            .denyMethod("setObjectInputFilter")
            .end()

            .forClass("ObjectOutputStream")
            .denyAllConstructors()
            .allowVariant("Ljava/io/OutputStream;")
            .denyMethod("enableReplaceObject")
            .denyMethod("writeUnshared")
            .end()

            .forClass("PrintStream")
            .denyAllConstructors()
            .allowVariant("Ljava/io/OutputStream;")
            .allowVariant("Ljava/io/OutputStream;Z")
            .allowVariant("Ljava/io/OutputStream;ZLjava/nio/charset/Charset;")
            .end()

            .forClass("PrintWriter")
            .denyAllConstructors()
            .allowVariant("Ljava/io/OutputStream;")
            .allowVariant("Ljava/io/OutputStream;Z")
            .allowVariant("Ljava/io/OutputStream;ZLjava/nio/charset/Charset;")
            .allowVariant("Ljava/io/Writer;")
            .allowVariant("Ljava/io/Writer;Z")
            .end()

            .forClass("RandomAccessFile")
            .denyAllConstructors()
            .end()
            ;

        b.forPackage("java.lang")
            .allowAll()

            .forClass("Boolean")
            .denyMethod("getBoolean")
            .end()

            .forClass("Class")
            .denyMethod("forName")
            .allowVariant(String.class)
            .denyMethod("getClassLoader")
            .denyMethod("getClasses")
            .denyMethod("getConstructor")
            .denyMethod("getConstructors")
            .denyMethod("getDeclaredClasses")
            .denyMethod("getDeclaredConstructor")
            .denyMethod("getDeclaredConstructors")
            .denyMethod("getDeclaredField")
            .denyMethod("getDeclaredFields")
            .denyMethod("getDeclaredMethod")
            .denyMethod("getDeclaredMethods")
            .denyMethod("getDeclaringClass")
            .denyMethod("getEnclosingClass")
            .denyMethod("getEnclosingConstructor")
            .denyMethod("getEnclosingMethod")
            .denyMethod("getField")
            .denyMethod("getFields")
            .denyMethod("getMethod")
            .denyMethod("getMethods")
            .denyMethod("getNestHost")
            .denyMethod("getNestMembers")
            .denyMethod("getPermittedSubclasses")
            .denyMethod("getProtectionDomain")
            .denyMethod("getRecordComponents")
            .denyMethod("newInstance") // deprecated
            .end()

            .forClass("ClassLoader")
            .denyAll()
            .end()

            .forClass("Integer")
            .denyMethod("getInteger")
            .end()

            .forClass("Long")
            .denyMethod("getLong")
            .end()

            .forClass("Module")
            .denyMethod("getClassLoader")
            .end()

            .forClass("ModuleLayer")
            .denyMethod("defineModules")
            .denyMethod("defineModulesWithOneLoader")
            .denyMethod("defineModulesWithManyLoaders")
            .denyMethod("findLoader")
            .end()

            .forClass("ModuleLayer.Controller")
            .denyMethod("enableNativeAccess") // possibly redundant
            .end()

            .forClass("Package")
            .denyMethod("getPackage") // deprecated
            .end()

            .forClass("Process")
            .denyMethod("children")
            .denyMethod("descendants")
            .denyMethod("toHandle")
            .end()

            .forClass("ProcessBuilder")
            .denyMethod("environment")
            .denyMethod("start")
            .denyMethod("startPipeline")
            .end()

            .forClass("ProcessHandle")
            .denyMethod("allProcesses")
            .denyMethod("children")
            .denyMethod("current")
            .denyMethod("descendants")
            .denyMethod("of")
            .denyMethod("parent")
            .end()

            .forClass("Runtime")
            .denyMethod("addShutdownHook")
            .denyMethod("exec")
            .denyMethod("exit")
            .denyMethod("gc")
            .denyMethod("halt")
            .denyMethod("load")
            .denyMethod("loadLibrary")
            .denyMethod("removeShutdownHook")
            .denyMethod("runFinalization")
            .end()

            .forClass("SecurityManager")
            .denyAll()
            .end()

            .forClass("StackWalker")
            .denyMethod("getInstance")
            .allowVariant() // no args
            .end()

            .forClass("System")
            .denyAll()
            .allowMethod("arraycopy")
            .allowMethod("currentTimeMillis")
            .allowMethod("getLogger")
            .allowMethod("identityHashCode")
            .allowMethod("lineSeparator")
            .allowMethod("nanoTime")
            .allowField("out")
            .allowField("err")
            .end()

            .forClass("System.LoggerFinder")
            .denyAll()
            .end()

            .forClass("Thread")
            .denyAllConstructors()
            .allowVariant() // no args
            .allowVariant(Runnable.class)
            .allowVariant(Runnable.class, String.class)
            .allowVariant(String.class)
            .denyMethod("checkAccess")
            .denyMethod("enumerate")
            .denyMethod("getAllStackTraces")
            .denyMethod("getContextClassLoader")
            .denyMethod("getStackTrace")
            .denyMethod("interrupt")
            .denyMethod("setContextClassLoader")
            .denyMethod("setDaemon")
            .denyMethod("setDefaultUncaughtExceptionHandler")
            .denyMethod("setName")
            .denyMethod("setPriority")
            .denyMethod("setUncaughtExceptionHandler")
            .end()

            .forClass("Thread.Builder.OfPlatform")
            .denyMethod("daemon")
            .denyMethod("group")
            .denyMethod("priority")
            .end()

            .forClass("ThreadGroup")
            .denyAllConstructors()
            .denyMethod("checkAccess")
            .denyMethod("enumerate")
            .denyMethod("getParent")
            .denyMethod("interrupt")
            .denyMethod("list")
            .denyMethod("parentOf")
            .denyMethod("setDaemon")
            .denyMethod("setMaxPriority")
            .end()
            ;

        b.forPackage("java.lang.invoke")
            .allowAll()

            .forClass("LambdaMetafactory")
            // FIXME: LambdaMetafactory has special checks. The implementation MethodHandle
            // might reside in a different package. Are module checks sufficient?
            .end()

            .forClass("MethodHandles")
            .denyMethod("privateLookupIn")
            .denyMethod("reflectAs")
            .end()

            .forClass("MethodHandles.Lookup")
            .denyAllMethods()
            .allowMethod("dropLookupMode")
            .allowMethod("hasFullPrivilegeAccess")
            .allowMethod("lookupClass")
            .allowMethod("lookupModes")
            .end()

            .forClass("MethodType")
            .denyMethod("fromMethodDescriptorString")
            .end()
            ;

        // FIXME: More packages:
        // java.lang.module, java.lang.ref, java.lang.reflect, java.net, java.nio.channels,
        // java.nio.channels.spi, java.nio.charset.spi, java.nio.file, java.nio.file.attribute,
        // java.nio.file.spi, java.security, java.util, java.util.concurrent, java.util.spi,
        // javax.net, javax.net.ssl, javax.security.auth, javax.security.auth.login
    }
}
