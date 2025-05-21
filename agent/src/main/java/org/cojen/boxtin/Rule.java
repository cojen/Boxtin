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

    public static Rule denyAtCaller() {
        return AtCaller.STANDARD;
    }

    public static Rule denyAtCaller(DenyAction action) {
        if (action == DenyAction.standard()) {
            return AtCaller.STANDARD;
        } else if (action == DenyAction.empty()) {
            return AtCaller.EMPTY;
        } else {
            return new AtCaller(Objects.requireNonNull(action));
        }
    }

    public static Rule denyAtTarget() {
        return AtTarget.STANDARD;
    }

    public static Rule denyAtTarget(DenyAction action) {
        if (action == DenyAction.standard()) {
            return AtTarget.STANDARD;
        } else if (action == DenyAction.empty()) {
            return AtTarget.EMPTY;
        } else {
            return new AtTarget(Objects.requireNonNull(action));
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

    public boolean isDeniedAtCaller() {
        return false;
    }

    public boolean isDeniedAtTarget() {
        return false;
    }

    /**
     * @return null if not denied
     */
    public DenyAction denyAction() {
        return null;
    }

    /**
     * @throws IllegalStateException if this rule isn't denied
     */
    public Rule withDenyAction(DenyAction action) {
        throw new IllegalStateException();
    }

    @Override
    public boolean isAllAllowed() {
        return isAllowed();
    }

    @Override
    public ForClass forClass(CharSequence packageName, CharSequence className) {
        return this;
    }

    @Override
    public ForClass forClass(Class<?> clazz) {
        return this;
    }

    public boolean printTo(Appendable a, String indent, String plusIndent) throws IOException {
        a.append(indent).append(toString());
        return true;
    }

    @Override
    public Rule ruleForConstructor(CharSequence descriptor) {
        return this;
    }

    @Override
    public Rule ruleForConstructor(Class<?>... paramTypes) {
        return this;
    }

    @Override
    public Rule ruleForMethod(CharSequence name, CharSequence descriptor) {
        return this;
    }

    @Override
    public Rule ruleForMethod(Class<?> returnType, CharSequence name, Class<?>... paramTypes) {
        return this;
    }

    @Override
    public boolean isAnyConstructorDenied() {
        return isDenied();
    }

    @Override
    public boolean isAnyDeniedAtCaller() {
        return isDeniedAtCaller();
    }

    public boolean isAnyDeniedAtTarget() {
        return isDeniedAtTarget();
    }

    @Override
    public int hashCode() {
        return 524764839;
    }

    @Override
    public String toString() {
        return "allow";
    }

    private static String denyString(String which, DenyAction action) {
        String deny = " deny";
        if (action == DenyAction.standard()) {
            return which + deny;
        } else {
            return which + deny + " action " + action;
        }
    }

    private static final class AtCaller extends Rule {
        private static final AtCaller STANDARD = new AtCaller(DenyAction.standard());
        private static final AtCaller EMPTY = new AtCaller(DenyAction.empty());

        private final DenyAction action;

        private AtCaller(DenyAction action) {
            this.action = action;
        }

        @Override
        public boolean isDenied() {
            return true;
        }

        @Override
        public boolean isDeniedAtCaller() {
            return true;
        }

        @Override
        public boolean isDeniedAtTarget() {
            return false;
        }

        @Override
        public DenyAction denyAction() {
            return action;
        }

        @Override
        public Rule withDenyAction(DenyAction action) {
            return this.action.equals(action) ? this : denyAtCaller(action);
        }

        @Override
        public int hashCode() {
            return -655008625 ^ action.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AtCaller other && action.equals(other.action);
        }

        @Override
        public String toString() {
            return denyString("caller", action);
        }
    }

    private static final class AtTarget extends Rule {
        private static final AtTarget STANDARD = new AtTarget(DenyAction.standard());
        private static final AtTarget EMPTY = new AtTarget(DenyAction.empty());

        private final DenyAction action;

        private AtTarget(DenyAction action) {
            this.action = action;
        }

        @Override
        public boolean isDenied() {
            return true;
        }

        @Override
        public boolean isDeniedAtCaller() {
            return false;
        }

        @Override
        public boolean isDeniedAtTarget() {
            return true;
        }

        @Override
        public DenyAction denyAction() {
            return action;
        }

        @Override
        public Rule withDenyAction(DenyAction action) {
            return this.action.equals(action) ? this : denyAtTarget(action);
        }

        @Override
        public int hashCode() {
            return 810018264 ^ action.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AtTarget other && action.equals(other.action);
        }

        @Override
        public String toString() {
            return denyString("target", action);
        }
    }
}
