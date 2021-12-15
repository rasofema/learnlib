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
package de.learnlib.algorithms.ikv.dfa;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Random;

import de.learnlib.acex.analyzers.AcexAnalyzers;
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
    private static Long RAND_SEED1 = (new Random()).nextLong();
    private static Long RAND_SEED2 = (new Random()).nextLong();
    private static CompactDFA<Symbol> TARGET_DFA1 = (new RandomAutomata(new Random(RAND_SEED1))).randomDFA(10, ALPHABET);
    private static CompactDFA<Symbol> TARGET_DFA2 = (new RandomAutomata(new Random(RAND_SEED2))).randomDFA(10, ALPHABET);

    private static MembershipOracle.DFAMembershipOracle<Symbol> DFA_ORACLE1 = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA1);
    private static MembershipOracle.DFAMembershipOracle<Symbol> DFA_ORACLE2_CLASS = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA2);
    private static MembershipOracle.DFAMembershipOracle<Symbol> DFA_ORACLE2_INC = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA2);

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

    private KearnsVaziraniDFA<Symbol> learnClassic(CompactDFA<Symbol> target, MembershipOracle.DFAMembershipOracle<Symbol> oracle) {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 8);

        KearnsVaziraniDFA<Symbol> learner = new KearnsVaziraniDFA<>(ALPHABET, memOracle, true, AcexAnalyzers.LINEAR_FWD);

        int cexCounter = testLearnModel(target, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;

    }

    private KearnsVaziraniDFA<Symbol> learnIncremental(KearnsVaziraniDFAState<Symbol> startingState, CompactDFA<Symbol> target, MembershipOracle.DFAMembershipOracle<Symbol> oracle) {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        KearnsVaziraniDFA<Symbol> learner = new IKearnsVaziraniDFA<>(ALPHABET, memOracle, AcexAnalyzers.LINEAR_FWD, startingState);
        int cexCounter = testLearnModel(target, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;
    }

    public void benchmark() throws IOException {
        System.out.println("Autoamton seed1: " + RAND_SEED1);
        System.out.println("Autoamton seed2: " + RAND_SEED2);

        KearnsVaziraniDFA<Symbol> classicLearner = learnClassic(TARGET_DFA1, DFA_ORACLE1);
        writeDotFile(classicLearner.getHypothesisModel(), ALPHABET, "./classic.dot");

        KearnsVaziraniDFA<Symbol> classicDiffLearner = learnClassic(TARGET_DFA2, DFA_ORACLE2_CLASS);
        writeDotFile(classicDiffLearner.getHypothesisModel(), ALPHABET, "./classic-diff.dot");

        // TODO: It would be good to clone the tree at some point.
        KearnsVaziraniDFAState<Symbol> startingState = classicLearner.suspend();

        KearnsVaziraniDFA<Symbol> incLearner = learnIncremental(startingState, TARGET_DFA2, DFA_ORACLE2_INC);
        writeDotFile(incLearner.getHypothesisModel(), ALPHABET, "./incremental.dot");
    }

    public void repeat() throws IOException {
        for (int i = 0; i < 5000; i++) {
            System.out.println("ITER: " + i);
            RAND_SEED1 = (new Random()).nextLong();
            RAND_SEED2 = (new Random()).nextLong();
            TARGET_DFA1 = (new RandomAutomata(new Random(RAND_SEED1))).randomDFA(20, ALPHABET);
            TARGET_DFA2 = (new RandomAutomata(new Random(RAND_SEED2))).randomDFA(20, ALPHABET);

            DFA_ORACLE1 = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA1);
            DFA_ORACLE2_CLASS = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA2);
            DFA_ORACLE2_INC = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA2);
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
