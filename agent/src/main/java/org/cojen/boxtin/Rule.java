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

/**
 * 
 *
 * @author Brian S. O'Neill
 */
enum Rule implements Checker, Checker.ForClass {
    /** Operation is allowed. */
    ALLOW,

    /** Operation is denied, and the check is performed in the caller class. */
    CALLER_DENY,

    /** Operation is denied, and the check is performed in the target class. */
    TARGET_DENY;

    @Override
    public ForClass forClass(CharSequence packageName, CharSequence className) {
        return this;
    }

    @Override
    public ForClass forClass(Class<?> clazz) {
        return this;
    }

    @Override
    public boolean isAnyConstructorDeniable() {
        return this != ALLOW;
    }

    @Override
    public boolean isConstructorAllowed(CharSequence descriptor) {
        return this == ALLOW;
    }

    @Override
    public boolean isConstructorAllowed(Class<?>... paramTypes) {
        return this == ALLOW;
    }

    @Override
    public boolean isAnyMethodDeniable() {
        return this != ALLOW;
    }

    @Override
    public boolean isMethodAllowed(CharSequence name, CharSequence descriptor) {
        return this == ALLOW;
    }

    @Override
    public boolean isMethodAllowed(Class<?> returnType, CharSequence name, Class<?>... paramTypes) {
        return this == ALLOW;
    }

    @Override
    public boolean isCallerChecked() {
        return this == CALLER_DENY;
    }

    @Override
    public boolean isCallerConstructorChecked(CharSequence descriptor) {
        return this == CALLER_DENY;
    }

    @Override
    public boolean isCallerMethodChecked(CharSequence name, CharSequence descriptor) {
        return this == CALLER_DENY;
    }

    @Override
    public boolean isTargetChecked() {
        return this == TARGET_DENY;
    }

    @Override
    public boolean isTargetConstructorChecked(CharSequence descriptor) {
        return this == TARGET_DENY;
    }

    @Override
    public boolean isTargetMethodChecked(CharSequence name, CharSequence descriptor) {
        return this == TARGET_DENY;
    }
}
