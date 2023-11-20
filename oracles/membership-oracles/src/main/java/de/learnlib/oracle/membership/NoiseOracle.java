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

import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.Query;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.word.Word;

public class NoiseOracle<I, O> implements MembershipOracle.MealyMembershipOracle<I, O> {
    public enum NoiseType {
        INPUT, OUTPUT
    }

    private Random random;
    private NoiseType noise;
    private MembershipOracle.MealyMembershipOracle<I, O> sulOracle;
    private Double probability;

    public NoiseOracle(Alphabet<I> inputAlphabet, Alphabet<O> outputAlphabet,
            MembershipOracle.MealyMembershipOracle<I, O> sulOracle, Double probability, NoiseType noiseType,
            Random random) {
        this.sulOracle = sulOracle;
        this.random = random;
        this.probability = probability;
        this.noise = noiseType;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, Word<O>>> queries) {
        for (Query<I, Word<O>> query : queries) {
            if (query.getInput().size() == 0) {
                query.answer(Word.epsilon());
                continue;
            }

            Word<I> input = Word.fromWords(query.getInput());
            List<I> localInputAlphabet = new LinkedList<>(input.asList());

            if (noise == NoiseType.INPUT) {
                Word<I> newInput = Word.epsilon();
                for (int i = 0; i < input.length(); i++) {
                    if (random.nextDouble() < probability) {
                        Collections.shuffle(localInputAlphabet, random);
                        newInput = newInput.append(localInputAlphabet.get(0));
                    } else {
                        newInput = newInput.append(input.getSymbol(i));
                    }
                }
                input = newInput;
            }

            Word<O> output = sulOracle.answerQuery(input);
            List<O> localOutputAlphabet = new LinkedList<>(output.asList());

            if (noise == NoiseType.OUTPUT) {
                Word<O> newOutput = Word.epsilon();
                for (int i = 0; i < output.length(); i++) {
                    if (random.nextDouble() < probability) {
                        Collections.shuffle(localOutputAlphabet, random);
                        newOutput = newOutput.append(localOutputAlphabet.get(0));
                    } else {
                        newOutput = newOutput.append(output.getSymbol(i));
                    }
                }
                output = newOutput;
            }

            query.answer(output);
        }
    }

}
