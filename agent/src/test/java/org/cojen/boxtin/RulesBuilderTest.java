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

import java.io.File;
import java.io.InputStream;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;

import java.net.URI;

import java.util.ArrayList;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class RulesBuilderTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(RulesBuilderTest.class.getName());
    }

    @Test
    public void allowAll() {
        Rules rules, rules2, rules3;

        {
            var b = new RulesBuilder();
            assertEquals(ModuleLayer.boot(), b.moduleLayer());
            rules = b.allowAll().build();
        }

        {
            var b = new RulesBuilder();
            rules2 = b.allowAll().build();
        }

        {
            var b = new RulesBuilder(ModuleLayer.empty());
            rules3 = b.allowAll().build();
        }

        assertEquals(rules.hashCode(), rules2.hashCode());
        assertEquals(rules, rules2);
        assertNotEquals(rules.hashCode(), rules3.hashCode());
        assertNotEquals(rules, rules3);

        assertEquals(ModuleLayer.boot(), rules.moduleLayer());
        assertEquals(ModuleLayer.empty(), rules3.moduleLayer());

        Rules.ForClass forClass = rules.forClass(String.class.getModule(), "java.lang", "Integer");
        assertFalse(forClass.isAnyDenied());
    }

    @Test
    public void moduleVersion() {
        var b = new RulesBuilder();

        try {
            b.forModule("xxx");
            fail();
        } catch (IllegalArgumentException e) {
        }
        
        try {
            b.forModule("java.base", "10000-", null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("too low"));
        }

        try {
            b.forModule("java.base", null, "1-x+y");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("too high"));
        }
    }

    @Test
    public void broken() {
        var b = new RulesBuilder();

        b.validate();

        b.forModule("java.base").forPackage("java.lang").forClass("Fake").allowAll();

        try {
            b.validate();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Fake"));
        }

        b.forModule("java.base").forPackage("java.lang").forClass("Fake2").allowAll();

        var messages = new ArrayList<String>();

        try {
            b.validate(msg -> messages.add(msg));
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Fake"));
        }

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("Fake"));
        assertTrue(messages.get(1).contains("Fake2"));

        try {
            b.forModule("java.base").forPackage("java.lang").forClass(String.class).denyMethod("<");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("method name"));
        }

        try {
            b.forModule("java.base").forPackage("java.util").forClass(String.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("package"));
        }

        try {
            b.forModule("java.base").forPackage("java.lang").forClass(String[].class);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unsupported"));
        }

        try {
            b.forModule("java.base").forPackage("java.lang").forClass(int.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unsupported"));
        }

        try {
            b.forModule("java.base").forPackage("xxx");
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            b.forModule("java.base").forPackage("java.lang").forClass("String").allowVariant("x");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("current"));
        }

        try {
            b.forModule("java.base").forPackage("java.lang").forClass("Long").denyVariant("x");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("current"));
        }

        try {
            b.forModule("java.base").forPackage("java.lang").forClass("Long")
                .denyVariant(DenyAction.standard(), "x");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("current"));
        }

        // This is actually okay.
        b.forModule("java.base").forPackage("java.lang").forClass("Long")
            .allowAllMethods().allowMethod("parseLong").denyVariant(String.class);
    }

    @Test
    public void validate() throws Exception {
        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.util").forClass("Map")
                .allowAllConstructors();
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Constructor"));
            }
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.util").forClass("HashMap")
                .denyAllConstructors().allowVariant(String.class);
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Constructor"));
                assertTrue(e.getMessage().contains("String"));
            }
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.util").forClass("HashMap")
                .denyAllMethods(DenyAction.standard()).denyMethod("abc");
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Method"));
                assertTrue(e.getMessage().contains("abc"));
            }
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.util").forClass("HashMap")
                .denyMethod("put").allowVariant(int.class);
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Method"));
                assertTrue(e.getMessage().contains("put"));
                assertTrue(e.getMessage().contains("I"));
            }
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").denyAll(DenyAction.exception(String.class));
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Not an exception"));
            }
        }

        {
            Class<?> exClass = java.nio.file.FileSystemException.class;
            var b = new RulesBuilder();
            b.forModule("java.base").denyAll(DenyAction.exception(exClass));
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("public no-arg"));
            }
        }

        {
            Class<?> exClass = java.text.ParseException.class;
            var b = new RulesBuilder();
            b.forModule("java.base").denyAll(DenyAction.exception(exClass, "hello"));
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("accepts just a message"));
            }
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").denyAll(DenyAction.exception("x"));
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("ClassNotFoundException"));
            }
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.lang").forClass("Class")
                .denyMethod(DenyAction.empty(), "getComponentType");
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("not supported"));
            }
        }

        {
            var lookup = MethodHandles.lookup();
            MethodHandleInfo mhi = lookup.revealDirect
                (lookup.findStatic(getClass(), "foo", MethodType.methodType(int.class)));
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.lang").forClass("Class")
                .denyMethod(DenyAction.custom(mhi), "getComponentType");
            try {
                b.validate();
                fail();
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("convert method return type"));
            }
        }

        {
            var lookup = MethodHandles.lookup();
            MethodHandleInfo mhi = lookup.revealDirect
                (lookup.findStatic(getClass(), "foo", MethodType.methodType(int.class)));
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.lang").forClass("Integer")
                .denyMethod(DenyAction.custom(mhi), "getInteger");
            // Boxing int to Integer should work.
            b.validate();
        }
    }

    public static int foo() {
        return 0;
    }

    @Test
    public void applyDenyRules() throws Exception {
        Module mod = getClass().getClassLoader().getUnnamedModule();

        {
            var b = new RulesBuilder().denyAll();
            b.applyDenyRules(RulesApplier.java_base());
            b.validate();
            Rules rules = b.build();
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertTrue(forClass.ruleForMethod("mkdir", "()").isDenied());
            assertTrue(forClass.ruleForMethod("getName", "()").isDenied());
        }

        {
            var b = new RulesBuilder().denyAll();
            b.forModule("java.base").forPackage("java.lang").allowAll();
            b.applyDenyRules(RulesApplier.java_base());
            b.validate();
            Rules rules = b.build();
            assertFalse(rules.forClass(mod, String.class).isAnyDenied());
            assertTrue(rules.forClass(mod, Boolean.class)
                       .ruleForMethod("getBoolean", String.class).isDenied());
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertTrue(forClass.ruleForMethod("mkdir", "()").isDenied());
            assertTrue(forClass.ruleForMethod("getName", "()").isDenied());
            forClass = rules.forClass(mod, ProcessBuilder.class);
            assertTrue(forClass.ruleForConstructor("()").isDenied());
        }

        {
            var b = new RulesBuilder().allowAll();
            b.forModule("java.base").forPackage("java.lang").denyAll();
            b.applyDenyRules(RulesApplier.java_base());
            b.validate();
            Rules rules = b.build();
            assertTrue(rules.forClass(mod, Boolean.class)
                       .ruleForMethod("getBoolean", String.class).isDenied());
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertTrue(forClass.ruleForMethod("mkdir", "()").isDenied());
            assertTrue(forClass.ruleForMethod("getName", "()").isAllowed());
        }
    }

    @Test
    public void applyAllowRules() throws Exception {
        Module mod = getClass().getClassLoader().getUnnamedModule();

        {
            var b = new RulesBuilder().allowAll();
            b.applyAllowRules(RulesApplier.java_base());
            b.validate();
            Rules rules = b.build();
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertTrue(forClass.ruleForMethod("mkdir", "()").isAllowed());
            assertTrue(forClass.ruleForMethod("getName", "()").isAllowed());
        }

        {
            var b = new RulesBuilder().allowAll();
            b.forModule("java.base").forPackage("java.io").denyAll();
            b.applyAllowRules(RulesApplier.java_base());
            b.validate();
            Rules rules = b.build();
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertTrue(forClass.ruleForMethod("mkdir", "()").isDenied());
            assertTrue(forClass.ruleForMethod("getName", "()").isAllowed());
            forClass = rules.forClass(mod, InputStream.class);
            assertFalse(forClass.isAnyDenied());
        }

        {
            var b = new RulesBuilder().denyAll();
            b.forModule("java.base").forPackage("java.io").allowAll();
            b.applyAllowRules(RulesApplier.java_base());
            b.validate();
            Rules rules = b.build();
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertTrue(forClass.ruleForMethod("mkdir", "()").isAllowed());
            assertTrue(forClass.ruleForMethod("getName", "()").isAllowed());
            forClass = rules.forClass(mod, ProcessBuilder.class);
            assertTrue(forClass.ruleForConstructor("()").isDenied());
        }
    }

    @Test
    public void isConstructionDenied() throws Exception {
        Module mod = getClass().getClassLoader().getUnnamedModule();

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.io").forClass("File")
                .allowAllConstructors()
                .denyVariant(String.class);
            b.validate();
            Rules rules = b.build();
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertFalse(forClass.isConstructionDenied());
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.io").forClass("File")
                .denyAllConstructors()
                .allowVariant(String.class);
            b.validate();
            Rules rules = b.build();
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertFalse(forClass.isConstructionDenied());
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.io").forClass("File")
                .allowAllConstructors()
                .denyVariant(File.class, String.class)
                .denyVariant(String.class)
                .denyVariant(String.class, String.class)
                .denyVariant(URI.class)
                ;
            b.validate();
            Rules rules = b.build();
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            assertFalse(forClass.isConstructionDenied());
        }

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.io").forClass("File")
                .denyAllConstructors()
                .denyVariant(DenyAction.empty(), File.class, String.class)
                ;
            b.validate();
            Rules rules = b.build();
            Rules.ForClass forClass = rules.forClass(mod, File.class);
            System.out.println("forClass: " + forClass);
            assertTrue(forClass.isConstructionDenied());
        }
    }

    @Test
    public void equalRulesTest() throws Exception {
        Rules r1, r2;

        {
            var b = new RulesBuilder();
            b.forModule("java.base").forPackage("java.io").forClass("File")
                .denyAllConstructors()
                .denyVariant(DenyAction.empty(), File.class, String.class)
                ;
            b.validate();
            r1 = b.build();
            r2 = b.build();
        }

        assertNotSame(r1, r2);
        int hash = r1.hashCode();
        assertEquals(hash, r1.hashCode());
        assertEquals(hash, r2.hashCode());
        assertEquals(r1, r2);
    }

    @Test
    public void sameModule() throws Exception {
        var b = new RulesBuilder();
        b.forModule("java.base").forPackage("java.io").forClass("File")
            .denyAllConstructors()
            .denyVariant(DenyAction.empty(), File.class, String.class)
            ;
        b.validate();
        Rules rules = b.build();

        assertFalse(rules.forClass(String.class.getModule(), File.class).isAnyDenied());
    }

    @Test
    public void qualifiedExport() throws Exception {
        Rules rules = new RulesBuilder().applyRules(RulesApplier.java_base()).build();

        Module thisMod = getClass().getModule();
        assertTrue(rules.forClass(thisMod, Integer.class).isAnyDenied());
        assertEquals(Rule.deny(), rules.forClass(thisMod, "jdk/internal/misc", "Unsafe"));

        Module unnamedMod = getClass().getClassLoader().getUnnamedModule();
        assertEquals(Rule.deny(), rules.forClass(unnamedMod, "jdk/internal/misc", "Unsafe"));

        Module desktopMod = java.awt.Component.class.getModule();
        assertEquals(Rule.allow(), rules.forClass(desktopMod, "jdk/internal/misc", "Unsafe"));
    }
}
