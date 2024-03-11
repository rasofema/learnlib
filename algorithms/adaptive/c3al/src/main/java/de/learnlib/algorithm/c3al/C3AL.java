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
package de.learnlib.algorithm.c3al;

import java.util.Random;
import java.util.function.Function;

import com.rits.cloning.Cloner;

import de.learnlib.algorithm.LearningAlgorithm;
import de.learnlib.exception.LimitException;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.equivalence.AbstractTestWordEQOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.concept.Output;
import net.automatalib.incremental.AdaptiveConstruction;
import net.automatalib.incremental.ConflictException;
import net.automatalib.word.Word;

// The Conflict-Aware Active Automata Learning framework.
public class C3AL<M extends Output<I, D>, I, D> implements LearningAlgorithm<M, I, D> {
    public final Reviser<M, I, D> oracle;
    private final Function<MembershipOracle<I, D>, LearningAlgorithm<M, I, D>> constructor;
    private LearningAlgorithm<M, I, D> algorithm;
    private final Alphabet<I> alphabet;
    private M currentHyp;
    private M finalHyp;
    private final EventHandler<M, I, D> eventHandler;

    public C3AL(Function<MembershipOracle<I, D>, LearningAlgorithm<M, I, D>> constructor,
            MembershipOracle<I, D> memOracle, MembershipOracle<I, D> eqOracle,
            AbstractTestWordEQOracle<M, I, D> testOracle, Alphabet<I> alphabet, AdaptiveConstruction<M, I, D> cache,
            Double revisionRatio, Boolean caching, Random random, EventHandler<M, I, D> eventHandler) {
        this.oracle = new Reviser<>(cache, memOracle, eqOracle, testOracle, eventHandler, revisionRatio, caching,
                random);
        this.constructor = constructor;
        this.alphabet = alphabet;
        this.eventHandler = eventHandler;
    }

    @Override
    public void startLearning() {
        algorithm = constructor.apply(oracle);
        try {
            algorithm.startLearning();
            saveHypothesis();
        } catch (ConflictException e) {
            startLearning();
        } catch (LimitException e) {
            return;
        }
    }

    private void saveHypothesis() {
        Cloner cloner = new Cloner();
        M newHyp = cloner.deepClone(algorithm.getHypothesisModel());
        currentHyp = newHyp;
        M finalHyp = eventHandler.hypEvent(newHyp);

        if (finalHyp != null) {
            this.finalHyp = finalHyp;
            throw new LearningFinishedException();
        }
    }

    public M run() {
        try {
            startLearning();
            DefaultQuery<I, D> cex;
            try {
                cex = oracle.findCounterExample(getHypothesisModel(), alphabet);
            } catch (ConflictException e) {
                startLearning();
                cex = new DefaultQuery<>(Word.epsilon());
            } catch (LimitException e) {
                return finalHyp;
            }

            while (cex != null) {
                try {
                    this.refineHypothesis(cex);
                    cex = oracle.findCounterExample(getHypothesisModel(), alphabet);
                } catch (ConflictException e) {
                    startLearning();
                    cex = new DefaultQuery<>(Word.epsilon());
                } catch (LimitException e) {
                    return finalHyp;
                }
            }
        } catch (LearningFinishedException e) {
            finalHyp = currentHyp;
        }

        return finalHyp;
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, D> ceQuery) throws ConflictException, LimitException {
        if (ceQuery.getInput().length() == 0) {
            return false;
        }

        Boolean out = algorithm.refineHypothesis(ceQuery);
        saveHypothesis();
        return out;
    }

    @Override
    public M getHypothesisModel() {
        return currentHyp;
    }

}
