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

import java.lang.reflect.RecordComponent;

import org.junit.*;
import static org.junit.Assert.*;

import org.cojen.maker.ClassMaker;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class RecordReflectionTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(RecordReflectionTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() throws Exception {
        MethodHandles.Lookup modLookup = TestUtils.newModule(getClass());

        System.getProperties().put(getClass().getName(), modLookup);

        Class<?> modClass = modLookup.lookupClass();
        Module mod = modClass.getModule();

        RulesBuilder b = new RulesBuilder(mod.getLayer()).applyRules(RulesApplier.java_base());
        b.forModule("java.base").forPackage("java.lang").forClass("System").allowAll();

        b.forModule(mod.getName()).denyAll();

        return b;
    }

    @Test
    public void getRecordComponents() throws Exception {
        if (runTransformed()) {
            return;
        }

        var modLookup = (MethodHandles.Lookup) System.getProperties().remove(getClass().getName());
        Class<?> modClass = modLookup.lookupClass();

        ClassMaker cm = ClassMaker.begin(modClass.getName(), modLookup).public_();

        cm.addField(String.class, "a");
        cm.addField(int.class, "b");

        cm.asRecord();

        Class<?> clazz = cm.finish();

        RecordComponent[] components = clazz.getRecordComponents();

        for (RecordComponent rc : components) {
            assertNotEquals("b", rc.getName());
        }
    }
}
