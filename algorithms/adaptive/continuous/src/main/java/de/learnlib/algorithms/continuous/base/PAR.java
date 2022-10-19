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

import com.rits.cloning.Cloner;

import de.learnlib.algorithms.Reviser;
import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.exception.LimitException;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;

import de.learnlib.filter.statistic.Counter;
import net.automatalib.automata.base.compact.CompactTransition;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.commons.util.Pair;
import net.automatalib.incremental.ConflictException;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

// The Practically Adequate Reviser framework.
public class PAR<I, O> implements LearningAlgorithm.MealyLearner<I, O> {
    public final Reviser<Integer, I, CompactTransition<String>, O> oracle;
    private final Function<MembershipOracle<I, Word<O>>, LearningAlgorithm.MealyLearner<I, O>> constructor;
    private LearningAlgorithm.MealyLearner<I, O> algorithm;
    private final Alphabet<I> alphabet;
    private final List<Pair<Integer, MealyMachine<?, I, ?, O>>> hypotheses;
    public Counter queryCounter;
    private List<Integer> conflictIndexes;

    public PAR(
            Function<MembershipOracle<I, Word<O>>, LearningAlgorithm.MealyLearner<I, O>> constructor,
            MembershipOracle<I, Word<O>> sulOracle, Alphabet<I> alphabet,
            Integer cexSearchLimit, Double revisionRatio, Boolean caching, Random random,
            Counter queryCounter) {
        this.queryCounter = queryCounter;
        this.oracle = new Reviser<>(alphabet, sulOracle, queryCounter, cexSearchLimit, revisionRatio, caching, random);
        this.constructor = constructor;
        this.hypotheses = new LinkedList<>();
        this.alphabet = alphabet;
        this.conflictIndexes = new LinkedList<>();
    }

    @Override
    public void startLearning() {
        algorithm = constructor.apply(oracle);
        try {
            algorithm.startLearning();
            saveHypothesis();
        } catch (ConflictException e) {
            conflictIndexes.add((int) (long) queryCounter.getCount());
            startLearning();
        } catch (LimitException e) {
            return;
        }
    }

    private void saveHypothesis() {
        Cloner cloner = new Cloner();
        hypotheses.add(Pair.of((int) (long) queryCounter.getCount(),
                cloner.deepClone(algorithm.getHypothesisModel())));
    }

    public List<Pair<Integer, MealyMachine<?, I, ?, O>>> run() {
        startLearning();
        DefaultQuery<I, Word<O>> cex;
        try {
            cex = oracle.findCounterExample(getHypothesisModel(), alphabet);
        } catch (ConflictException e) {
            conflictIndexes.add((int) (long) queryCounter.getCount());
            startLearning();
            cex = new DefaultQuery<>(Word.epsilon(), Word.epsilon(), Word.epsilon());
        } catch (LimitException e) {
            return hypotheses;
        }

        while (cex != null) {
            try {
                this.refineHypothesis(cex);
                cex = oracle.findCounterExample(getHypothesisModel(), alphabet);
            } catch (ConflictException e) {
                conflictIndexes.add((int) (long) queryCounter.getCount());
                startLearning();
                cex = new DefaultQuery<>(Word.epsilon(), Word.epsilon(), Word.epsilon());
            } catch (LimitException e) {
                return hypotheses;
            }
        }

        return hypotheses;
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ceQuery)
            throws ConflictException, LimitException {
        if (ceQuery.getInput().length() == 0) {
            return false;
        }

        Boolean out = algorithm.refineHypothesis(ceQuery);
        saveHypothesis();
        return out;
    }

    @Override
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        return hypotheses.get(hypotheses.size() - 1).getSecond();
    }

}
