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

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealy;
import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;

import de.learnlib.filter.statistic.Counter;
import de.learnlib.oracle.membership.PASOracle;
import net.automatalib.automata.base.compact.CompactTransition;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.commons.util.Pair;
import net.automatalib.incremental.ConflictException;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class PAS implements LearningAlgorithm.MealyLearner<String,String> {
    private final PASOracle<Integer, String, CompactTransition<String>, String> oracle;
    private final Function<MembershipOracle.MealyMembershipOracle<String, String>, KearnsVaziraniMealy<String, String>> constructor;
    private KearnsVaziraniMealy<String, String> algorithm;
    private final Alphabet<String> alphabet;
    private final List<Pair<Integer, CompactMealy<String, String>>> hypotheses;
    public Counter counter;
    private List<Integer> conflictIndexes;

    public PAS(
            Function<MembershipOracle.MealyMembershipOracle<String, String>, KearnsVaziraniMealy<String, String>> constructor,
            MembershipOracle.MealyMembershipOracle<String, String> sulOracle, Alphabet<String> alphabet,
            Integer cexSearchLimit, Double revisionRatio, Double lengthFactor,
            Random random) {
        this.counter = new Counter("Membership Queries", "Number of membership queries");
        this.oracle = new PASOracle<>(alphabet, sulOracle, counter, cexSearchLimit, revisionRatio, lengthFactor,
                random);
        this.constructor = constructor;
        this.hypotheses = new LinkedList<>();
        this.alphabet = alphabet;
        this.conflictIndexes = new LinkedList<>();
    }

    @Override
    public void startLearning() {
        algorithm = constructor.apply(oracle);
        algorithm.startLearning();
        hypotheses.add(Pair.of((int) (long) counter.getCount(),
                new CompactMealy<>((CompactMealy<String, String>) algorithm.getHypothesisModel())));
    }

    public List<Pair<Integer, CompactMealy<String, String>>> run() {
        startLearning();
        DefaultQuery<String, Word<String>> cex = oracle.findCounterExample(getHypothesisModel(), alphabet);
        while (cex != null) {
            try {
                this.refineHypothesis(cex);
                cex = oracle.findCounterExample(getHypothesisModel(), alphabet);
            } catch (ConflictException e) {
                conflictIndexes.add((int) (long) counter.getCount());
                startLearning();
                cex = new DefaultQuery<>(Word.epsilon(), Word.epsilon(), Word.epsilon());
            }
        }

        System.out.println("# CONFLICTS: " + conflictIndexes);
        return hypotheses;
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<String, Word<String>> ceQuery) throws ConflictException {
        if (ceQuery.getInput().length() == 0) {
            return false;
        }

        Boolean out = algorithm.refineHypothesis(ceQuery);
        hypotheses.add(Pair.of((int) (long) counter.getCount(),
                new CompactMealy<>((CompactMealy<String, String>) algorithm.getHypothesisModel())));
        return out;
    }

    @Override
    public CompactMealy<String, String> getHypothesisModel() {
        return hypotheses.get(hypotheses.size() - 1).getSecond();
    }

}
