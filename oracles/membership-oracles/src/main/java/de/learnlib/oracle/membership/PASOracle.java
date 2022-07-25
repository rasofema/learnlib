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
package de.learnlib.oracle.membership;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.query.Query;
import de.learnlib.filter.statistic.Counter;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.incremental.ConflictException;
import net.automatalib.incremental.mealy.tree.AdaptiveMealyTreeBuilder;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class PASOracle<S, I, T, O>
        implements MembershipOracle.MealyMembershipOracle<I, O>, EquivalenceOracle.MealyEquivalenceOracle<I, O> {
    private final AdaptiveMealyTreeBuilder<I, O> cache;
    private MealyMachine<S, I, T, O> hypothesis;
    private final Set<Word<I>> conflicts;
    private final Set<T> conflictedTransitions;
    private final Set<Word<I>> simulatedQueries;
    private final MembershipOracle.MealyMembershipOracle<I, O> sulOracle;
    private Counter counter;
    private Random random;
    private Boolean skipSimulation = false;

    public PASOracle(Alphabet<I> alphabet, MembershipOracle.MealyMembershipOracle<I, O> sulOracle, Counter counter,
            Random random) {
        this.cache = new AdaptiveMealyTreeBuilder<>(alphabet);
        this.conflicts = new HashSet<>();
        this.conflictedTransitions = new HashSet<>();
        this.simulatedQueries = new HashSet<>();
        this.sulOracle = sulOracle;
        this.counter = counter;
        this.random = random;
    }

    public MealyMachine<S, I, T, O> getHypothesis() {
        return hypothesis;
    }

    public void setHypothesis(MealyMachine<S, I, T, O> hypothesis) {
        this.hypothesis = hypothesis;
        conflicts.clear();
    }

    private List<T> getTransitions(Word<I> word) {
        List<T> transitions = new LinkedList<>();
        S state = hypothesis.getInitialState();
        for (I sym : word) {
            transitions.add(hypothesis.getTransition(state, sym));
            state = hypothesis.getSuccessor(state, sym);
        }
        return transitions;
    }

    private void addConflict(Query<I, Word<O>> query) {
        conflicts.add(query.getInput());
        conflictedTransitions.addAll(getTransitions(query.getInput()));
    }

    private Word<O> internalProcessQuery(Query<I, Word<O>> query, Boolean saveInCache) throws ConflictException {
        Word<O> answer = sulOracle.answerQuery(query.getInput());
        query.answer(answer.suffix(query.getSuffix().length()));
        simulatedQueries.remove(query.getInput());
        counter.increment();

        if (saveInCache) {
            cache.insert(query.getInput(), answer, (int) (long) counter.getCount());
        }

        // Conflict detected
        if (cache.conflicts(query.getInput(), answer)) {
            addConflict(query);
            Word<O> cachedAnswer = cache.lookup(query.getInput());
            cache.insert(query.getInput(), answer, (int) (long) counter.getCount());
            throw new ConflictException(
                    "Input: " + query.getInput() + ", Cache: " + cachedAnswer + ", Output: " + answer + ".");
        }

        return answer;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, Word<O>>> queries) throws ConflictException {
        for (Query<I, Word<O>> query : queries) {
            // 1: Check against cache.
            Word<O> cacheOutput = cache.lookup(query.getInput());
            if (query.getInput().length() == cacheOutput.length()) {
                query.answer(cacheOutput.suffix(query.getSuffix().length()));
                continue;
            }

            // 2: Check against hypothesis.
            if (hypothesis != null && !skipSimulation) {
                Set<T> overlappedTransitions = new HashSet<>(conflictedTransitions);
                overlappedTransitions.retainAll(getTransitions(query.getInput()));
                if (overlappedTransitions.isEmpty()) {
                    query.answer(hypothesis.computeOutput(query.getInput()));
                    simulatedQueries.add(query.getInput());
                    continue;
                }
            }

            // 3: Ask the SUL Oracle.
            internalProcessQuery(query, true);
        }
    }

    private Word<I> sampleWord() {
        double ALPHA = 0.9;
        if (random.nextFloat() < ALPHA) {
            List<I> alphas = new LinkedList<>(cache.getInputAlphabet());
            Collections.shuffle(alphas, random);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    @Override
    public @Nullable DefaultQuery<I, Word<O>> findCounterExample(MealyMachine<?, I, ?, O> hypothesis,
            Collection<? extends I> inputs) throws ConflictException {
        // FIXME: Weight based rather than full prioity.
        for (Word<I> simQuery : simulatedQueries) {
            DefaultQuery<I, Word<O>> query = new DefaultQuery<>(simQuery);
            Word<O> out = internalProcessQuery(query, true);
            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
        }

        while (counter.getCount() <= 21_000) {
            DefaultQuery<I, Word<O>> query = new DefaultQuery<>(sampleWord());
            Word<O> out = internalProcessQuery(query, false);
            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
        }

        return null;
    }

    public void skipSimulation() {
        skipSimulation = true;
    }

    public void useSimulation() {
        skipSimulation = false;
    }

}
