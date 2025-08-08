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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Brian S. O'Neill
 */
final class PackageToModule {
    private static final SoftCache<ModuleLayer, Map<String, Module>> cCache = new SoftCache<>();

    /**
     * Returns a map of package names (with '/' separators) to Modules, for all packages of all
     * named modules provided by the given ModuleLayer.
     */
    static Map<String, Module> packageMapFor(ModuleLayer layer) {
        Map<String, Module> map = cCache.get(layer);

        if (map == null) {
            synchronized (cCache) {
                map = cCache.get(layer);
                if (map == null) {
                    map = gatherPackages(layer);
                    cCache.put(layer, map);
                }
            }
        }

        return map;
    }

    private static Map<String, Module> gatherPackages(ModuleLayer layer) {
        var map = new HashMap<String, Module>();

        for (Module module : layer.modules()) {
            if (module.isNamed()) {
                for (String pname : module.getPackages()) {
                    map.put(pname.replace('.', '/').intern(), module);
                }
            }
        }

        layer.parents().forEach(parentLayer -> map.putAll(packageMapFor(parentLayer)));

        return map.isEmpty() ? Map.of() : map;
    }
}
