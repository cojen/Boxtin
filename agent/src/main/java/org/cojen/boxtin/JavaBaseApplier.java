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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

import java.nio.ByteBuffer;

import java.security.ProtectionDomain;

import java.util.Properties;

/**
 * Defines a set of rules to deny operations in the java.base module which could be considered
 * harmful.
 *
 * @author Brian S. O'Neill
 * @see RulesApplier#java_base
 */
final class JavaBaseApplier implements RulesApplier {
    private static MethodType mt(Class<?> rtype, Class<?>... ptypes) {
        return MethodType.methodType(rtype, ptypes);
    }

    private MethodHandleInfo findMethod(MethodHandles.Lookup lookup, String name, MethodType mt)
        throws NoSuchMethodException, IllegalAccessException
    {
        return lookup.revealDirect(lookup.findStatic(CustomActions.class, name, mt));
    }

    @Override
    public void applyRulesTo(RulesBuilder b) {
        MethodHandleInfo iv1, iv2, lv1, lv2;
        MethodHandleInfo fp1, fp2, fp3, fp4, fp5, fp6;
        MethodHandleInfo cdc1, cdc2;
        MethodHandleInfo cfn1;
        MethodHandleInfo cgr1, cgr2, cgr3;
        MethodHandleInfo cna1;

        // Custom deny actions used by reflection methods.
        MethodHandleInfo cref1, cref2, cref3, cref4, cref5, cref6,
            cref7, cref8, cref9, cref10, cref11;

        // Custom deny actions used by MethodHandle.Lookup methods.
        MethodHandleInfo cmh1, cmh2, cmh3, cmh4, cmh5;

        DenyAction restricted, inaccessible;

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            iv1 = findMethod(lookup, "intValue", mt(Integer.class, String.class, int.class));
            iv2 = findMethod(lookup, "intValue", mt(Integer.class, String.class, Integer.class));
            lv1 = findMethod(lookup, "longValue", mt(Long.class, String.class, long.class));
            lv2 = findMethod(lookup, "longValue", mt(Long.class, String.class, Long.class));

            fp1 = findMethod(lookup, "getProperties", mt(Properties.class, Class.class));
            fp2 = findMethod(lookup, "getProperty",
                             mt(String.class, Class.class, String.class));
            fp3 = findMethod(lookup, "getProperty",
                             mt(String.class, Class.class, String.class, String.class));
            fp4 = findMethod(lookup, "setProperties",
                             mt(void.class, Class.class, Properties.class));
            fp5 = findMethod(lookup, "setProperty",
                             mt(String.class, Class.class, String.class, String.class));
            fp6 = findMethod(lookup, "clearProperty", mt(String.class, Class.class, String.class));

            cdc1 = findMethod(lookup, "checkDefineClass",
                              mt(boolean.class, Class.class, ClassLoader.class, String.class,
                                 byte[].class, int.class, int.class, ProtectionDomain.class));
            cdc2 = findMethod(lookup, "checkDefineClass",
                              mt(boolean.class, Class.class, ClassLoader.class, String.class,
                                 ByteBuffer.class, ProtectionDomain.class));

            cfn1 = findMethod(lookup, "checkForName",
                              mt(boolean.class, Class.class, String.class, boolean.class,
                                 ClassLoader.class));

            cgr1 = findMethod(lookup, "checkGetResource",
                              mt(boolean.class, Class.class, Class.class));
            cgr2 = findMethod(lookup, "checkGetResource",
                              mt(boolean.class, Class.class, ClassLoader.class));
            cgr3 = findMethod(lookup, "checkGetResource",
                              mt(boolean.class, Class.class, Module.class));

            cna1 = findMethod(lookup, "checkNativeAccess", mt(boolean.class, Class.class));

            cref1 = findMethod(lookup, "getConstructor",
                               mt(Constructor.class, Class.class, Class.class, Class[].class));
            cref2 = findMethod(lookup, "getConstructors",
                               mt(Constructor[].class, Class.class, Class.class));
            cref3 = findMethod(lookup, "getDeclaredConstructor",
                               mt(Constructor.class, Class.class, Class.class, Class[].class));
            cref4 = findMethod(lookup, "getDeclaredConstructors",
                               mt(Constructor[].class, Class.class, Class.class));
            cref5 = findMethod(lookup, "getDeclaredMethod",
                               mt(Method.class, Class.class,
                                  Class.class, String.class, Class[].class));
            cref6 = findMethod(lookup, "getDeclaredMethods",
                               mt(Method[].class, Class.class, Class.class));
            cref7 = findMethod(lookup, "getEnclosingConstructor",
                               mt(Constructor.class, Class.class, Class.class));
            cref8 = findMethod(lookup, "getEnclosingMethod",
                               mt(Method.class, Class.class, Class.class));
            cref9 = findMethod(lookup, "getMethod",
                               mt(Method.class, Class.class, Class.class,
                                  String.class, Class[].class));
            cref10 = findMethod(lookup, "getMethods",
                                mt(Method[].class, Class.class, Class.class));
            cref11 = findMethod(lookup, "getRecordComponents",
                                mt(RecordComponent[].class, Class.class, Class.class));

            cmh1 = findMethod(lookup, "lookupBind",
                              mt(MethodHandle.class, Class.class, MethodHandles.Lookup.class,
                                 Object.class, String.class, MethodType.class));
            cmh2 = findMethod(lookup, "lookupFindConstructor",
                              mt(MethodHandle.class, Class.class, MethodHandles.Lookup.class,
                                 Class.class, MethodType.class));
            cmh3 = findMethod(lookup, "lookupFindSpecial",
                              mt(MethodHandle.class, Class.class, MethodHandles.Lookup.class,
                                 Class.class, String.class, MethodType.class, Class.class));
            cmh4 = findMethod(lookup, "lookupFindStatic",
                              mt(MethodHandle.class, Class.class, MethodHandles.Lookup.class,
                                 Class.class, String.class, MethodType.class));
            cmh5 = findMethod(lookup, "lookupFindVirtual",
                              mt(MethodHandle.class, Class.class, MethodHandles.Lookup.class,
                                 Class.class, String.class, MethodType.class));

            restricted = DenyAction.checked
                (cna1, DenyAction.exception("java.lang.IllegalCallerException"));

            inaccessible = DenyAction.checked
                (findMethod(lookup, "checkSetAccessible",
                            mt(boolean.class, Class.class, Object.class, boolean.class)),
                 DenyAction.exception("java.lang.reflect.InaccessibleObjectException"));

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        b.forModule("java.base")

            .forPackage("java.io")
            .allowAll()

            .forClass("File")
            .denyAllMethods()
            .denyMethod(DenyAction.empty(), "list")
            .denyMethod(DenyAction.empty(), "listFiles")
            .denyMethod(DenyAction.empty(), "listRoots")
            .denyMethod(DenyAction.exception("java.io.FileNotFoundException"), "createNewFile")
            .denyMethod(DenyAction.exception("java.io.FileNotFoundException"), "createTempFile")
            .denyMethod(DenyAction.value(0L), "getFreeSpace")
            .denyMethod(DenyAction.value(0L), "getTotalSpace")
            .denyMethod(DenyAction.value(0L), "getUsableSpace")
            .denyMethod(DenyAction.value(0L), "lastModified")
            .denyMethod(DenyAction.value(0L), "length")
            .denyMethod(DenyAction.value(false), "canExecute")
            .denyMethod(DenyAction.value(false), "canRead")
            .denyMethod(DenyAction.value(false), "canWrite")
            .denyMethod(DenyAction.value(false), "delete")
            .denyMethod(DenyAction.value(false), "exists")
            .denyMethod(DenyAction.value(false), "isAbsolute")
            .denyMethod(DenyAction.value(false), "isDirectory")
            .denyMethod(DenyAction.value(false), "isFile")
            .denyMethod(DenyAction.value(false), "isHidden")
            .denyMethod(DenyAction.value(false), "mkdir")
            .denyMethod(DenyAction.value(false), "mkdirs")
            .denyMethod(DenyAction.value(false), "renameTo")
            .denyMethod(DenyAction.value(false), "setExecutable")
            .denyMethod(DenyAction.value(false), "setLastModified")
            .denyMethod(DenyAction.value(false), "setReadOnly")
            .denyMethod(DenyAction.value(false), "setReadable")
            .denyMethod(DenyAction.value(false), "setWritable")
            .allowMethod("compareTo")
            .allowMethod("getName")
            .allowMethod("getParent")
            .allowMethod("getParentFile")
            .allowMethod("getPath")
            .allowMethod("toPath")

            .forClass("FileInputStream")
            .denyAllConstructors(DenyAction.exception("java.io.FileNotFoundException"))
            .denyVariant(DenyAction.standard(), "Ljava/io/FileDescriptor;")

            .forClass("FileOutputStream")
            .denyAllConstructors(DenyAction.exception("java.io.FileNotFoundException"))
            .denyVariant(DenyAction.standard(), "Ljava/io/FileDescriptor;")

            .forClass("FileReader")
            .denyAllConstructors(DenyAction.exception("java.io.FileNotFoundException"))
            .denyVariant(DenyAction.standard(), "Ljava/io/FileDescriptor;")

            .forClass("FileWriter")
            .denyAllConstructors(DenyAction.exception("java.io.FileNotFoundException"))
            .denyVariant(DenyAction.standard(), "Ljava/io/FileDescriptor;")

            .forClass("ObjectInputFilter.Config")
            .denyMethod("setSerialFilter")
            .denyMethod("setSerialFilterFactory")

            .forClass("ObjectInputStream")
            .denyAllConstructors()
            .denyMethod("readUnshared")
            .denyMethod("setObjectInputFilter")

            .forClass("ObjectOutputStream")
            .denyAllConstructors()
            .allowVariant("Ljava/io/OutputStream;")
            .denyMethod("enableReplaceObject")
            .denyMethod("writeUnshared")

            .forClass("PrintStream")
            .denyAllConstructors(DenyAction.exception("java.io.FileNotFoundException"))
            .allowVariant("Ljava/io/OutputStream;")
            .allowVariant("Ljava/io/OutputStream;Z")
            .allowVariant("Ljava/io/OutputStream;ZLjava/lang/String;")
            .allowVariant("Ljava/io/OutputStream;ZLjava/nio/charset/Charset;")

            .forClass("PrintWriter")
            .denyAllConstructors(DenyAction.exception("java.io.FileNotFoundException"))
            .allowVariant("Ljava/io/OutputStream;")
            .allowVariant("Ljava/io/OutputStream;Z")
            .allowVariant("Ljava/io/OutputStream;ZLjava/nio/charset/Charset;")
            .allowVariant("Ljava/io/Writer;")
            .allowVariant("Ljava/io/Writer;Z")

            .forClass("RandomAccessFile")
            .denyAllConstructors(DenyAction.exception("java.io.FileNotFoundException"))

            .forPackage("java.lang")
            .allowAll()

            .forClass("Boolean")
            // Always return false.
            .denyMethod(DenyAction.value("false"), "getBoolean")

            .forClass("Class")
            .denyMethod("forName")
            .allowVariant(String.class)
            .allowVariant(Module.class, String.class)
            .denyVariant(DenyAction.checked(cfn1, DenyAction.standard()),
                         String.class, boolean.class, ClassLoader.class)
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
            .denyMethod("getProtectionDomain")
            .denyMethod(DenyAction.custom(cref11), "getRecordComponents")
            .denyMethod("newInstance") // deprecated
            .denyMethod(DenyAction.checked(cgr1, DenyAction.value(null)), "getResource")
            .denyMethod(DenyAction.checked(cgr1, DenyAction.value(null)), "getResourceAsStream")

            .forClass("ClassLoader")
            .denyMethod("clearAssertionStatus")
            .allowMethod("defineClass")
            .denyVariant("[BII") // deprecated
            // Cannot specify a ProtectionDomain when defining a class.
            .denyVariant(DenyAction.checked(cdc1, DenyAction.standard()),
                         "Ljava/lang/String;[BIILjava/security/ProtectionDomain;")
            .denyVariant(DenyAction.checked(cdc2, DenyAction.standard()),
                         "Ljava/lang/String;Ljava/nio/ByteBuffer;Ljava/security/ProtectionDomain;")
            .denyMethod("getSystemResource")
            .denyMethod("getSystemResourceAsStream")
            .denyMethod("getSystemResources")
            .denyMethod("setClassAssertionStatus")
            .denyMethod("setDefaultAssertionStatus")
            .denyMethod("setPackageAssertionStatus")
            .denyMethod(DenyAction.checked(cgr2, DenyAction.value(null)), "getResource")
            .denyMethod(DenyAction.checked(cgr2, DenyAction.value(null)), "getResourceAsStream")
            .denyMethod(DenyAction.checked(cgr2, DenyAction.empty()), "getResources")
            .denyMethod(DenyAction.checked(cgr2, DenyAction.empty()), "resources")

            .forClass("Integer")
            .denyMethod("getInteger")
            // Always return null.
            .denyVariant(DenyAction.value(null), "Ljava/lang/String;")
            // Always return the default value.
            .denyVariant(DenyAction.custom(iv1), "Ljava/lang/String;I")
            .denyVariant(DenyAction.custom(iv2), "Ljava/lang/String;Ljava/lang/Integer;")

            .forClass("Long")
            .denyMethod("getLong")
            // Always return null.
            .denyVariant(DenyAction.value(null), "Ljava/lang/String;")
            // Always return the default value.
            .denyVariant(DenyAction.custom(lv1), "Ljava/lang/String;J")
            .denyVariant(DenyAction.custom(lv2), "Ljava/lang/String;Ljava/lang/Long;")

            .forClass("Module")
            .denyMethod(DenyAction.checked(cgr3, DenyAction.value(null)), "getResourceAsStream")

            .forClass("ModuleLayer")
            .denyMethod("defineModules")
            .denyMethod("defineModulesWithOneLoader")
            .denyMethod("defineModulesWithManyLoaders")

            .forClass("ModuleLayer.Controller")
            .denyMethod(restricted, "enableNativeAccess")

            .forClass("Package")
            .denyMethod("getPackage") // deprecated

            .forClass("Process")
            .denyMethod("children")
            .denyMethod("descendants")
            .denyMethod("toHandle")

            .forClass("ProcessBuilder")
            .denyMethod("environment")
            .denyMethod("start")
            .denyMethod("startPipeline")

            .forClass("ProcessHandle")
            .denyMethod("allProcesses")
            .denyMethod("current")
            .denyMethod("of")
            .denyMethod("children")
            .denyMethod("descendants")
            .denyMethod("parent")

            .forClass("Runtime")
            .denyAll()
            .denyMethod(restricted, "load")
            .denyMethod(restricted, "loadLibrary")
            .denyMethod(DenyAction.empty(), "gc") // do nothing
            .denyMethod(DenyAction.empty(), "runFinalization") // do nothing
            .allowMethod("availableProcessors")
            .allowMethod("freeMemory")
            .allowMethod("getRuntime")
            .allowMethod("maxMemory")
            .allowMethod("totalMemory")
            .allowMethod("version")

            .forClass("System")
            .denyAll()
            .denyMethod(restricted, "load")
            .denyMethod(restricted, "loadLibrary")
            .denyMethod("getenv")
            // Return an empty map of environment variables.
            .denyVariant(DenyAction.empty())
            // Return null for all environment variables.
            .denyVariant(DenyAction.value(null), "Ljava/lang/String;")
            // Return a filtered set of properties.
            .denyMethod(DenyAction.custom(fp1), "getProperties")
            .denyMethod("getProperty")
            .denyVariant(DenyAction.custom(fp2), "Ljava/lang/String;")
            .denyVariant(DenyAction.custom(fp3), "Ljava/lang/String;Ljava/lang/String;")
            .denyMethod(DenyAction.custom(fp4), "setProperties")
            .denyMethod(DenyAction.custom(fp5), "setProperty")
            .denyMethod(DenyAction.custom(fp6), "clearProperty")
            .denyMethod(DenyAction.empty(), "gc") // do nothing
            .denyMethod(DenyAction.empty(), "runFinalization") // do nothing
            .allowMethod("arraycopy")
            .allowMethod("currentTimeMillis")
            .allowMethod("getLogger")
            .allowMethod("identityHashCode")
            .allowMethod("lineSeparator")
            .allowMethod("nanoTime")

            .forClass("System.LoggerFinder")
            .denyAll()

            .forClass("Thread")
            .denyAll()
            .denyMethod(DenyAction.value(1), "activeCount")
            .denyMethod(DenyAction.value(null), "getContextClassLoader")
            .denyMethod(DenyAction.value(0), "enumerate")  // do nothing
            .denyMethod(DenyAction.empty(), "setDaemon")   // do nothing
            .denyMethod(DenyAction.empty(), "setName")     // do nothing
            .denyMethod(DenyAction.empty(), "setPriority") // do nothing
            .allowAllConstructors()
            .allowMethod("clone") // always throws an exception anyhow
            .allowMethod("currentThread")
            .allowMethod("dumpStack")
            .allowMethod("getDefaultUncaughtExceptionHandler")
            .allowMethod("getId")
            .allowMethod("getName")
            .allowMethod("getPriority")
            .allowMethod("getState")
            .allowMethod("getThreadGroup")
            .allowMethod("getUncaughtExceptionHandler")
            .allowMethod("holdsLock")
            .allowMethod("interrupted")
            .allowMethod("isAlive")
            .allowMethod("isDaemon")
            .allowMethod("isInterrupted")
            .allowMethod("isVirtual")
            .allowMethod("join")
            .allowMethod("ofPlatform")
            .allowMethod("ofVirtual")
            .allowMethod("onSpinWait")
            .allowMethod("run")
            .allowMethod("sleep")
            .allowMethod("start")
            .allowMethod("startVirtualThread")
            .allowMethod("threadId")
            .allowMethod("yield")

            .forClass("Thread.Builder.OfPlatform")
            .denyMethod("priority")

            .forClass("ThreadGroup")
            .denyMethod(DenyAction.value(1), "activeCount")
            .denyMethod(DenyAction.value(1), "activeGroupCount")
            .denyMethod(DenyAction.value(0), "enumerate")  // do nothing
            .denyMethod("getParent")
            .denyMethod("interrupt")
            .denyMethod(DenyAction.empty(), "list") // do nothing
            .denyMethod("parentOf")
            .denyMethod(DenyAction.empty(), "setDaemon")      // do nothing
            .denyMethod(DenyAction.empty(), "setMaxPriority") // do nothing

            .forPackage("java.lang.annotation")
            .allowAll()

            .forClass("AnnotationTypeMismatchException")
            .denyMethod("element")

            .forPackage("java.lang.classfile").allowAll()

            .forPackage("java.lang.classfile.attribute").allowAll()

            .forPackage("java.lang.classfile.constantpool").allowAll()

            .forPackage("java.lang.classfile.instruction").allowAll()

            .forPackage("java.lang.constant").allowAll()

            .forPackage("java.lang.foreign")
            .allowAll()

            .forClass("AddressLayout")
            .denyMethod(restricted, "withTargetLayout")

            .forClass("Linker")
            .denyMethod(restricted, "downcallHandle")
            .denyMethod(restricted, "upcallStub")

            .forClass("MemorySegment")
            .denyMethod(restricted, "reinterpret")

            .forClass("SymbolLookup")
            .denyMethod(restricted, "libraryLookup")

            .forPackage("java.lang.invoke")
            .allowAll()

            .forClass("MethodHandles.Lookup")
            .denyAllMethods()
            .denyMethod(DenyAction.custom(cmh1), "bind")
            .denyMethod(DenyAction.custom(cmh2), "findConstructor")
            .denyMethod(DenyAction.custom(cmh3), "findSpecial")
            .denyMethod(DenyAction.custom(cmh4), "findStatic")
            .denyMethod(DenyAction.custom(cmh5), "findVirtual")
            .allowMethod("accessClass")
            .allowMethod("dropLookupMode")
            .allowMethod("ensureInitialized")
            .allowMethod("findClass")
            .allowMethod("findGetter")
            .allowMethod("findSetter")
            .allowMethod("findStaticGetter")
            .allowMethod("findStaticSetter")
            .allowMethod("findStaticVarHandle")
            .allowMethod("findVarHandle")
            .allowMethod("hasFullPrivilegeAccess")
            .allowMethod("hasPrivateAccess")
            .allowMethod("in")
            .allowMethod("lookupClass")
            .allowMethod("lookupModes")
            .allowMethod("previousLookupClass")
            .allowMethod("revealDirect")
            .allowMethod("unreflect")
            .allowMethod("unreflectConstructor")
            .allowMethod("unreflectGetter")
            .allowMethod("unreflectSetter")
            .allowMethod("unreflectSpecial")
            .allowMethod("unreflectVarHandle")

            .forPackage("java.lang.module")
            .allowAll()

            .forClass("Configuration")
            .denyMethod("resolve")
            .denyMethod("resolveAndBind")

            .forClass("ModuleFinder")
            .denyAll()

            .forClass("ModuleReader")
            .denyAll()
            .allowMethod("close")

            .forClass("ModuleReference")
            .allowAll()
            .denyMethod("open")

            .forPackage("java.lang.ref").allowAll()

            .forPackage("java.lang.reflect")
            .allowAll()

            .forClass("AccessibleObject")
            .denyMethod(inaccessible, "setAccessible")
            .denyMethod(DenyAction.value(false), "trySetAccessible")

            .forClass("Constructor")
            .denyMethod(inaccessible, "setAccessible") // FIXME: inherited
            .denyMethod(DenyAction.value(false), "trySetAccessible") // FIXME: inherited

            .forClass("Field")
            .denyMethod(inaccessible, "setAccessible") // FIXME: inherited
            .denyMethod(DenyAction.value(false), "trySetAccessible") // FIXME: inherited

            .forClass("Method")
            .denyMethod(inaccessible, "setAccessible") // FIXME: inherited
            .denyMethod(DenyAction.value(false), "trySetAccessible") // FIXME: inherited

            .forClass("RecordComponent")
            .denyMethod("getAccessor")

            .forClass("Proxy")
            .denyMethod("getProxyClass") // deprecated

            .forPackage("java.lang.runtime").allowAll()

            .forPackage("java.math").allowAll()

            .forPackage("java.net")
            .allowAll()

            .forClass("Authenticator")
            .denyMethod(DenyAction.value(null), "getDefault")
            .denyMethod("setDefault")
            .denyMethod("requestPasswordAuthentication")

            .forClass("CookieHandler")
            .denyMethod(DenyAction.value(null), "getDefault")
            .denyMethod("setDefault")

            .forClass("DatagramSocket")
            .denyAllConstructors()
            .denyMethod("bind")
            .denyMethod("connect")
            .denyMethod("getOption")
            .denyMethod("joinGroup")
            .denyMethod("leaveGroup")
            .denyMethod("send")
            .denyMethod("setDatagramSocketImplFactory")
            .denyMethod("setOption")

            .forClass("HttpURLConnection")
            .denyMethod("setFollowRedirects")
            .denyMethod("setRequestMethod")

            .forClass("InetAddress")
            .denyMethod("getByName")
            .denyMethod("getAllByName")
 
            .forClass("InetSocketAddress")
            .denyAllConstructors()
            .allowVariant(int.class)
            .allowVariant("Ljava/net/InetAddress;I")

            .forClass("MulticastSocket")
            .denyAllConstructors()
            .denyMethod("joinGroup")
            .denyMethod("leaveGroup")
            .denyMethod("send")

            .forClass("ProxySelector")
            .denyAllConstructors()
            .denyMethod(DenyAction.value(null), "getDefault")
            .denyMethod("setDefault")

            .forClass("ResponseCache")
            .denyAllConstructors()
            .denyMethod(DenyAction.value(null), "getDefault")
            .denyMethod("setDefault")

            .forClass("ServerSocket")
            .denyAllConstructors()
            .denyMethod("accept")
            .denyMethod("bind")
            .denyMethod("setSocketFactory")

            .forClass("Socket")
            .denyAllConstructors()
            .denyMethod("bind")
            .denyMethod("connect")
            .denyMethod("setSocketImplFactory")

            .forClass("URL")
            .denyAllConstructors()
            .denyMethod("of")
            .denyMethod("openConnection")
            .denyMethod("setURLStreamHandlerFactory")

            .forClass("URLClassLoader")
            .denyAllConstructors()
            .denyMethod("close")
            .denyMethod("findResource") // FIXME: inherited
            .denyMethod("findResources") // FIXME: inherited
            .denyMethod("getResourceAsStream") // FIXME: inherited

            .forClass("URLConnection")
            .denyMethod("setContentHandlerFactory")
            .denyMethod("setFileNameMap")

            .forClass("URLStreamHandler")
            .denyMethod("setURL")

            .forPackage("java.net.spi").denyAll()

            .forPackage("java.nio").allowAll()

            .forPackage("java.nio.channels")
            .allowAll()

            .forClass("AsynchronousFileChannel")
            .denyAllConstructors()
            .denyMethod("open")

            .forClass("AsynchronousServerSocketChannel")
            .denyAllConstructors()
            .denyMethod("bind") // FIXME: inherit from NetworkChannel
            .denyMethod("open")

            .forClass("AsynchronousSocketChannel")
            .denyAllConstructors()
            .denyMethod("bind") // FIXME: inherit from NetworkChannel
            .denyMethod("connect")

            .forClass("DatagramChannel")
            .denyAllConstructors()
            .denyMethod("bind") // FIXME: inherit from NetworkChannel
            .denyMethod("connect")
            .denyMethod("receive")
            .denyMethod("send")

            .forClass("FileChannel")
            .denyMethod("open")

            .forClass("MulticastChannel")
            .denyMethod("join")

            .forClass("NetworkChannel")
            .denyMethod("bind")

            .forClass("ServerSocketChannel")
            .denyAllConstructors()
            .denyMethod("accept")
            .denyMethod("bind") // FIXME: inherit from NetworkChannel

            .forClass("SocketChannel")
            .denyAllConstructors()
            .denyMethod("open")
            .denyMethod("bind") // FIXME: inherit from NetworkChannel
            .denyMethod("connect")

            .forPackage("java.nio.channels.spi").denyAll()

            .forPackage("java.nio.charset").allowAll()

            .forPackage("java.nio.charset.spi").denyAll()

            .forPackage("java.nio.file")
            .allowAll()

            .forClass("Files")
            .denyAll()

            .forClass("FileSystems")
            .denyAll()

            .forClass("Path")
            .denyMethod("of")
            .denyMethod("register")
            .denyMethod("toAbsolutePath")
            .denyMethod("toRealPath")
            .denyMethod("toUri")

            .forClass("Paths")
            .denyAll()

            .forClass("SecureDirectoryStream")
            .denyAll()

            .forClass("Watchable")
            .denyMethod("register")

            .forPackage("java.nio.file.attribute")
            .allowAll()

            .forClass("AclFileAttributeView")
            .denyAll()
            .allowMethod("name")

            .forClass("BasicFileAttributeView")
            .denyAll()
            .allowMethod("name")

            .forClass("DosFileAttributeView")
            .denyAll()
            .allowMethod("name")

            .forClass("FileOwnerAttributeView")
            .denyAll()
            .allowMethod("name")

            .forClass("PosixFileAttributeView")
            .denyAll()
            .allowMethod("name")

            .forClass("UserDefinedFileAttributeView")
            .denyAll()
            .allowMethod("name")

            .forClass("UserPrincipalLookupService")
            .denyAll()

            .forPackage("java.nio.file.spi").denyAll()

            .forPackage("java.security")
            .allowAll()

            .forClass("AccessControlContext") // deprecated
            .denyAll()

            .forClass("AuthProvider")
            .denyMethod("login")
            .denyMethod("logout")
            .denyMethod("setCallbackHandler")

            .forClass("Guard")
            .denyMethod("checkGuard")

            .forClass("GuardedObject")
            .denyMethod("getObject")

            .forClass("Identity") // deprecated
            .denyAll()

            .forClass("IdentityScope") // deprecated
            .denyAll()

            .forClass("KeyStore")
            .denyMethod("getInstance")

            .forClass("Permission")
            .denyMethod("checkGuard")

            .forClass("Policy")
            .denyMethod("getInstance")
            .denyMethod("getPolicy")
            .denyMethod("setPolicy")

            .forClass("Provider")
            .denyMethod("clear")
            .denyMethod("compute")
            .denyMethod("computeIfAbsent")
            .denyMethod("computeIfPresent")
            .denyMethod("merge")
            .denyMethod("put")
            .denyMethod("putIfAbsent")
            .denyMethod("putService")
            .denyMethod("remove")
            .denyMethod("removeService")
            .denyMethod("replace")
            .denyMethod("replaceAll")

            .forClass("SecureClassLoader")
            .denyAllConstructors()
            .denyMethod("defineClass") // FIXME: inherited? just deny the new variants?

            .forClass("Security")
            .denyMethod("addProvider")
            .denyMethod("getProperty")
            .denyMethod("insertProviderAt")
            .denyMethod("removeProvider")
            .denyMethod("setProperty")

            .forClass("Signer") // deprecated
            .denyAll()

            .forPackage("java.security.cert").allowAll()

            .forPackage("java.security.interfaces").allowAll()

            .forPackage("java.security.spec").allowAll()

            .forPackage("java.text").allowAll()

            .forPackage("java.text.spi").allowAll()

            .forPackage("java.time").allowAll()

            .forPackage("java.time.chrono").allowAll()

            .forPackage("java.time.format").allowAll()

            .forPackage("java.time.temporal").allowAll()

            .forPackage("java.time.zone").allowAll()

            .forPackage("java.util")
            .allowAll()

            .forClass("Formatter")
            .denyAllConstructors()
            .allowVariant() // no args
            .allowVariant("Ljava/lang/Appendable;")
            .allowVariant("Ljava/util/Locale;")
            .allowVariant("Ljava/lang/Appendable;Ljava/util/Locale;")
            .allowVariant("Ljava/io/PrintStream;")
            .allowVariant("Ljava/io/OutputStream;")
            .allowVariant("Ljava/io/OutputStream;Ljava/lang/String;")
            .allowVariant("Ljava/io/OutputStream;Ljava/lang/String;Ljava/util/Locale;")
            .allowVariant("Ljava/io/OutputStream;Ljava/nio/charset/Charset;Ljava/util/Locale;")

            .forClass("Locale")
            .denyMethod("setDefault")

            .forClass("ResourceBundle")
            .denyMethod("getBundle")
            .allowVariant("Ljava/lang.String;")
            .allowVariant("Ljava/lang.String;Ljava/util/Locale;")
            .allowVariant("Ljava/lang.String;Ljava/util/ResourceBundle$Control;")
            .allowVariant("Ljava/lang.String;Ljava/util/Locale;Ljava/util/ResourceBundle$Control;")

            .forClass("TimeZone")
            .denyMethod("setDefault")

            .forPackage("java.util.concurrent")
            .allowAll()

            .forClass("ForkJoinPool")
            // These methods are denied because shared instances can be accessed by calling the
            // ForkJoinTask.getPool() method. Rather than throwing an exception, calling these
            // methods has no effect, just like ForkJoinPool.commonPool.
            .denyMethod(DenyAction.value(null), "close")
            .denyMethod(DenyAction.value(null), "shutdown")
            .denyMethod(DenyAction.value(null), "shutdownNow")

            .forPackage("java.util.concurrent.atomic").allowAll()

            .forPackage("java.util.concurrent.locks").allowAll()

            .forPackage("java.util.function").allowAll()

            .forPackage("java.util.jar")
            .allowAll()

            .forClass("JarFile")
            .denyAllConstructors()
            
            .forPackage("java.util.random").allowAll()

            .forPackage("java.util.regex").allowAll()

            .forPackage("java.util.spi")
            .allowAll()

            .forClass("LocaleServiceProvider")
            .denyAllConstructors()

            .forPackage("java.util.stream").allowAll()

            .forPackage("java.util.zip")
            .allowAll()

            .forClass("ZipFile")
            .denyAllConstructors()

            .forPackage("javax.crypto").allowAll()

            .forPackage("javax.crypto.interfaces").allowAll()

            .forPackage("javax.crypto.spec").allowAll()

            .forPackage("javax.net")
            .allowAll()

            .forClass("ServerSocketFactory")
            .denyAllConstructors()
            .denyMethod("createServerSocket")

            .forClass("SocketFactory")
            .denyAllConstructors()
            .denyMethod("createSocket")

            .forPackage("javax.net.ssl")
            .allowAll()

            .forClass("ExtendedSSLSession")
            .denyMethod("getSessionContext")

            .forClass("HttpsURLConnection")
            .denyMethod("setDefaultHostnameVerifier")
            .denyMethod("setDefaultSSLSocketFactory")
            .denyMethod("setSSLSocketFactory")

            .forClass("SSLContext")
            .denyMethod("setDefault")

            .forClass("SSLServerSocket")
            .denyAllConstructors()

            .forClass("SSLSession")
            .denyMethod("getSessionContext")

            .forClass("SSLSocket")
            .denyAllConstructors()

            .forPackage("javax.security.auth")
            .allowAll()

            .forClass("Subject")
            .denyMethod("doAs")
            .denyMethod("doAsPrivileged")
            .denyMethod("getSubject")
            .denyMethod("setReadOnly")

            .forPackage("javax.security.auth.callback").allowAll()

            .forPackage("javax.security.auth.login")
            .allowAll()

            .forClass("Configuration")
            .denyMethod("getInstance")

            .forPackage("javax.security.auth.spi").allowAll()

            .forPackage("javax.security.auth.x500").allowAll()

            ;
    }
}
