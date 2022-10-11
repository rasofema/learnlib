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
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.exception.LimitException;
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

public class Reviser<S, I, T, O>
        implements MembershipOracle<I, Word<O>>, EquivalenceOracle.MealyEquivalenceOracle<I, O> {
    private final AdaptiveMealyTreeBuilder<I, O> cache;
    private final MembershipOracle<I, Word<O>> sulOracle;
    private Counter counter;
    private Random random;
    private Integer limit;
    private Double revisionRatio;
    private Double lengthFactor;
    private Boolean caching;

    public Reviser(Alphabet<I> alphabet, MembershipOracle<I, Word<O>> sulOracle, Counter counter,
            Integer cexSearchLimit, Double revisionRatio, Double lengthFactor, Boolean caching, Random random) {
        this.cache = new AdaptiveMealyTreeBuilder<>(alphabet);
        this.sulOracle = sulOracle;
        this.counter = counter;
        this.random = random;
        this.limit = cexSearchLimit;
        this.revisionRatio = revisionRatio;
        this.lengthFactor = lengthFactor;
        this.caching = caching;
    }

    private Word<O> internalProcessQuery(Query<I, Word<O>> query) throws ConflictException, LimitException {
        Word<O> answer = sulOracle.answerQuery(query.getInput());
        query.answer(answer.suffix(query.getSuffix().length()));

        // Conflict detected
        if (cache.insert(query.getInput(), answer)) {
            throw new ConflictException("Input: " + query.getInput());
        }

        if (counter.getCount() >= limit) {
            throw new LimitException("Reached query limit.");
        }

        return answer;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, Word<O>>> queries)
            throws ConflictException, LimitException {
        for (Query<I, Word<O>> query : queries) {
            // A: Check against cache.
            if (caching) {
                Word<O> cacheOutput = cache.lookup(query.getInput());
                if (query.getInput().length() == cacheOutput.length()) {
                    query.answer(cacheOutput.suffix(query.getSuffix().length()));
                    continue;
                }
            }

            // B: Ask the SUL Oracle.
            internalProcessQuery(query);
        }
    }

    private Word<I> sampleWord() {
        if (random.nextFloat() < lengthFactor) {
            List<I> alphas = new LinkedList<>(cache.getInputAlphabet());
            Collections.shuffle(alphas, random);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    @Override
    public @Nullable DefaultQuery<I, Word<O>> findCounterExample(MealyMachine<?, I, ?, O> hypothesis,
            Collection<? extends I> inputs) throws ConflictException, LimitException {

        Word<I> sepInput = cache.findSeparatingWord(hypothesis, inputs, true);
        while (sepInput != null) {
            DefaultQuery<I, Word<O>> query = new DefaultQuery<>(sepInput);
            Word<O> out = internalProcessQuery(query);
            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
            sepInput = cache.findSeparatingWord(hypothesis, inputs, true);
        }

        while (counter.getCount() < limit) {
            DefaultQuery<I, Word<O>> query = new DefaultQuery<>(
                    random.nextFloat() < revisionRatio ? (Word<I>) cache.getOldestInput() : sampleWord());
            Word<O> out = internalProcessQuery(query);

            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
        }

        return null;
    }
}
