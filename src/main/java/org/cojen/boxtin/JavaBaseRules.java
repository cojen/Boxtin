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
public final class JavaBaseRules {
    private JavaBaseRules() {
    }

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

        b.forPackage("java.lang.annotation").allowAll();

        b.forPackage("java.lang.classfile").allowAll();

        b.forPackage("java.lang.classfile.attribute").allowAll();

        b.forPackage("java.lang.classfile.constantpool").allowAll();

        b.forPackage("java.lang.classfile.instruction").allowAll();

        b.forPackage("java.lang.constant").allowAll();

        b.forPackage("java.lang.foreign").denyAll();

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

        b.forPackage("java.lang.module")
            .allowAll()

            .forClass("Configuration")
            .denyMethod("resolve")
            .denyMethod("resolveAndBind")
            .end()

            .forClass("ModuleFinder")
            .denyMethod("find")
            .denyMethod("findAll")
            .denyMethod("ofSystem")
            .end()

            .forClass("ModuleReader")
            .denyAll()
            .end()

            .forClass("ModuleReference")
            .allowAll()
            .denyMethod("open")
            .end()
            ;

        b.forPackage("java.lang.ref").allowAll();

        b.forPackage("java.lang.reflect")
            .allowAll()

            .forClass("AccessibleObject")
            .denyMethod("setAccessible")
            .denyMethod("trySetAccessible")
            .end()

            .forClass("Constructor")
            .denyMethod("setAccessible")
            .end()

            .forClass("Field")
            .denyMethod("setAccessible")
            .end()

            .forClass("Method")
            .denyMethod("setAccessible")
            .end()

            .forClass("Proxy")
            .denyMethod("getProxyClass") // deprecated
            .denyMethod("newProxyInstance")
            .end()
            ;

        b.forPackage("java.lang.runtime").allowAll();

        b.forPackage("java.math").allowAll();

        b.forPackage("java.net")
            .allowAll()

            .forClass("Authenticator")
            .denyMethod("getDefault")
            .denyMethod("setDefault")
            .denyMethod("requestPasswordAuthentication")
            .end()

            .forClass("CookieHandler")
            .denyMethod("getDefault")
            .denyMethod("setDefault")
            .end()

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
            .end()

            .forClass("HttpURLConnection")
            .denyMethod("setFollowRedirects")
            .denyMethod("setRequestMethod")
            .end()

            .forClass("InetAddress")
            .denyMethod("getByName")
            .denyMethod("getAllByName")
            .end()
 
            .forClass("InetSocketAddress")
            .denyAllConstructors()
            .allowVariant(int.class)
            .allowVariant("Ljava/net/InetAddress;I")
            .end()

            .forClass("MulticastSocket")
            .denyAllConstructors()
            .denyMethod("joinGroup")
            .denyMethod("leaveGroup")
            .denyMethod("send")
            .end()

            .forClass("ResponseCache")
            .denyAllConstructors()
            .denyMethod("getDefault")
            .denyMethod("setDefault")
            .end()

            .forClass("ProxySelector")
            .denyAllConstructors()
            .denyMethod("getDefault")
            .denyMethod("setDefault")
            .end()

            .forClass("ServerSocket")
            .denyAllConstructors()
            .denyMethod("accept")
            .denyMethod("bind")
            .denyMethod("getInetAddress")
            .denyMethod("getOption")
            .denyMethod("setOption")
            .denyMethod("setSocketFactory")
            .end()

            .forClass("Socket")
            .denyAllConstructors()
            .denyMethod("bind")
            .denyMethod("getLocalAddress")
            .denyMethod("getOption")
            .denyMethod("setOption")
            .denyMethod("setSocketImplFactory")
            .end()

            .forClass("URL")
            .denyAllConstructors()
            .denyMethod("of")
            .denyMethod("openConnection")
            .denyMethod("setURLStreamHandlerFactory")
            .end()

            .forClass("URLClassLoader")
            .denyAllConstructors()
            .end()

            .forClass("URLConnection")
            .denyMethod("setContentHandlerFactory")
            .denyMethod("setFileNameMap")
            .end()

            .forClass("URLStreamHandler")
            .denyMethod("setURL")
            .end()
            ;

        b.forPackage("java.net.spi").denyAll();

        b.forPackage("java.nio").allowAll();

        b.forPackage("java.nio.channels")
            .allowAll()

