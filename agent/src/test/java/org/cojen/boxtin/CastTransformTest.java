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

import java.security.Provider;
import java.security.SecureRandom;

import java.util.HashMap;
import java.util.Map;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public class CastTransformTest extends TransformTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(CastTransformTest.class.getName());
    }

    @Override
    protected RulesBuilder builder() {
        return new RulesBuilder().applyRules(RulesApplier.java_base());
    }

    @Test
    public void cast() throws Throwable {
        if (runTransformed(SubProvider.class)) {
            return;
        }

        // Casting to a supertype shouldn't eliminate a security check defined in a subtype.

        // Map operations should be fine here.
        {
            var hashMap = new HashMap<Object, Object>();
            hashMap.put("a", "b");
            var map = (Map<Object, Object>) hashMap;
            map.put("a", "b");
        }

        var provider = new SecureRandom().getProvider();
        
        try {
            provider.put("a", "b");
            fail();
        } catch (SecurityException e) {
        }

        try {
            var map = (Map<Object, Object>) provider;
            map.put("hello", "world");
            fail();
        } catch (SecurityException e) {
        }

        try {
            new SubProvider().doPut("a", "b");
            fail();
        } catch (SecurityException e) {
        }
    }

    static class SubProvider extends Provider {
        SubProvider() {
            super("name", "version", "info");
        }

        void doPut(String key, String value) {
            super.put(key, value);
        }
    }
}
