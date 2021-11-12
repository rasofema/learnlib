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
package de.learnlib.algorithms.continuous.dfa;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.ikv.dfa.SimpleDFA;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFAState;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.equivalence.WpMethodEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import de.learnlib.util.Experiment;
import net.automatalib.automata.Automaton;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.FastAlphabet;
import net.automatalib.words.impl.Symbol;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class LearningBenchmark {
    private static final Alphabet<Symbol> ALPHABET = new FastAlphabet<>(new Symbol("0"), new Symbol("1"), new Symbol("2"));
    private static Long RAND_SEED = (new Random()).nextLong();

    private static CompactDFA<Symbol> TARGET = (new RandomAutomata(new Random(RAND_SEED))).randomDFA(10, ALPHABET);
    private static MembershipOracle.DFAMembershipOracle<Symbol> ORACLE = new SimulatorOracle.DFASimulatorOracle<>(TARGET);

    private static int testLearnModel(DFA<?, Symbol> target, Alphabet<Symbol> alphabet,
                                     KearnsVaziraniDFA<Symbol> learner,
                                     EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle) {

        Experiment.DFAExperiment<Symbol> experiment = new Experiment.DFAExperiment<Symbol>(learner, eqOracle, alphabet);

        experiment.setLogModels(true);
        experiment.setProfile(true);

        experiment.run();

        Assert.assertEquals(experiment.getFinalHypothesis().size(), DFAs.minimize(target, alphabet).size());
        Assert.assertTrue(DFAs.acceptsEmptyLanguage(DFAs.xor(experiment.getFinalHypothesis(), DFAs.minimize(target, alphabet), alphabet)));
        return (int) experiment.getRounds().getCount();
    }

    private KearnsVaziraniDFA<Symbol> learnClassic(DFA<?, Symbol> target, MembershipOracle.DFAMembershipOracle<Symbol> oracle) {
        long startTime = System.nanoTime();
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 8);

        KearnsVaziraniDFA<Symbol> learner = new KearnsVaziraniDFA<>(ALPHABET, memOracle, true, AcexAnalyzers.LINEAR_FWD);

        int cexCounter = testLearnModel(target, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        long endTime = System.nanoTime();
        System.out.println(endTime - startTime);
        return learner;

    }

    private CompactDFA<Symbol> learnContinuous(MembershipOracle.DFAMembershipOracle<Symbol> oracle) {
        long startTime = System.nanoTime();
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");

        ContinuousDFA<Symbol> learner = new ContinuousDFA<>(ALPHABET, 0.9, memOracle);
        List<CompactDFA<Symbol>> results = learner.learn(10000);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        long endTime = System.nanoTime();
        System.out.println(endTime - startTime);
        return results.get(results.size() - 1);
    }

    public void benchmark() throws IOException {
        System.out.println("SEED: " + RAND_SEED);
        KearnsVaziraniDFA<Symbol> classicLearner = learnClassic(TARGET, ORACLE);
        writeDotFile(classicLearner.getHypothesisModel(), ALPHABET, "./classic.dot");

        CompactDFA<Symbol> contDFA = learnContinuous(ORACLE);
        writeDotFile(contDFA, ALPHABET, "./continuous.dot");

        assert DFAs.acceptsEmptyLanguage(DFAs.xor(classicLearner.getHypothesisModel(), contDFA, ALPHABET));
    }

    public void repeat() throws IOException {
        for (int i = 0; i < 1000; i++) {
            RAND_SEED = new Random().nextLong();
            TARGET = (new RandomAutomata(new Random(RAND_SEED))).randomDFA(10, ALPHABET);
            ORACLE = new SimulatorOracle.DFASimulatorOracle<>(TARGET);
            benchmark();
        }
    }

    // policy : convert into method throwing unchecked exception
    private static <S, I, T> void writeDotFile(Automaton<S, I, T> automaton, Collection<? extends I> inputAlphabet, String filepath) throws IOException {
        BufferedWriter outstream = new BufferedWriter(new FileWriter(filepath));
        try {
            GraphDOT.write(automaton, inputAlphabet, outstream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        outstream.close();
    }
}
