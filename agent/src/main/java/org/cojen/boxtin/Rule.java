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

import java.util.Map;
import java.util.Objects;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed class Rule implements Rules, Rules.ForClass {
    private static final Rule ALLOWED = new Rule();

    public static Rule allow() {
        return ALLOWED;
    }

    public static Rule deny() {
        return Denied.STANDARD;
    }

    public static Rule deny(DenyAction action) {
        if (action == DenyAction.standard()) {
            return Denied.STANDARD;
        } else if (action == DenyAction.empty()) {
            return Denied.EMPTY;
        } else {
            return new Denied(Objects.requireNonNull(action));
        }
    }

    private Rule() {
    }

    public final boolean isAllowed() {
        return !isDenied();
    }

    public boolean isDenied() {
        return false;
    }

    /**
     * @return null if not denied
     */
    public DenyAction denyAction() {
        return null;
    }

    /**
     * Same as {@link #isAllowed}.
     */
    @Override
    public final boolean isAllAllowed() {
        return isAllowed();
    }

    /**
     * Returns {@code this}.
     */
    @Override
    public final ForClass forClass(CharSequence packageName, CharSequence className) {
        return this;
    }

    /**
     * Returns {@code this}.
     */
    @Override
    public final ForClass forClass(Class<?> clazz) {
        return this;
    }

    @Override
    public Map<String, Rule> denialsForMethod(CharSequence name, CharSequence descriptor) {
        return Map.of();
    }

    public final boolean printTo(Appendable a, String indent, String plusIndent)
        throws IOException
    {
        a.append(indent).append(toString());
        return true;
    }

    /**
     * Returns {@code this}.
     */
    @Override
    public final Rule ruleForConstructor(CharSequence descriptor) {
        return this;
    }

    /**
     * Returns {@code this}.
     */
    @Override
    public final Rule ruleForConstructor(Class<?>... paramTypes) {
        return this;
    }

    /**
     * Returns {@code this}.
     */
    @Override
    public final Rule ruleForMethod(CharSequence name, CharSequence descriptor) {
        return this;
    }

    /**
     * Returns {@code this}.
     */
    @Override
    public final Rule ruleForMethod(CharSequence name, Class<?>... paramTypes) {
        return this;
    }

    @Override
    public int hashCode() {
        return 524764839;
    }

    @Override
    public String toString() {
        return "allow";
    }

    private static final class Denied extends Rule {
        private static final Denied STANDARD = new Denied(DenyAction.standard());
        private static final Denied EMPTY = new Denied(DenyAction.empty());

        private final DenyAction action;

        private Denied(DenyAction action) {
            this.action = action;
        }

        @Override
        public boolean isDenied() {
            return true;
        }

        @Override
        public DenyAction denyAction() {
            return action;
        }

        @Override
        public int hashCode() {
            return -655008625 ^ action.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Denied other && action.equals(other.action);
        }

        @Override
        public String toString() {
            String deny = "deny";
            return action == DenyAction.standard() ? deny : (deny + " action " + action);
        }
    }
}
