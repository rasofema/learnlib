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
package de.learnlib.filter.cache.mealy;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.query.Query;
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
    private final EquivalenceOracle.MealyEquivalenceOracle<I, O> eqOracle;
    private Integer queryCounter;

    public PASOracle(Alphabet<I> alphabet, MembershipOracle.MealyMembershipOracle<I, O> sulOracle,
            EquivalenceOracle.MealyEquivalenceOracle<I, O> backingEQMethod) {
        this.cache = new AdaptiveMealyTreeBuilder<>(alphabet);
        this.conflicts = new HashSet<>();
        this.conflictedTransitions = new HashSet<>();
        this.simulatedQueries = new HashSet<>();
        this.sulOracle = sulOracle;
        this.eqOracle = backingEQMethod;
        this.queryCounter = 0;
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

    private void addConflict(Query<I, Word<O>> query, Word<O> answer) {
        Word<O> cachedOutput = cache.lookup(query.getInput());
        int minConflict = answer.longestCommonPrefix(cachedOutput).length() + 1;
        Word<I> conflict = query.getInput().prefix(minConflict);
        conflicts.add(conflict);
        conflictedTransitions.addAll(getTransitions(conflict));
    }

    private Word<O> internalProcessQuery(Query<I, Word<O>> query) throws ConflictException {
        Word<O> answer = sulOracle.answerQuery(query.getPrefix(), query.getSuffix());
        query.answer(answer);
        simulatedQueries.remove(query.getInput());
        queryCounter += 1;

        if (cache.insert(query.getInput(), answer, queryCounter)) {
            addConflict(query, answer);
            throw new ConflictException();
        }

        return answer;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, Word<O>>> queries) throws ConflictException {
        for (Query<I, Word<O>> query : queries) {

            // 1: Check against cache.
            Word<O> cacheOutput = cache.lookup(query.getInput());
            if (query.getInput().length() == cacheOutput.length()) {
                query.answer(cacheOutput);
                continue;
            }

            // 2: Check against hypothesis.
            Set<T> overlappedTransitions = new HashSet<>(conflictedTransitions);
            overlappedTransitions.retainAll(getTransitions(query.getInput()));
            if (overlappedTransitions.isEmpty()) {
                query.answer(hypothesis.computeOutput(query.getInput()));
                simulatedQueries.add(query.getInput());
                continue;
            }

            // 3: Ask the SUL Oracle.
            internalProcessQuery(query);
        }
    }

    @Override
    public @Nullable DefaultQuery<I, Word<O>> findCounterExample(MealyMachine<?, I, ?, O> hypothesis,
            Collection<? extends I> inputs) {
        // TODO Auto-generated method stub
        return null;
    }

}
