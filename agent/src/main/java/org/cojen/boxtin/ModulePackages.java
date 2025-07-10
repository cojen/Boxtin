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

import java.util.HashSet;
import java.util.Set;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class ModulePackages {
    private static final SoftCache<Module, Set<String>> cCache = new SoftCache<>();

    private ModulePackages() {
    }

    /**
     * Returns true if the given package is defined by a module, as discoverable by the given
     * caller.
     *
     * @param packageName package name must have '/' characters as separators
     */
    static boolean isPackageModular(Module caller, CharSequence packageName) {
        Set<String> packages = cCache.get(caller);
        if (packages == null) {
            packages = findPackages(caller, packageName);
        }
        return packages.contains(packageName);
    }

    private static Set<String> findPackages(Module caller, CharSequence packageName) {
        synchronized (caller) {
            Set<String> packages = cCache.get(caller);

            if (packages == null) {
                ModuleLayer layer = caller.getLayer();
                if (layer == null) {
                    layer = ModuleLayer.boot();
                }

                packages = new HashSet<>();

                for (Module mod : layer.modules()) {
                    if (mod.isNamed()) {
                        for (String pname : mod.getPackages()) {
                            packages.add(pname.replace('.', '/').intern());
                        }
                    }
                }

                cCache.put(caller, packages);
            }

            return packages;
        }
    }
}
