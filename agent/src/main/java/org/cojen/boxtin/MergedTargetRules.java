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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

/**
 * Defines a merged set of rules suitable for applying target class transformations.
 *
 * @author Brian S. O'Neill
 */
final class MergedTargetRules implements Rules {
    static Rules from(Controller controller) {
        return from(controller.allRules());
    }

    static Rules from(Set<Rules> allRules) {
        int size;
        if (allRules == null || (size = allRules.size()) == 0) {
            return Rule.allow();
        }

        Iterator<Rules> it = allRules.iterator();

        if (size == 1 && it.hasNext()) {
            return it.next();
        }

        var list = new ArrayList<Rules>(size);

        while (it.hasNext()) {
            Rules rules = it.next();
            if (rules != null) {
                list.add(rules);
            }
        }

        size = list.size();

        if (size <= 1) {
            return size == 0 ? Rule.allow() : list.get(0);
        }

        return new MergedTargetRules(list.toArray(Rules[]::new));
    }

    private final Rules[] mSources;

    /**
     * @param sources must not contain any nulls
     */
    private MergedTargetRules(Rules... sources) {
        mSources = sources;
    }

    @Override
    public boolean isAllAllowed() {
        for (Rules rules : mSources) {
            if (!rules.isAllAllowed()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ForClass forClass(CharSequence packageName, CharSequence className) {
        return new MergedClass(packageName, className);
    }

    @Override
    public boolean printTo(Appendable a, String indent, String plusIndent) {
        return false;
    }

    private final class MergedClass implements Rules.ForClass {
        private final CharSequence mPackageName, mClassName;

        MergedClass(CharSequence packageName, CharSequence className) {
            mPackageName = packageName;
            mClassName = className;
        }

        @Override
        public boolean isAllAllowed() {
            for (Rules rules : mSources) {
                if (!rules.forClass(mPackageName, mClassName).isAllAllowed()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Rule ruleForConstructor(CharSequence desc) {
            Rule mergedRule = Rule.allow();
            for (Rules rules : mSources) {
                Rule rule = rules.forClass(mPackageName, mClassName).ruleForConstructor(desc);
                mergedRule = merge(mergedRule, rule);
            }
            return mergedRule;
        }

        @Override
        public Rule ruleForMethod(CharSequence name, CharSequence desc) {
            Rule mergedRule = Rule.allow();
            for (Rules rules : mSources) {
                Rule rule = rules.forClass(mPackageName, mClassName).ruleForMethod(name, desc);
                mergedRule = merge(mergedRule, rule);
            }
            return mergedRule;
        }

        private static Rule merge(Rule mergedRule, Rule addedRule) {
            if (!addedRule.isDeniedAtTarget()) {
                return mergedRule;
            }
            if (mergedRule == Rule.allow()) {
                return addedRule;
            }
            DenyAction mergedAction = mergedRule.denyAction();
            DenyAction addedAction = addedRule.denyAction();
            if (mergedAction.equals(addedAction)) {
                return mergedRule;
            }
            return Rule.denyAtTarget
                ((mergedAction.isChecked() || addedAction.isChecked())
                 ? DenyAction.checkedDynamic() : DenyAction.dynamic());
        }

        @Override
        public boolean isAnyConstructorDenied() {
            // Note that this doesn't filter out "for caller" constructors because constructors
            // are always transformed in the target.
            for (Rules rules : mSources) {
                if (rules.forClass(mPackageName, mClassName).isAnyConstructorDenied()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isAnyDeniedAtCaller() {
            // Target rules only, so no need to check the sources.
            return false;
        }

        @Override
        public boolean isAnyDeniedAtTarget() {
            for (Rules rules : mSources) {
                if (rules.forClass(mPackageName, mClassName).isAnyDeniedAtTarget()) {
                    return true;
                }
            }
            return false;
        }
    }
}
