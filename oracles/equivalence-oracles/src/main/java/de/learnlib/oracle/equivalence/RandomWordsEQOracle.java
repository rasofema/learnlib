/* Copyright (C) 2013-2023 TU Dortmund
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
package de.learnlib.oracle.equivalence;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import de.learnlib.buildtool.refinement.annotation.GenerateRefinement;
import de.learnlib.buildtool.refinement.annotation.Generic;
import de.learnlib.buildtool.refinement.annotation.Interface;
import de.learnlib.buildtool.refinement.annotation.Map;
import de.learnlib.oracle.EquivalenceOracle.DFAEquivalenceOracle;
import de.learnlib.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.oracle.EquivalenceOracle.MooreEquivalenceOracle;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.MembershipOracle.DFAMembershipOracle;
import de.learnlib.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.oracle.MembershipOracle.MooreMembershipOracle;
import net.automatalib.automaton.concept.Output;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.automaton.transducer.MooreMachine;
import net.automatalib.common.util.collection.CollectionUtil;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

@GenerateRefinement(name = "DFARandomWordsEQOracle",
                    generics = "I",
                    parentGenerics = {@Generic(clazz = DFA.class, generics = {"?", "I"}),
                                      @Generic("I"),
                                      @Generic(clazz = Boolean.class)},
                    parameterMapping = @Map(from = MembershipOracle.class,
                                            to = DFAMembershipOracle.class,
                                            withGenerics = "I"),
                    interfaces = @Interface(clazz = DFAEquivalenceOracle.class, generics = "I"))
@GenerateRefinement(name = "MealyRandomWordsEQOracle",
                    generics = {"I", "O"},
                    parentGenerics = {@Generic(clazz = MealyMachine.class, generics = {"?", "I", "?", "O"}),
                                      @Generic("I"),
                                      @Generic(clazz = Word.class, generics = "O")},
                    parameterMapping = @Map(from = MembershipOracle.class,
                                            to = MealyMembershipOracle.class,
                                            withGenerics = {"I", "O"}),
                    interfaces = @Interface(clazz = MealyEquivalenceOracle.class, generics = {"I", "O"}))
@GenerateRefinement(name = "MooreRandomWordsEQOracle",
                    generics = {"I", "O"},
                    parentGenerics = {@Generic(clazz = MooreMachine.class, generics = {"?", "I", "?", "O"}),
                                      @Generic("I"),
                                      @Generic(clazz = Word.class, generics = "O")},
                    parameterMapping = @Map(from = MembershipOracle.class,
                                            to = MooreMembershipOracle.class,
                                            withGenerics = {"I", "O"}),
                    interfaces = @Interface(clazz = MooreEquivalenceOracle.class, generics = {"I", "O"}))
public class RandomWordsEQOracle<A extends Output<I, D>, I, D> extends AbstractTestWordEQOracle<A, I, D> {

    private final Random random;
    private final int maxTests;
    private final int minLength;
    private final int maxLength;

    public RandomWordsEQOracle(MembershipOracle<I, D> mqOracle, int minLength, int maxLength, int maxTests) {
        this(mqOracle, minLength, maxLength, maxTests, new Random());
    }

    public RandomWordsEQOracle(MembershipOracle<I, D> mqOracle,
                               int minLength,
                               int maxLength,
                               int maxTests,
                               Random random) {
        this(mqOracle, minLength, maxLength, maxTests, random, 1);
    }

    public RandomWordsEQOracle(MembershipOracle<I, D> mqOracle,
                               int minLength,
                               int maxLength,
                               int maxTests,
                               Random random,
                               int batchSize) {
        super(mqOracle, batchSize);
        this.maxTests = maxTests;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.random = random;
    }

    @Override
    public Stream<Word<I>> generateTestWords(A hypothesis, Collection<? extends I> inputs) {

        final List<? extends I> symbolList = CollectionUtil.randomAccessList(inputs);

        return Stream.generate(() -> generateTestWord(symbolList, symbolList.size())).limit(maxTests);
    }

    private Word<I> generateTestWord(List<? extends I> symbolList, int numSyms) {

        final int length = minLength + random.nextInt((maxLength - minLength) + 1);
        final WordBuilder<I> result = new WordBuilder<>(length);

        for (int j = 0; j < length; ++j) {
            int symidx = random.nextInt(numSyms);
            I sym = symbolList.get(symidx);
            result.append(sym);
        }

        return result.toWord();
    }
}
