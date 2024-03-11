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

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.exception.LimitException;
import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.equivalence.AbstractTestWordEQOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.query.Query;
import net.automatalib.automaton.concept.Output;
import net.automatalib.common.util.Pair;
import net.automatalib.incremental.AdaptiveConstruction;
import net.automatalib.incremental.ConflictException;
import net.automatalib.word.Word;

public class Reviser<M extends Output<I, D>, I, D> implements MembershipOracle<I, D>, EquivalenceOracle<M, I, D> {
    private final AdaptiveConstruction<M, I, D> cache;
    private final MembershipOracle<I, D> memOracle;
    private final MembershipOracle<I, D> eqOracle;
    private Random random;
    private Double revisionRatio;
    private Boolean caching;
    private AbstractTestWordEQOracle<M, I, D> testOracle;
    private final EventHandler<M, I, D> eventHandler;

    public Reviser(AdaptiveConstruction<M, I, D> cache, MembershipOracle<I, D> memOracle,
            MembershipOracle<I, D> eqOracle, AbstractTestWordEQOracle<M, I, D> testOracle,
            EventHandler<M, I, D> eventHandler,
            Double revisionRatio, Boolean caching, Random random) {
        this.cache = cache;
        this.memOracle = memOracle;
        this.eqOracle = eqOracle;
        this.random = random;
        this.revisionRatio = revisionRatio;
        this.caching = caching;
        this.testOracle = testOracle;
        this.eventHandler = eventHandler;
    }

    private D internalProcessQuery(Query<I, D> query, Boolean isMemQuery)
            throws ConflictException, LimitException {
        D answer = (isMemQuery ? memOracle : eqOracle).answerQuery(query.getInput());
        query.answer(answer);

        // We have done things that changed the query count. So we update the bags and
        // check if now we are done.
        M finito = eventHandler.queryEvent(query);
        if (finito != null) {
            throw new LearningFinishedException();
        }

        // Conflict detected
        if (cache.insert(query.getInput(), answer)) {
            throw new ConflictException("Input: " + query.getInput());
        }

        return answer;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, D>> queries)
            throws ConflictException, LimitException {
        for (Query<I, D> query : queries) {
            // A: Check against cache.
            if (caching) {
                Pair<Boolean, D> cacheOutput = cache.lookup(query.getInput());
                if (cacheOutput.getFirst()) {
                    query.answer(cacheOutput.getSecond());
                    continue;
                }
            }

            // B: Ask the SUL Oracle.
            internalProcessQuery(query, true);
        }
    }

    @Override
    public @Nullable DefaultQuery<I, D> findCounterExample(M hypothesis,
            Collection<? extends I> inputs) throws ConflictException, LimitException {

        Word<I> sepInput = cache.findSeparatingWord(hypothesis, inputs, true);
        while (sepInput != null) {
            DefaultQuery<I, D> query = new DefaultQuery<>(sepInput);
            D out = internalProcessQuery(query, false);
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
            DefaultQuery<I, D> query = new DefaultQuery<>(
                    random.nextFloat() < revisionRatio ? (Word<I>) cache.getOldestInput() : iter.next());
            D out = internalProcessQuery(query, false);

            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
        }
    }
}
