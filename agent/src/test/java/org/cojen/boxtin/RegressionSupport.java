/*
 *  Copyright 2026 Cojen.org
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

import java.io.IOException;
import java.io.InputStream;

import java.net.JarURLConnection;
import java.net.URL;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Shared helpers for the regression tests which transform a class with a custom set of rules,
 * and which run code in a child JVM.
 *
 * @author Claude
 */
final class RegressionSupport {
    private RegressionSupport() {
    }

    static final class Loader extends ClassLoader {
        Loader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    static byte[] classBytes(Class<?> clazz) throws IOException {
        String path = clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }

    /**
     * Builds rules which allow everything in java.base, except for what the given consumer
     * denies. The caller classes are loaded into an unnamed module, and so everything in
     * java.base is a "foreign" target for them.
     */
    static Rules rules(Consumer<RulesBuilder> denials) {
        var b = new RulesBuilder();
        b.forModule("java.base").allowAll();
        denials.accept(b);
        return b.build();
    }

    /**
     * Transforms a copy of the given class using the given rules, defines it in a fresh
     * ClassLoader, and then links it (which runs the bytecode verifier). The returned class
     * is a different class than the original.
     *
     * @throws LinkageError if the transformed class is malformed or fails verification
     */
    static Class<?> transformAndLink(Class<?> original, Rules rules) throws Throwable {
        SecurityAgent agent = SecurityAgent.testActivate(module -> rules);
        try {
            var loader = new Loader(original.getClassLoader());
            byte[] bytes = classBytes(original);
            byte[] xbytes = agent.transform
                (loader.getUnnamedModule(), original.getName().replace('.', '/'), bytes);
            Class<?> clazz = loader.define(original.getName(), xbytes == null ? bytes : xbytes);
            // Initialization forces linking, and so verification.
            Class.forName(clazz.getName(), true, loader);
            return clazz;
        } finally {
            SecurityAgent.testActivate(null);
        }
    }

    record ChildResult(int exitCode, String output) {
    }

    /**
     * Runs a main method in a child JVM, using a class path built from the locations of the
     * given classes. This is needed for scenarios which would kill the JVM, or which require
     * special JVM options.
     *
     * @param jvmArgs extra JVM arguments, placed before the class path
     * @param programArgs arguments passed to the main method
     * @param classPathOf classes whose class path roots are included in the class path
     */
    static ChildResult runChild(List<String> jvmArgs, List<String> programArgs,
                                Class<?> mainClass, Class<?>... classPathOf)
        throws Exception
    {
        var cmd = new ArrayList<String>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(jvmArgs);

        Set<String> roots = new LinkedHashSet<>();
        for (Class<?> c : classPathOf) {
            roots.add(rootOf(c));
        }

        cmd.add("-cp");
        cmd.add(String.join(java.io.File.pathSeparator, roots));
        cmd.add(mainClass.getName());
        cmd.addAll(programArgs);

        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output;
        try (InputStream in = p.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("Child JVM timed out: " + output);
        }
        return new ChildResult(p.exitValue(), output);
    }

    /**
     * Returns the class path root (directory or jar file) which provides the given class.
     */
    static String rootOf(Class<?> clazz) throws Exception {
        String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        URL url = clazz.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Cannot locate: " + clazz);
        }
        if (url.getProtocol().equals("jar")) {
            var conn = (JarURLConnection) url.openConnection();
            return Path.of(conn.getJarFileURL().toURI()).toString();
        }
        Path path = Path.of(url.toURI());
        // Strip off the package directories and the class file itself.
        for (int i = clazz.getName().split("\\.").length; --i >= 0; ) {
            path = path.getParent();
        }
        return path.toString();
    }
}
