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
package de.learnlib.algorithms;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.exception.LimitException;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.query.Query;
import de.learnlib.oracle.equivalence.AbstractTestWordEQOracle;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.incremental.ConflictException;
import net.automatalib.incremental.mealy.tree.AdaptiveMealyTreeBuilder;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class Reviser<S, I, T, O>
        implements MembershipOracle<I, Word<O>>, EquivalenceOracle.MealyEquivalenceOracle<I, O> {
    private final AdaptiveMealyTreeBuilder<I, O> cache;
    private final MembershipOracle<I, Word<O>> memOracle;
    private final MembershipOracle<I, Word<O>> eqOracle;
    private Random random;
    private Double revisionRatio;
    private Boolean caching;
    private AbstractTestWordEQOracle<MealyMachine<?, I, ?, O>, I, Word<O>> testOracle;

    public Reviser(Alphabet<I> alphabet, MembershipOracle<I, Word<O>> memOracle, MembershipOracle<I, Word<O>> eqOracle,
            AbstractTestWordEQOracle<MealyMachine<?, I, ?, O>, I, Word<O>> testOracle,
            Double revisionRatio, Boolean caching, Random random) {
        this.cache = new AdaptiveMealyTreeBuilder<>(alphabet);
        this.memOracle = memOracle;
        this.eqOracle = eqOracle;
        this.random = random;
        this.revisionRatio = revisionRatio;
        this.caching = caching;
        this.testOracle = testOracle;
    }

    private Word<O> internalProcessQuery(Query<I, Word<O>> query, Boolean isMemQuery)
            throws ConflictException, LimitException {
        Word<O> answer = (isMemQuery ? memOracle : eqOracle).answerQuery(query.getInput());
        query.answer(answer.suffix(query.getSuffix().length()));

        // Conflict detected
        if (cache.insert(query.getInput(), answer)) {
            throw new ConflictException("Input: " + query.getInput());
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
            internalProcessQuery(query, true);
        }
    }

    @Override
    public @Nullable DefaultQuery<I, Word<O>> findCounterExample(MealyMachine<?, I, ?, O> hypothesis,
            Collection<? extends I> inputs) throws ConflictException, LimitException {

        Word<I> sepInput = cache.findSeparatingWord(hypothesis, inputs, true);
        while (sepInput != null) {
            DefaultQuery<I, Word<O>> query = new DefaultQuery<>(sepInput);
            Word<O> out = internalProcessQuery(query, false);
            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
            sepInput = cache.findSeparatingWord(hypothesis, inputs, true);
        }

        Iterator<Word<I>> iter = testOracle.generateTestWords(hypothesis, inputs).iterator();

        while (true) {
            if (!iter.hasNext()) {
                iter = testOracle.generateTestWords(hypothesis, inputs).iterator();
            }
            DefaultQuery<I, Word<O>> query = new DefaultQuery<>(
                    random.nextFloat() < revisionRatio ? (Word<I>) cache.getOldestInput() : iter.next());
            Word<O> out = internalProcessQuery(query, false);

            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
        }
    }
}