            .forClass("AsynchronousFileChannel")
            .denyMethod("open")
            .end()

            .forClass("AsynchronousServerSocketChannel")
            .denyMethod("bind")
            .end()

            .forClass("AsynchronousSocketChannel")
            .denyMethod("bind")
            .denyMethod("connect")
            .end()

            .forClass("DatagramChannel")
            .denyMethod("bind")
            .denyMethod("connect")
            .denyMethod("receive")
            .denyMethod("send")
            .end()

            .forClass("FileChannel")
            .denyMethod("open")
            .end()

            .forClass("MulticastChannel")
            .denyMethod("join")
            .end()

            .forClass("NetworkChannel")
            .denyMethod("bind")
            .end()

            .forClass("ServerSocketChannel")
            .denyMethod("accept")
            .denyMethod("bind")
            .end()

            .forClass("SocketChannel")
            .denyMethod("bind")
            .denyMethod("connect")
            .denyMethod("open")
            .end()
            ;

        b.forPackage("java.nio.channels.spi").denyAll();

        b.forPackage("java.nio.charset").allowAll();

        b.forPackage("java.nio.charset.spi").denyAll();

        b.forPackage("java.nio.file")
            .allowAll()

            .forClass("Files")
            .denyAll()
            .end()

            .forClass("FileSystems")
            .denyMethod("getFileSystem")
            .denyMethod("newFileSystem")
            .end()

            .forClass("Path")
            .denyMethod("of")
            .denyMethod("register")
            .denyMethod("toAbsolutePath")
            .denyMethod("toRealPath")
            .denyMethod("toUri")
            .end()

            .forClass("Paths")
            .denyMethod("get")
            .end()

            .forClass("SecureDirectoryStream")
            .denyMethod("deleteDirectory")
            .denyMethod("deleteFile")
            .denyMethod("move")
            .denyMethod("newByteChannel")
            .denyMethod("newDirectoryStream")
            .end()

            .forClass("Watchable")
            .denyMethod("register")
            .end()
            ;

        b.forPackage("java.nio.file.attribute")
            .allowAll()

            .forClass("AclFileAttributeView")
            .denyMethod("getAcl")
            .denyMethod("setAcl")
            .end()

            .forClass("BasicFileAttributeView")
            .denyMethod("readAttributes")
            .denyMethod("setTimes")
            .end()

            .forClass("DosFileAttributeView")
            .denyMethod("readAttributes")
            .denyMethod("setArchive")
            .denyMethod("setHidden")
            .denyMethod("setReadOnly")
            .denyMethod("setSystem")
            .end()

            .forClass("FileOwnerAttributeView")
            .denyMethod("getOwner")
            .denyMethod("setOwner")
            .end()

            .forClass("PosixFileAttributeView")
            .denyMethod("readAttributes")
            .denyMethod("setGroup")
            .denyMethod("setPermissions")
            .end()

            .forClass("UserDefinedFileAttributeView")
            .denyMethod("delete")
            .denyMethod("list")
            .denyMethod("read")
            .denyMethod("size")
            .denyMethod("write")
            .end()

            .forClass("UserPrincipalLookupService")
            .denyMethod("lookupPrincipalByName")
            .denyMethod("lookupPrincipalByGroupName")
            .end()
            ;

        b.forPackage("java.nio.file.spi").denyAll();

        b.forPackage("java.security")
            .allowAll()

            .forClass("AccessControlContext") // deprecated
            .denyAll()
            .end()

            .forClass("AuthProvider")
            .denyMethod("login")
            .denyMethod("logout")
            .denyMethod("setCallbackHandler")
            .end()

            .forClass("Guard")
            .denyMethod("checkGuard")
            .end()

            .forClass("GuardedObject")
            .denyMethod("getObject")
            .end()

            .forClass("Identity") // deprecated
            .denyAll()
            .end()

            .forClass("IdentityScope") // deprecated
            .denyAll()
            .end()

            .forClass("KeyStore")
            .denyMethod("getInstance")
            .end()

            .forClass("Permission")
            .denyMethod("checkGuard")
            .end()

            .forClass("Policy")
            .denyMethod("getInstance")
            .denyMethod("getPolicy")
            .denyMethod("setPolicy")
            .end()

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
            .end()

            .forClass("SecureClassLoader")
            .denyAllConstructors()
            .denyMethod("defineClass")
            .end()

