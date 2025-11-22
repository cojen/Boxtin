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

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;

import java.nio.ByteBuffer;

import java.security.ProtectionDomain;

import static org.cojen.boxtin.CustomActions.findMethod;

/**
 * Defines a set of rules to deny operations in the java.base module which could be considered
 * harmful.
 *
 * @author Brian S. O'Neill
 * @see RulesApplier#java_base
 */
final class JavaBaseApplier implements RulesApplier {
    @Override
    public void applyRulesTo(RulesBuilder b) {
        MethodHandleInfo iv1, iv2, lv1, lv2;
        MethodHandleInfo gp1, gp2;
        MethodHandleInfo ctn;
        MethodHandleInfo cdc1, cdc2;
        MethodHandleInfo cfn1;
        MethodHandleInfo cgr1, cgr2, cgr3;
        MethodHandleInfo cna1;

        DenyAction restricted;

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            iv1 = findMethod(lookup, "intValue", Integer.class, String.class, int.class);
            iv2 = findMethod(lookup, "intValue", Integer.class, String.class, Integer.class);
            lv1 = findMethod(lookup, "longValue", Long.class, String.class, long.class);
            lv2 = findMethod(lookup, "longValue", Long.class, String.class, Long.class);

            gp1 = findMethod(lookup, "getProperty", String.class, String.class);
            gp2 = findMethod(lookup, "getProperty", String.class, String.class, String.class);

            ctn = findMethod(lookup, "checkThreadNew", boolean.class, Thread.class);

            cdc1 = findMethod(lookup, "checkDefineClass",
                              boolean.class, Caller.class, ClassLoader.class, String.class,
                                 byte[].class, int.class, int.class, ProtectionDomain.class);
            cdc2 = findMethod(lookup, "checkDefineClass",
                              boolean.class, Caller.class, ClassLoader.class, String.class,
                                 ByteBuffer.class, ProtectionDomain.class);

            cfn1 = findMethod(lookup, "checkForName",
                              boolean.class, Caller.class, String.class, boolean.class,
                                 ClassLoader.class);

            cgr1 = findMethod(lookup, "checkGetResource",
                              boolean.class, Caller.class, Class.class);
            cgr2 = findMethod(lookup, "checkGetResource",
                              boolean.class, Caller.class, ClassLoader.class);
            cgr3 = findMethod(lookup, "checkGetResource",
                              boolean.class, Caller.class, Module.class);

            cna1 = findMethod(lookup, "checkNativeAccess", boolean.class, Caller.class);

            restricted = DenyAction.exception("java.lang.IllegalCallerException").check(cna1);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        b.forModule("java.base", null, "27")

            .forPackage("java.io")
            .allowAll()

            .forClass("File")
            .denyAllMethods()
            .denyMethod(DenyAction.exception("java.io.FileNotFoundException"), "createNewFile")
            .denyMethod(DenyAction.exception("java.io.FileNotFoundException"), "createTempFile")
            .denyMethod(DenyAction.value(0L), "getFreeSpace")
            .denyMethod(DenyAction.value(0L), "getTotalSpace")
            .denyMethod(DenyAction.value(0L), "getUsableSpace")
            .denyMethod(DenyAction.value(0L), "lastModified")
            .denyMethod(DenyAction.value(0L), "length")
            .denyMethod(DenyAction.value(null), "list")
            .denyMethod(DenyAction.value(null), "listFiles")
            .denyMethod(DenyAction.value(null), "listRoots")
            .denyMethod(DenyAction.value(false), "canExecute")
            .denyMethod(DenyAction.value(false), "canRead")
            .denyMethod(DenyAction.value(false), "canWrite")
            .denyMethod(DenyAction.value(false), "delete")
            .denyMethod(DenyAction.value(false), "exists")
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
            .allowMethod("isAbsolute")

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
            .denyVariant(DenyAction.standard().check(cfn1),
                         String.class, boolean.class, ClassLoader.class)
            .denyMethod("getProtectionDomain")
            .denyMethod("newInstance") // deprecated
            .denyMethod(DenyAction.value(null).check(cgr1), "getResource")
            .denyMethod(DenyAction.value(null).check(cgr1), "getResourceAsStream")

            .forClass("ClassLoader")
            .denyMethod("clearAssertionStatus")
            .allowMethod("defineClass")
            .denyVariant("[BII") // deprecated
            // Cannot specify a ProtectionDomain when defining a class.
            .denyVariant(DenyAction.standard().check(cdc1),
                         "Ljava/lang/String;[BIILjava/security/ProtectionDomain;")
            .denyVariant(DenyAction.standard().check(cdc2),
                         "Ljava/lang/String;Ljava/nio/ByteBuffer;Ljava/security/ProtectionDomain;")
            .denyMethod("getSystemResource")
            .denyMethod("getSystemResourceAsStream")
            .denyMethod("getSystemResources")
            .denyMethod("setClassAssertionStatus")
            .denyMethod("setDefaultAssertionStatus")
            .denyMethod("setPackageAssertionStatus")
            .denyMethod(DenyAction.value(null).check(cgr2), "getResource")
            .denyMethod(DenyAction.value(null).check(cgr2), "getResourceAsStream")
            .denyMethod(DenyAction.empty().check(cgr2), "getResources")
            .denyMethod(DenyAction.empty().check(cgr2), "resources")

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
            .denyMethod(DenyAction.value(null).check(cgr3), "getResourceAsStream")

            .forClass("ModuleLayer")
            .denyMethod("defineModules")
            .denyMethod("defineModulesWithOneLoader")
            .denyMethod("defineModulesWithManyLoaders")

            .forClass("ModuleLayer.Controller")
            .denyMethod(restricted, "enableNativeAccess")

            .forClass("Package")
            .denyMethod("getPackage") // deprecated

            .forClass("Process")
            .denyAll()
            .allowMethod("isAlive")
            .allowMethod("exitValue")
            .allowMethod("onExit")
            .allowMethod("pid")
            .allowMethod("supportsNormalTermination")
            .allowMethod("toHandle")
            .allowMethod("waitFor")

            .forClass("ProcessBuilder")
            .denyAll()

            .forClass("ProcessHandle")
            .denyAll()
            .allowMethod("compareTo")
            .allowMethod("current")
            .allowMethod("isAlive")
            .allowMethod("onExit")
            .allowMethod("pid")
            .allowMethod("supportsNormalTermination")

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
            .denyMethod("getProperty")
            .denyVariant(DenyAction.custom(gp1), "Ljava/lang/String;")
            .denyVariant(DenyAction.custom(gp2), "Ljava/lang/String;Ljava/lang/String;")
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
            .denyMethod(DenyAction.value(0), "enumerate")  // do nothing
            .denyMethod(DenyAction.value(null).check(ctn), "getContextClassLoader")
            .denyMethod(DenyAction.standard().check(ctn), "setContextClassLoader")
            .denyMethod(DenyAction.empty().check(ctn), "setDaemon")
            .denyMethod(DenyAction.empty().check(ctn), "setName")
            .denyMethod(DenyAction.empty(), "setPriority") // do nothing
            .denyMethod(DenyAction.standard().check(ctn), "setUncaughtExceptionHandler")
            .allowAllConstructors()
            .allowMethod("checkAccess")
            .allowMethod("clone") // always throws an exception anyhow
            .allowMethod("currentThread")
            .allowMethod("dumpStack")
            .allowMethod("getDefaultUncaughtExceptionHandler")
            .allowMethod("getId")
            .allowMethod("getName")
            .allowMethod("getPriority")
            .allowMethod("getStackTrace")
            .allowMethod("getState")
            .allowMethod("getThreadGroup")
            .allowMethod("getUncaughtExceptionHandler")
            .allowMethod("holdsLock")
            .allowMethod("interrupt")
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

            .forPackage("java.lang.annotation").allowAll()

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
            .allowMethod("accessClass")
            .allowMethod("defineClass")
            // Defining of hidden classes requires that special changes be made to the
            // MethodHandles.Lookup class itself. See SecurityAgent#doTransform.
            .allowMethod("defineHiddenClass")
            .allowMethod("defineHiddenClassWithClassData")
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

            .forClass("ModuleReference")
            .allowAll()
            .denyMethod("open")

            .forPackage("java.lang.ref").allowAll()

            // Reflection operations will be denied below when checkReflection is applied.
            .forPackage("java.lang.reflect").allowAll()

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
            .denyMethod("setDatagramSocketImplFactory")

            .forClass("HttpURLConnection")
            .denyMethod("setFollowRedirects")
            .denyMethod("setRequestMethod")

            .forClass("MulticastSocket")
            .denyAllConstructors()

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
            .denyMethod("setSocketFactory")

            .forClass("Socket")
            .denyAllConstructors()
            .denyMethod("setSocketImplFactory")

            .forClass("UnixDomainSocketAddress")
            .denyAll()

            .forClass("URL")
            .denyAllConstructors()
            .denyMethod("of")
            .denyMethod("openConnection")
            .denyMethod("setURLStreamHandlerFactory")

            .forClass("URLClassLoader")
            .denyAllConstructors()
            .denyMethod("close")

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
            .denyMethod("open")

            .forClass("AsynchronousSocketChannel")
            .denyAllConstructors()
            .denyMethod("open")

            .forClass("DatagramChannel")
            .denyAllConstructors()
            .denyMethod("open")

            .forClass("FileChannel")
            .denyAllConstructors()
            .denyMethod("open")

            .forClass("ServerSocketChannel")
            .denyAllConstructors()
            .denyMethod("open")

            .forClass("SocketChannel")
            .denyAllConstructors()
            .denyMethod("open")

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
            .denyMethod("getFileSystem")
            .denyMethod("of")
            .denyMethod("register")
            .denyMethod("toAbsolutePath")
            .denyMethod("toRealPath")
            .denyMethod("toUri")

            .forClass("Paths")
            .denyAll()

            .forClass("Watchable")
            .denyMethod("register")

            .forPackage("java.nio.file.attribute")
            .allowAll()

            .forClass("UserPrincipalLookupService")
            .denyAll()

            .forPackage("java.nio.file.spi").denyAll()

            .forPackage("java.security")
            .allowAll()

            .forClass("AuthProvider")
            .denyMethod("login")
            .denyMethod("logout")
            .denyMethod("setCallbackHandler")

            .forClass("Guard")
            .denyMethod("checkGuard")

            .forClass("GuardedObject")
            .denyMethod("getObject")

            .forClass("IdentityScope")
            .denyMethod("getSystemScope")
            .denyMethod("setSystemScope")

            .forClass("KeyStore")
            .denyMethod("getInstance")

            .forClass("KeyStore.Builder")
            .denyMethod("newInstance")

            .forClass("Permission")
            .denyMethod("checkGuard")

            .forClass("Policy")
            .denyMethod("getInstance")
            .denyMethod("getPolicy")
            .denyMethod("setPolicy")

            .forClass("Provider")
            .denyAll()
            .allowMethod("clone")
            .allowMethod("contains")
            .allowMethod("containsKey")
            .allowMethod("containsValue")
            .allowMethod("elements")
            .allowMethod("entrySet")
            .allowMethod("forEach")
            .allowMethod("get")
            .allowMethod("getInfo")
            .allowMethod("getName")
            .allowMethod("getOrDefault")
            .allowMethod("getProperty")
            .allowMethod("getService")
            .allowMethod("getServices")
            .allowMethod("getVersionStr")
            .allowMethod("isConfigured")
            .allowMethod("isEmpty")
            .allowMethod("keys")
            .allowMethod("keySet")
            .allowMethod("list")
            .allowMethod("propertyNames")
            .allowMethod("size")
            .allowMethod("store")
            .allowMethod("storeToXML")
            .allowMethod("stringPropertyNames")
            .allowMethod("values")

            .forClass("Provider.Service")
            // Allowing construction would allow construction of arbitrary classes (which have
            // public constructors), bypassing reflection checks.
            .denyAllConstructors()

            .forClass("SecureClassLoader")
            .denyAllConstructors()
            .denyMethod("defineClass")

            .forClass("Security")
            .denyMethod("addProvider")
            .denyMethod("getProperty")
            .denyMethod("insertProviderAt")
            .denyMethod("removeProvider")
            .denyMethod("setProperty")

            .forPackage("java.security.cert").allowAll()

            .forPackage("java.security.interfaces").allowAll()

            .forPackage("java.security.spec").allowAll()

            .forPackage("java.text").allowAll()

            .forPackage("java.text.spi").denyAll()

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
            .allowVariant("Ljava/lang/Appendable;Ljava/util/Locale;")
            .allowVariant("Ljava/util/Locale;")
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

            .forClass("Scanner")
            .denyAllConstructors()
            .allowVariant("Ljava/io/InputStream;")
            .allowVariant("Ljava/io/InputStream;Ljava/lang.String;")
            .allowVariant("Ljava/io/InputStream;Ljava/nio/charset/Charset;")
            .allowVariant("Ljava/lang/Readable;")
            .allowVariant("Ljava/lang/String;")
            .allowVariant("Ljava/nio/channels/ReadableByteChannel;")
            .allowVariant("Ljava/nio/channels/ReadableByteChannel;Ljava/lang.String;")
            .allowVariant("Ljava/nio/channels/ReadableByteChannel;Ljava/nio/charset/Charset;")

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
            .denyMethod(DenyAction.empty(), "shutdownNow")

            .forPackage("java.util.concurrent.atomic").allowAll()

            .forPackage("java.util.concurrent.locks").allowAll()

            .forPackage("java.util.function").allowAll()

            .forPackage("java.util.jar")
            .allowAll()

            .forClass("JarFile")
            .denyAllConstructors()
            
            .forPackage("java.util.random").allowAll()

            .forPackage("java.util.regex").allowAll()

            .forPackage("java.util.spi").denyAll()

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
            .denyMethod("createServerSocket")

            .forClass("SocketFactory")
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

            .forPackage("javax.security.auth.spi").denyAll()

            .forPackage("javax.security.auth.x500").allowAll()

            ;

        RulesApplier.checkReflection().applyRulesTo(b);
    }
}
