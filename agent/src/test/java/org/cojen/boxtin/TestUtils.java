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

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;

import java.net.URI;

import java.nio.ByteBuffer;

import java.util.Optional;
import java.util.Set;

import java.util.stream.Stream;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.Label;
import org.cojen.maker.MethodMaker;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class TestUtils {
    private static int mModuleNum;

    private static synchronized String newModuleName() {
        return "org.cojen.boxtin.Test_" + ++mModuleNum;
    }

    public static MethodHandles.Lookup newModule(Class<?> parent) throws Exception {
        return newModule(parent.getClassLoader(), parent.getModule());
    }

    public static MethodHandles.Lookup newModule(ClassLoader parentLoader, Module... dependencies)
        throws Exception
    {
        String moduleName = newModuleName();
        return newModule(parentLoader, moduleName, "org.cojen.boxtin.tt", dependencies);
    }

    public static MethodHandles.Lookup newModule(ClassLoader parentLoader,
                                                 String moduleName, String pkgName,
                                                 Module... dependencies)
        throws Exception
    {
        System.out.println("dependencies: " + java.util.Arrays.toString(dependencies));

        // There's got to be an easier way to do this.

        String className = pkgName + ".Bootstrap";

        byte[] classBytes;
        {
            ClassMaker cm = ClassMaker.beginExternal(className).public_();
            MethodMaker mm = cm.addMethod(MethodHandles.Lookup.class, "boot", Module[].class)
                .public_().static_();

            var depsVar = mm.param(0);

            var modVar = mm.class_().invoke("getModule");

            {
                var i = mm.var(int.class).set(0);
                Label start = mm.label().here();
                Label end = mm.label();
                i.ifGe(depsVar.alength(), end);
                modVar.invoke("addReads", depsVar.aget(i));
                i.inc(1);
                mm.goto_(start);
                end.here();
            }

            mm.return_(mm.var(MethodHandles.class).invoke("lookup"));
            classBytes = cm.finishBytes();
        }

        ModuleDescriptor desc = ModuleDescriptor.newModule(moduleName).exports(pkgName).build();

        var finder = new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                if (!name.equals(moduleName)) {
                    return Optional.empty();
                }

                return Optional.of(new ModuleReference(desc, null) {
                    @Override
                    public ModuleReader open() {
                        return new ModuleReader() {
                            @Override
                            public Optional<ByteBuffer> read(String name) {
                                if (name.equals(className.replace('.', '/') + ".class")) {
                                    return Optional.of(ByteBuffer.wrap(classBytes));
                                }
                                return Optional.empty();
                            }

                            @Override
                            public void close() {
                            }

                            public Optional<URI> find(String name) {
                                return Optional.empty();
                            }

                            public Stream<String> list() {
                                return Stream.empty();
                            }
                        };
                    }
                });
            }

            @Override
            public Set<ModuleReference> findAll() {
                return Set.of(find(moduleName).get());
            }
        };

        ModuleLayer boot = ModuleLayer.boot();
        Configuration config = boot.configuration()
            .resolve(finder, ModuleFinder.of(), Set.of(moduleName));
        ModuleLayer layer = boot.defineModulesWithOneLoader(config, parentLoader);

        Class<?> bootClass = layer.findLoader(moduleName).loadClass(className);

        return (MethodHandles.Lookup) bootClass
            .getMethod("boot", Module[].class).invoke(null, (Object) dependencies);
    }
}