            .forClass("Security")
            .denyMethod("addProvider")
            .denyMethod("getProperty")
            .denyMethod("insertProviderAt")
            .denyMethod("removeProvider")
            .denyMethod("setProperty")
            .end()

            .forClass("Signer") // deprecated
            .denyAll()
            .end()
            ;

        b.forPackage("java.security.cert").allowAll();

        b.forPackage("java.security.interfaces").allowAll();

        b.forPackage("java.security.spec").allowAll();

        b.forPackage("java.text").allowAll();

        b.forPackage("java.text.spi").allowAll();

        b.forPackage("java.time").allowAll();

        b.forPackage("java.time.chrono").allowAll();

        b.forPackage("java.time.format").allowAll();

        b.forPackage("java.time.temporal").allowAll();

        b.forPackage("java.time.zone").allowAll();

        b.forPackage("java.util")
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
            .end()

            .forClass("Locale")
            .denyMethod("setDefault")
            .end()

            .forClass("ResourceBundle")
            .allowMethod("getBundle")
            .denyVariant("Ljava/lang.String;Ljava/lang.Module;")
            .denyVariant("Ljava/lang.String;Ljava/util/Locale;Ljava/lang.Module;")
            .end()

            .forClass("ResourceBundle")
            .denyMethod("getBundle")
            .end()

            .forClass("TimeZone")
            .denyMethod("setDefault")
            .end()
            ;

        b.forPackage("java.util.concurrent")
            .allowAll()

            .forClass("ExecutorService")
            .denyMethod("shutdown")
            .denyMethod("shutdownNow")
            .end()

            .forClass("ForkJoinPool")
            .denyMethod("shutdown")
            .denyMethod("shutdownNow")
            .end()

            .forClass("ScheduledThreadPoolExecutor")
            .denyMethod("shutdown")
            .denyMethod("shutdownNow")
            .end()

            .forClass("ThreadPoolExecutor")
            .denyMethod("shutdown")
            .denyMethod("shutdownNow")
            .end()
            ;
 
        b.forPackage("java.util.concurrent.atomic").allowAll();

        b.forPackage("java.util.concurrent.locks").allowAll();

        b.forPackage("java.util.function").allowAll();

        b.forPackage("java.util.jar")
            .allowAll()

            .forClass("JarFile")
            .denyAllConstructors()
            .end()
            ;

        b.forPackage("java.util.random").allowAll();

        b.forPackage("java.util.regex").allowAll();

        b.forPackage("java.util.spi")
            .allowAll()

            .forClass("LocaleServiceProvider")
            .denyAllConstructors()
            .end()
            ;

        b.forPackage("java.util.stream").allowAll();

        b.forPackage("java.util.zip")
            .allowAll()

            .forClass("ZipFile")
            .denyAllConstructors()
            .end()
            ;

        b.forPackage("javax.crypto").allowAll();

        b.forPackage("javax.crypto.interfaces").allowAll();

        b.forPackage("javax.crypto.spec").allowAll();

        b.forPackage("javax.net")
            .allowAll()

            .forClass("ServerSocketFactory")
            .denyMethod("createServerSocket")
            .end()

            .forClass("SocketFactory")
            .denyMethod("createSocket")
            .end()
            ;

        b.forPackage("javax.net.ssl")
            .allowAll()

            .forClass("HttpsURLConnection")
            .denyMethod("setDefaultHostnameVerifier")
            .denyMethod("setDefaultSSLSocketFactory")
            .denyMethod("setSSLSocketFactory")
            .end()

            .forClass("SSLContext")
            .denyMethod("setDefault")
            .end()

            .forClass("SSLServerSocket")
            .denyAllConstructors()
            .end()

            .forClass("SSLSession")
            .denyMethod("getSessionContext")
            .end()

            .forClass("SSLSocket")
            .denyAllConstructors()
            .end()
            ;

        b.forPackage("javax.security.auth")
            .allowAll()

            .forClass("Subject")
            .denyMethod("doAs")
            .denyMethod("doAsPrivileged")
            .denyMethod("getSubject")
            .denyMethod("setReadOnly")
            .end()
            ;

        b.forPackage("javax.security.auth.callback").allowAll();

        b.forPackage("javax.security.auth.login")
            .allowAll()

            .forClass("Configuration")
            .denyMethod("getInstance")
            .end()
            ;

        b.forPackage("javax.security.auth.spi").allowAll();

        b.forPackage("javax.security.auth.x500").allowAll();
    }
}
