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

import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

/**
 * Caches filtered sets of properties, keyed by caller module. Each module has a distinct set,
 * such that it can be freely modified without affecting the sets observed by other modules.
 *
 * @author Brian S. O'Neill
 * @see CustomActions#getProperties
 */
final class FilteredProperties {
    private static final WeakHashMap<Module, Properties> cCache = new WeakHashMap<>();

    static synchronized Properties getProperties(Module caller) {
        Properties props = cCache.get(caller);
        if (props != null) {
            return props;
        }

        props = new Properties();

        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            Object key = e.getKey();
            if (key instanceof String name && checkGetProperty(name)) {
                props.put(name, e.getValue());
            }
        }

        Properties existing = cCache.putIfAbsent(caller, props);

        return existing == null ? props : existing;
    }

    static String getProperty(Module caller, String name) {
        return getProperties(caller).getProperty(name);
    }

    static String getProperty(Module caller, String name, String def) {
        return getProperties(caller).getProperty(name, def);
    }

    static synchronized void setProperties(Module caller, Properties props) {
        if (props == null) {
            cCache.remove(caller);
        } else {
            cCache.put(caller, props);
        }
    }

    static String setProperty(Module caller, String name, String value) {
        Object old = getProperties(caller).setProperty(name, value);
        return old instanceof String s ? s : null;
    }

    static String clearProperty(Module caller, String name) {
        Object old = getProperties(caller).remove(name);
        return old instanceof String s ? s : null;
    }

    private static boolean checkGetProperty(String name) {
        return switch (name) {
            default -> false;

            case "java.version", "java.version.date",
                "java.vendor", "java.vendor.url", "java.vendor.version",
                "java.vm.specification.version", "java.vm.specification.vendor",
                "java.vm.specification.name", "java.vm.version", "java.vm.vendor", "java.vm.name",
                "java.specification.version", "java.specification.maintenance.version",
                "java.specification.vendor", "java.specification.name",
                "java.class.version",
                "os.name", "os.arch", "os.version",
                "file.separator", "path.separator", "line.separator",
                "native.encoding", "stdout.encoding", "stderr.encoding"
                -> true;
        };
    }

    private FilteredProperties() {
    }
}
