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

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Checks if access to a class member is allowed or denied.
 *
 * @author Brian S. O'Neill
 */
public interface Checker {
    public boolean isConstructorAllowed(MemberRef ctorRef);

    public boolean isMethodAllowed(MemberRef methodRef);

    public boolean isVirtualMethodAllowed(MemberRef methodRef);

    public boolean isFieldAllowed(MemberRef fieldRef);
}
