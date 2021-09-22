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

package de.learnlib.algorithms.ilstar;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;

import de.learnlib.algorithms.ilstar.dfa.ExtensibleILStarDFA;
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFA;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import de.learnlib.datastructure.observationtable.OTLearner;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.equivalence.WpMethodEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import net.automatalib.automata.Automaton;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Symbol;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class LearningBenchmark {
    private static final Alphabet<Symbol> ALPHABET = SimpleDFA.createInputAlphabet();
    private static Long RAND_SEED1 = 924193704317147478L /*(new Random()).nextLong()*/;
    private static Long RAND_SEED2 = 6103753416947823906L /*(new Random()).nextLong()*/;
    private static CompactDFA<Symbol> TARGET_DFA1 = (new RandomAutomata(new Random(RAND_SEED1))).randomDFA(10, ALPHABET);
    private static CompactDFA<Symbol> TARGET_DFA2 = (new RandomAutomata(new Random(RAND_SEED2))).randomDFA(10, ALPHABET);

    private static MembershipOracle.DFAMembershipOracle<Symbol> DFA_ORACLE1 = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA1);
    private static MembershipOracle.DFAMembershipOracle<Symbol> DFA_ORACLE2 = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA2);

    private static int testLearnModel(DFA<?, Symbol> target, Alphabet<Symbol> alphabet,
        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learner,
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle) {
        int cexCounter = 0;

        learner.startLearning();

        while (true) {
            DFA<?, Symbol> hyp = learner.getHypothesisModel();
            Assert.assertFalse(((GenericObservationTable<Symbol, Boolean>) learner.getObservationTable()).findContradiction());

            // Fun fact: There's no guarantee that intermediate hyp will be canonical, only DFAs.
            // This is because we could have 2 states that are different in the table,
            // but are just waiting for a cex to be corrected, thus pointing to the same row.

            DefaultQuery<Symbol, Boolean> ce = eqOracle.findCounterExample(hyp, alphabet);
            cexCounter++;

            if (ce == null) {
                break;
            }

            learner.refineHypothesis(ce);
        }

        DFA<?, Symbol> hyp = learner.getHypothesisModel();

        Assert.assertEquals(hyp.size(), DFAs.minimize(target, alphabet).size());
        assert DFAs.acceptsEmptyLanguage(DFAs.xor(hyp, DFAs.minimize(target, alphabet), ALPHABET));
        return cexCounter;
    }

    private OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learnClassic(CompactDFA<Symbol> target, MembershipOracle.DFAMembershipOracle<Symbol> oracle) {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        ClassicLStarDFA<Symbol> learner = new ClassicLStarDFA<>(ALPHABET, memOracle);

        int cexCounter = testLearnModel(target, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;

    }

    private OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learnIncremental(GenericObservationTable<Symbol, Boolean> startingOT) {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(DFA_ORACLE2, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learner = new ExtensibleILStarDFA<>(ALPHABET, memOracle, startingOT);
        int cexCounter = testLearnModel(TARGET_DFA2, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;
    }

    public void benchmark() throws IOException {
        System.out.println("Autoamton seed1: " + RAND_SEED1);
        System.out.println("Autoamton seed2: " + RAND_SEED2);

        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> classicLearner = learnClassic(TARGET_DFA1, DFA_ORACLE1);
        writeDotFile(classicLearner.getHypothesisModel(), ALPHABET, "./classic.dot");

        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> classicDiffLearner = learnClassic(TARGET_DFA2, DFA_ORACLE2);
        writeDotFile(classicDiffLearner.getHypothesisModel(), ALPHABET, "./classic-diff.dot");

        GenericObservationTable<Symbol, Boolean> startingOT = new GenericObservationTable<>((GenericObservationTable<Symbol, Boolean>) classicLearner.getObservationTable());
        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> incLearner = learnIncremental(startingOT);
        writeDotFile(incLearner.getHypothesisModel(), ALPHABET, "./incremental.dot");

        assert DFAs.acceptsEmptyLanguage(DFAs.xor(classicDiffLearner.getHypothesisModel(), incLearner.getHypothesisModel(), ALPHABET));
    }

    public void repeat() throws IOException {
        for (int i = 0; i < 5000; i++) {
            System.out.println("ITER: " + i);
            RAND_SEED1 = (new Random()).nextLong();
            RAND_SEED2 = (new Random()).nextLong();
            TARGET_DFA1 = (new RandomAutomata(new Random(RAND_SEED1))).randomDFA(10, ALPHABET);
            TARGET_DFA2 = (new RandomAutomata(new Random(RAND_SEED2))).randomDFA(10, ALPHABET);

            DFA_ORACLE1 = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA1);
            DFA_ORACLE2 = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA2);
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
