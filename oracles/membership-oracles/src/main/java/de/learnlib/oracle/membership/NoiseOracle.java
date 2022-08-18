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

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.Query;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;

public class NoiseOracle<I, O> implements MembershipOracle.MealyMembershipOracle<I, O> {
    private Random random;
    private MembershipOracle.MealyMembershipOracle<I, O> sulOracle;
    private Double probability;

    public NoiseOracle(Alphabet<I> alphabet, MembershipOracle.MealyMembershipOracle<I, O> sulOracle, Double probability,
            Random random) {
        this.sulOracle = sulOracle;
        this.random = random;
        this.probability = probability;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, Word<O>>> queries) {
        for (Query<I, Word<O>> query : queries) {
            if (query.getInput().size() == 0) {
                query.answer(Word.epsilon());
                continue;
            }

            Word<O> realOut = sulOracle.answerQuery(query.getInput());
            List<O> localOutputAlphabet = new LinkedList<>(realOut.asList());

            WordBuilder<O> newOut = new WordBuilder<>();
            // Save all but last symbol of suffix.
            for (O symbol : realOut.suffix(query.getSuffix().size()).prefix(query.getSuffix().size() - 1)) {
                newOut.add(symbol);
            }

            Collections.shuffle(localOutputAlphabet, random);
            newOut.add(random.nextFloat() < probability ? localOutputAlphabet.get(0) : realOut.lastSymbol());

            query.answer(newOut.toWord());
        }
    }

}
