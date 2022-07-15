/* Copyright (C) 2013-2021 TU Dortmund
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
package de.learnlib.algorithms.continuous.base;

import java.util.function.Function;

import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.cache.mealy.PASOracle;
import de.learnlib.oracle.membership.SimulatorOmegaOracle;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class PAS<I, O> implements LearningAlgorithm.MealyLearner<I, O> {
    private final PASOracle<I, O> oracle;

    private final Function<MembershipOracle.MealyMembershipOracle<I, O>, LearningAlgorithm.MealyLearner<I, O>> constructor;
    private LearningAlgorithm.MealyLearner<I, O> algorithm;

    public PAS(Function<MembershipOracle.MealyMembershipOracle<I, O>, LearningAlgorithm.MealyLearner<I, O>> constructor,
            MembershipOracle.MealyMembershipOracle<I, O> sulOracle,
            EquivalenceOracle.MealyEquivalenceOracle<I, O> eqOracle, Alphabet<I> alphabet) {
        this.oracle = new PASOracle<>(alphabet, sulOracle, eqOracle);
        this.constructor = constructor;
    }

    @Override
    public void startLearning() {
        algorithm = constructor.apply(simulator);
        algorithm.startLearning();
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ceQuery) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        return algorithm.getHypothesisModel();
    }

}
