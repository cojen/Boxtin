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
 * Defines a set of rules to deny operations in the java.base module which could be considered
 * harmful.
 *
 * @author Brian S. O'Neill
 * @see RulesApplier#java_base
 */
final class JavaBaseApplier implements RulesApplier {
    @Override
    public void applyRulesTo(RulesBuilder b) {
        b.forModule("java.base")

            .forPackage("java.io")
            .allowAll()

            .forClass("File")
            .denyAllMethods()
            .allowMethod("compareTo")
            .allowMethod("getName")
            .allowMethod("getParent")
            .allowMethod("getParentFile")
            .allowMethod("getPath")
            .allowMethod("toPath")

            .forClass("FileInputStream")
            .denyAllConstructors()

            .forClass("FileOutputStream")
            .denyAllConstructors()

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
            .denyAllConstructors()
            .allowVariant("Ljava/io/OutputStream;")
            .allowVariant("Ljava/io/OutputStream;Z")
            .allowVariant("Ljava/io/OutputStream;ZLjava/nio/charset/Charset;")

            .forClass("PrintWriter")
            .denyAllConstructors()
            .allowVariant("Ljava/io/OutputStream;")
            .allowVariant("Ljava/io/OutputStream;Z")
            .allowVariant("Ljava/io/OutputStream;ZLjava/nio/charset/Charset;")
            .allowVariant("Ljava/io/Writer;")
            .allowVariant("Ljava/io/Writer;Z")

            .forClass("RandomAccessFile")
            .denyAllConstructors()

            .forPackage("java.lang")
            .allowAll()

            .forClass("Boolean")
            .denyMethod("getBoolean")

            // Note: The methods which return Constructors and Methods are treated specially,
            // and denying access here only denies access to those methods themselves. Checks
            // are put in place for when callers obtain members, ensuring that access is
            // allowed to the underlying class member as if it was called directly.
            .forClass("Class")
            .callerCheck()
            .denyMethod("forName")
            .allowVariant(String.class)
            .denyMethod("getClassLoader")
            .denyMethod("getClasses")
            .denyMethod("getConstructor")
            .denyMethod("getConstructors")
            .denyMethod("getDeclaredClasses")
            .denyMethod("getDeclaredConstructor")
            .denyMethod("getDeclaredConstructors")
            .denyMethod("getDeclaredMethod")
            .denyMethod("getDeclaredMethods")
            .denyMethod("getDeclaringClass")
            .denyMethod("getEnclosingClass")
            .denyMethod("getEnclosingConstructor")
            .denyMethod("getEnclosingMethod")
            .denyMethod("getMethod")
            .denyMethod("getMethods")
            .denyMethod("getNestHost")
            .denyMethod("getNestMembers")
            .denyMethod("getPermittedSubclasses")
            .denyMethod("getProtectionDomain")
            .denyMethod("getRecordComponents")
            .denyMethod("newInstance") // deprecated

            .forClass("ClassLoader")
            .denyAllConstructors()
            .denyMethod("getParent")
            .denyMethod("getPlatformClassLoader")
            .denyMethod("getSystemClassLoader")
            .denyMethod("getSystemResource")
            .denyMethod("getSystemResourceAsStream")
            .denyMethod("getSystemResources")
            .denyMethod("setClassAssertionStatus")
            .denyMethod("setDefaultAssertionStatus")
            .denyMethod("setPackageAssertionStatus")
            .denyMethod("clearAssertionStatus")

            .forClass("Integer")
            .denyMethod("getInteger")

            .forClass("Long")
            .denyMethod("getLong")

            .forClass("Module")
            .callerCheck()
            .denyMethod("getClassLoader")


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

            .forClass("SecurityManager")
            .denyAll()

            .forClass("StackWalker")
            .denyMethod("getInstance")
            .allowVariant() // no args

            .forClass("System")
            .callerCheck()
            .denyAll()
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
            .denyMethod("daemon")
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

            .forClass("MethodHandles")
            .denyMethod("privateLookupIn")
            .denyMethod("reflectAs")

            .forClass("MethodHandles.Lookup")
            .callerCheck()
            .denyAllMethods()
            .allowMethod("dropLookupMode")
            .allowMethod("hasFullPrivilegeAccess")
            .allowMethod("lookupClass")
            .allowMethod("lookupModes")

            .forClass("MethodType")
            .denyMethod("fromMethodDescriptorString")

            .forPackage("java.lang.module")
            .allowAll()

            .forClass("Configuration")
            .denyMethod("resolve")
            .denyMethod("resolveAndBind")

            .forClass("ModuleFinder")
            .callerCheck()
            .denyMethod("find")
            .denyMethod("findAll")
            .denyMethod("ofSystem")

            .forClass("ModuleReader")
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
            .denyMethod("newProxyInstance")

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
            .denyMethod("getInetAddress")
            .denyMethod("getOption")
            .denyMethod("setOption")
            .denyMethod("setSocketFactory")

            .forClass("Socket")
            .denyAllConstructors()
            .denyMethod("bind")
            .denyMethod("getLocalAddress")
            .denyMethod("getOption")
            .denyMethod("setOption")
            .denyMethod("setSocketImplFactory")

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
            .denyAll()

            .forClass("FileSystems")
            .denyMethod("getFileSystem")
            .denyMethod("newFileSystem")

            .forClass("Path")
            .callerCheck()
            .denyMethod("of")
            .denyMethod("register")
            .denyMethod("toAbsolutePath")
            .denyMethod("toRealPath")
            .denyMethod("toUri")

            .forClass("Paths")
            .denyMethod("get")

            .forClass("SecureDirectoryStream")
            .callerCheck()
            .denyMethod("deleteDirectory")
            .denyMethod("deleteFile")
            .denyMethod("move")
            .denyMethod("newByteChannel")
            .denyMethod("newDirectoryStream")

            .forClass("Watchable")
            .callerCheck()
            .denyMethod("register")

            .forPackage("java.nio.file.attribute")
            .allowAll()

            .forClass("AclFileAttributeView")
            .callerCheck()
            .denyMethod("getAcl")
            .denyMethod("getOwner")
            .denyMethod("setAcl")
            .denyMethod("setOwner")

            .forClass("BasicFileAttributeView")
            .callerCheck()
            .denyMethod("readAttributes")
            .denyMethod("setTimes")

            .forClass("DosFileAttributeView")
            .callerCheck()
            .denyMethod("readAttributes")
            .denyMethod("setArchive")
            .denyMethod("setHidden")
            .denyMethod("setReadOnly")
            .denyMethod("setSystem")
            .denyMethod("setTimes")

            .forClass("FileOwnerAttributeView")
            .callerCheck()
            .denyMethod("getOwner")
            .denyMethod("setOwner")

            .forClass("PosixFileAttributeView")
            .callerCheck()
            .denyMethod("getOwner")
            .denyMethod("readAttributes")
            .denyMethod("setGroup")
            .denyMethod("setOwner")
            .denyMethod("setPermissions")
            .denyMethod("setTimes")

            .forClass("UserDefinedFileAttributeView")
            .callerCheck()
            .denyMethod("delete")
            .denyMethod("list")
            .denyMethod("read")
            .denyMethod("size")
            .denyMethod("write")

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
            // ForkJoinTask.getPool() method.
            .denyMethod("close")
            .denyMethod("shutdown")
            .denyMethod("shutdownNow")

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
