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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

import java.nio.ByteBuffer;

import java.security.ProtectionDomain;

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
        MethodHandleInfo iv1, iv2, lv1, lv2, sv1;
        MethodHandleInfo cdc1, cdc2;

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            iv1 = findMethod(lookup, "intValue", mt(Integer.class, String.class, int.class));
            iv2 = findMethod(lookup, "intValue", mt(Integer.class, String.class, Integer.class));
            lv1 = findMethod(lookup, "longValue", mt(Long.class, String.class, long.class));
            lv2 = findMethod(lookup, "longValue", mt(Long.class, String.class, Long.class));
            sv1 = findMethod(lookup, "stringValue", mt(String.class, String.class, String.class));

            cdc1 = findMethod(lookup, "checkDefineClass",
                              mt(boolean.class, Class.class, ClassLoader.class, String.class,
                                 byte[].class, int.class, int.class, ProtectionDomain.class));
            cdc2 = findMethod(lookup, "checkDefineClass",
                              mt(boolean.class, Class.class, ClassLoader.class, String.class,
                                 ByteBuffer.class, ProtectionDomain.class));

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

            .forClass("FileOutputStream")
            .denyAllConstructors(DenyAction.exception("java.io.FileNotFoundException"))

            .forClass("ObjectInputFilter.Config")
            .denyMethod("setSerialFilter")
            .denyMethod("setSerialFilterFactory")

            .forClass("ObjectInputStream")
            .denyAllConstructors()
            .allowVariant("Ljava/io/InputStream;")
            .denyMethod("enableResolveObject")
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
            .callerCheck()
            // Always return false.
            .denyMethod(DenyAction.value("false"), "getBoolean")

            // Note: The methods which return Constructors, Methods, and RecordComponents are
            // treated specially. Denying access here only denies access to those methods
            // themselves. Checks are put in place for when callers obtain members, ensuring
            // that access is allowed to the underlying class member as if it was called
            // directly. See the Reflection class.
            .forClass("Class")
            .callerCheck()
            // FIXME: Consider allowing the variant which has the initialize parameter, but
            // always treat it as false.
            .denyMethod("forName")
            .allowVariant(String.class)
            .allowVariant(Module.class, String.class)
            .denyMethod("getConstructor")
            .denyMethod("getConstructors")
            .denyMethod("getDeclaredConstructor")
            .denyMethod("getDeclaredConstructors")
            .denyMethod("getDeclaredMethod")
            .denyMethod("getDeclaredMethods")
            .denyMethod("getEnclosingConstructor")
            .denyMethod("getEnclosingMethod")
            .denyMethod("getMethod")
            .denyMethod("getMethods")
            .denyMethod("getProtectionDomain")
            .denyMethod("getRecordComponents")
            .denyMethod("getResource")
            .denyMethod("getResourceAsStream")
            .denyMethod("newInstance") // deprecated

            .forClass("ClassLoader")
            .denyMethod("clearAssertionStatus")
            .denyMethod("findResource")
            .denyMethod("findResources")
            .denyMethod("getResource")
            .denyMethod("getResourceAsStream")
            .denyMethod("getResources")
            .denyMethod("getSystemResource")
            .denyMethod("getSystemResourceAsStream")
            .denyMethod("getSystemResources")
            .denyMethod("resources")
            .denyMethod("setClassAssertionStatus")
            .denyMethod("setDefaultAssertionStatus")
            .denyMethod("setPackageAssertionStatus")
            .callerCheck()
            .allowMethod("defineClass")
            .denyVariant("[BII") // deprecated
            // Cannot specify a ProtectionDomain when defining a class.
            .denyVariant(DenyAction.checked(cdc1, DenyAction.standard()),
                         "Ljava/lang/String;[BIILjava/security/ProtectionDomain;")
            .denyVariant(DenyAction.checked(cdc2, DenyAction.standard()),
                         "Ljava/lang/String;Ljava/nio/ByteBuffer;Ljava/security/ProtectionDomain;")

            .forClass("Integer")
            .callerCheck()
            .denyMethod("getInteger")
            // Always return null.
            .denyVariant(DenyAction.value(null), "Ljava/lang/String;")
            // Always return the default value.
            .denyVariant(DenyAction.custom(iv1), "Ljava/lang/String;I")
            .denyVariant(DenyAction.custom(iv2), "Ljava/lang/String;Ljava/lang/Integer;")

            .forClass("Long")
            .callerCheck()
            .denyMethod("getLong")
            // Always return null.
            .denyVariant(DenyAction.value(null), "Ljava/lang/String;")
            // Always return the default value.
            .denyVariant(DenyAction.custom(lv1), "Ljava/lang/String;J")
            .denyVariant(DenyAction.custom(lv2), "Ljava/lang/String;Ljava/lang/Long;")

            .forClass("Module")
            .callerCheck()
            .denyMethod("getResourceAsStream")

            .forClass("ModuleLayer")
            .denyMethod("defineModules")
            .denyMethod("defineModulesWithOneLoader")
            .denyMethod("defineModulesWithManyLoaders")
            .denyMethod("findLoader")

            .forClass("ModuleLayer.Controller")
            // Is @CallerSensitive and @Restricted, and so this check might be redundant.
            .callerCheck()
            .denyMethod("enableNativeAccess")

            .forClass("Package")
            .callerCheck()
            .denyMethod("getPackage")

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
            .callerCheck()
            .denyMethod("children")
            .denyMethod("descendants")
            .denyMethod("parent")

            .forClass("Runtime")
            .callerCheck()
            .denyAll()
            .allowMethod("availableProcessors")
            .allowMethod("freeMemory")
            .allowMethod("gc")
            .allowMethod("getRuntime")
            .allowMethod("maxMemory")
            .allowMethod("runFinalization")
            .allowMethod("totalMemory")
            .allowMethod("version")

            .forClass("System")
            .callerCheck()
            .denyAll()
            // FIXME: Allow access to a subset of the standard properties.
            .denyMethod("getProperty")
            // Always return null.
            .denyVariant(DenyAction.value(null), "Ljava/lang/String;")
            // Always return the default value.
            .denyVariant(DenyAction.custom(sv1), "Ljava/lang/String;Ljava/lang/String;")
            .allowMethod("arraycopy")
            .allowMethod("currentTimeMillis")
            .allowMethod("gc")
            .allowMethod("getLogger")
            .allowMethod("identityHashCode")
            .allowMethod("lineSeparator")
            .allowMethod("nanoTime")
            .allowMethod("runFinalization")

            .forClass("System.LoggerFinder")
            .denyAll()

            .forClass("Thread")
            .denyAll()
            .allowAllConstructors()
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
            .allowMethod("setDaemon")
            .allowMethod("sleep")
            .allowMethod("start")
            .allowMethod("startVirtualThread")
            .allowMethod("threadId")
            .allowMethod("yield")

            .forClass("Thread.Builder.OfPlatform")
            .callerCheck()
            .denyMethod("priority")

            .forClass("ThreadGroup")
            .denyMethod("enumerate")
            .denyMethod("getParent")
            .denyMethod("interrupt")
            .denyMethod("list")
            .denyMethod("parentOf")
            .denyMethod("setDaemon")
            .denyMethod("setMaxPriority")

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
            // Is @CallerSensitive and @Restricted, and so this check might be redundant.
            .callerCheck()
            .denyMethod("withTargetLayout")

            .forClass("Linker")
            // Is @CallerSensitive and @Restricted, and so these checks might be redundant.
            .callerCheck()
            .denyMethod("downcallHandle")
            .denyMethod("upcallStub")

            .forClass("MemorySegment")
            // Is @CallerSensitive and @Restricted, and so this check might be redundant.
            .callerCheck()
            .denyMethod("reinterpret")

            .forClass("SymbolLookup")
            // Is @CallerSensitive, but is also defined in a non-sealed interface, and so this
            // check isn't really effective. The method is @Restricted, so assume that the
            // checks for restricted methods are sufficient.
            .callerCheck()
            .denyMethod("libraryLookup")

            .forPackage("java.lang.invoke")
            .allowAll()

            .forClass("MethodHandles.Lookup")
            .callerCheck()
            .denyAllMethods()
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
            .callerCheck()
            .denyMethod("resolve")
            .denyMethod("resolveAndBind")

            .forClass("ModuleFinder")
            .callerCheck()
            .denyAll()

            .forClass("ModuleReader")
            .callerCheck()
            .denyAll()

            .forClass("ModuleReference")
            .allowAll()
            .callerCheck()
            .denyMethod("open")

            .forPackage("java.lang.ref").allowAll()

            .forPackage("java.lang.reflect")
            .allowAll()

            .forClass("AccessibleObject")
            .callerCheck()
            .denyMethod("setAccessible")
            .denyMethod("trySetAccessible")

            .forClass("Constructor")
            .callerCheck()
            .denyMethod("setAccessible")

            .forClass("Field")
            .callerCheck()
            .denyMethod("setAccessible")

            .forClass("Method")
            .callerCheck()
            .denyMethod("setAccessible")

            .forClass("RecordComponent")
            .denyMethod("getAccessor")

            .forClass("Proxy")
            .denyMethod("getProxyClass") // deprecated

            .forPackage("java.lang.runtime").allowAll()

            .forPackage("java.math").allowAll()

            .forPackage("java.net")
            .allowAll()

            .forClass("Authenticator")
            .denyMethod("getDefault")
            .denyMethod("setDefault")
            .denyMethod("requestPasswordAuthentication")

            .forClass("CookieHandler")
            .denyMethod("getDefault")
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

            .forClass("ResponseCache")
            .denyAllConstructors()
            .denyMethod("getDefault")
            .denyMethod("setDefault")

            .forClass("ProxySelector")
            .denyAllConstructors()
            .denyMethod("getDefault")
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
            .denyMethod("findResource")
            .denyMethod("findResources")
            .denyMethod("getResourceAsStream")

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
            .denyMethod("open")

            .forClass("AsynchronousServerSocketChannel")
            .denyMethod("bind")

            .forClass("AsynchronousSocketChannel")
            .denyAllConstructors()
            .callerCheck()
            .denyMethod("bind")
            .denyMethod("connect")

            .forClass("DatagramChannel")
            .denyAllConstructors()
            .callerCheck()
            .denyMethod("bind")
            .denyMethod("connect")
            .denyMethod("receive")
            .denyMethod("send")

            .forClass("FileChannel")
            .denyMethod("open")

            .forClass("MulticastChannel")
            .callerCheck()
            .denyMethod("join")

            .forClass("NetworkChannel")
            .callerCheck()
            .denyMethod("bind")

            .forClass("ServerSocketChannel")
            .denyAllConstructors()
            .callerCheck()
            .denyMethod("accept")
            .denyMethod("bind")

            .forClass("SocketChannel")
            .denyAllConstructors()
            .denyMethod("open")
            .callerCheck()
            .denyMethod("bind")
            .denyMethod("connect")

            .forPackage("java.nio.channels.spi").denyAll()

            .forPackage("java.nio.charset").allowAll()

            .forPackage("java.nio.charset.spi").denyAll()

            .forPackage("java.nio.file")
            .allowAll()

            .forClass("Files")
            .callerCheck()
            .denyAll()

            .forClass("FileSystems")
            .callerCheck()
            .denyAll()

            .forClass("Path")
            .callerCheck()
            .denyMethod("of")
            .denyMethod("register")
            .denyMethod("toAbsolutePath")
            .denyMethod("toRealPath")
            .denyMethod("toUri")

            .forClass("Paths")
            .callerCheck()
            .denyAll()

            .forClass("SecureDirectoryStream")
            .callerCheck()
            .denyAll()

            .forClass("Watchable")
            .callerCheck()
            .denyMethod("register")

            .forPackage("java.nio.file.attribute")
            .allowAll()

            .forClass("AclFileAttributeView")
            .callerCheck()
            .denyAll()

            .forClass("BasicFileAttributeView")
            .callerCheck()
            .denyAll()

            .forClass("DosFileAttributeView")
            .callerCheck()
            .denyAll()

            .forClass("FileOwnerAttributeView")
            .callerCheck()
            .denyAll()

            .forClass("PosixFileAttributeView")
            .callerCheck()
            .denyAll()

            .forClass("UserDefinedFileAttributeView")
            .callerCheck()
            .denyAll()

            .forClass("UserPrincipalLookupService")
            .denyAll()

            .forPackage("java.nio.file.spi").denyAll()

            .forPackage("java.security")
            .allowAll()

            .forClass("AccessControlContext") // deprecated
            .denyAll()

            .forClass("AuthProvider")
            .callerCheck()
            .denyMethod("login")
            .denyMethod("logout")
            .denyMethod("setCallbackHandler")

            .forClass("Guard")
            .callerCheck()
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
            .denyMethod("defineClass")

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


            /* FIXME: Cannot call getCallerClass() from a @CallerSensitive method. Tagging these
                      methods using callerCheck() doesn't work because ResourceBundle can be
                      subclassed, providing access to the inherited static methods. Determine
                      if the existing checks are sufficient to deny access to resources from
                      other Modules or ClassLoaders.
            .forClass("ResourceBundle")
            .denyMethod("getBundle")
            .allowVariant("Ljava/lang.String;")
            .allowVariant("Ljava/lang.String;Ljava/util/Locale;")
            .allowVariant("Ljava/lang.String;Ljava/util/ResourceBundle$Control;")
            .allowVariant("Ljava/lang.String;Ljava/util/Locale;Ljava/util/ResourceBundle$Control;")
            */

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
            .callerCheck()
            .denyMethod("createServerSocket")

            .forClass("SocketFactory")
            .denyAllConstructors()
            .callerCheck()
            .denyMethod("createSocket")

            .forPackage("javax.net.ssl")
            .allowAll()

            .forClass("ExtendedSSLSession")
            .callerCheck()
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
            .callerCheck()
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
