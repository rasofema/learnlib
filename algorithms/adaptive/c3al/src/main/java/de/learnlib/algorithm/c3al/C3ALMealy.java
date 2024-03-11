/* Copyright (C) 2013-2024 TU Dortmund
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

import de.learnlib.algorithm.LearningAlgorithm;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.equivalence.AbstractTestWordEQOracle;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.incremental.mealy.tree.AdaptiveMealyTreeBuilder;
import net.automatalib.word.Word;

// The Conflict-Aware Active Automata Learning framework.
public class C3ALMealy<I, O> extends C3AL<MealyMachine<?, I, ?, O>, I, Word<O>> {

    public C3ALMealy(
            Function<MembershipOracle<I, Word<O>>, LearningAlgorithm<MealyMachine<?, I, ?, O>, I, Word<O>>> constructor,
            MembershipOracle<I, Word<O>> memOracle, MembershipOracle<I, Word<O>> eqOracle,
            AbstractTestWordEQOracle<MealyMachine<?, I, ?, O>, I, Word<O>> testOracle, Alphabet<I> alphabet,
            Double revisionRatio, Boolean caching, Random random,
            EventHandler<MealyMachine<?, I, ?, O>, I, Word<O>> eventHandler) {
        super(constructor, memOracle, eqOracle, testOracle, alphabet, new AdaptiveMealyTreeBuilder<>(alphabet),
                revisionRatio, caching, random, eventHandler);
    }

}
