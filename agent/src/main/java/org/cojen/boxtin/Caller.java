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

import java.util.Objects;

/**
 * Wraps a caller class, to be passed to a {@link DenyAction#custom custom} or {@link
 * DenyAction#check checked} deny action method.
 *
 * @author Brian S. O'Neill
 */
public final class Caller {
    /* 
       Design note: Passing the caller Class directly is simpler, but any Class could be passed
       in, easily bypassing an access check. Direct access to the custom or checked deny action
       methods themselves can be denied, but this is a detail which might be easily overlooked.
     */

    private final Class<?> mCallerClass;

    Caller(Class<?> callerClass) {
        mCallerClass = Objects.requireNonNull(callerClass);
    }

    /**
     * Custom and checked deny actions must invoke this method to validate this instance and to
     * obtain the actual caller class.
     *
     * @return a non-null caller class
     */
    public Class<?> validate() {
        // Note that nothing needs to be done. Requiring that the custom action invokes this
        // method simply ensures that the Caller instance isn't null.
        return mCallerClass;
    }
}
