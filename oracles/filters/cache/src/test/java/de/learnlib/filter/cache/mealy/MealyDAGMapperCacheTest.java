/* Copyright (C) 2013-2022 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.filter.cache.mealy;

import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;

/**
 * @author frohme
 */
public class MealyDAGMapperCacheTest extends AbstractMealyCacheTest {

    @Override
    protected MealyCacheOracle<Character, Integer> getCache(MealyMembershipOracle<Character, Integer> delegate) {
        return MealyCaches.createDAGCache(getAlphabet(), super.errorMapper, delegate);
    }

    @Override
    protected boolean usesMapping() {
        return true;
    }
}
