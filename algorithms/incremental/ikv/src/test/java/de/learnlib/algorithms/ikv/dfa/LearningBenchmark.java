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
import java.util.LinkedList;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.kv.StateInfo;
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
import net.automatalib.automata.fsa.MutableDFA;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Symbol;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class LearningBenchmark {
    private static final MutableDFA<Integer, Symbol> TARGET_DFA = SimpleDFA.constructMachine();
    private static final Alphabet<Symbol> ALPHABET = SimpleDFA.createInputAlphabet();
    private static final MembershipOracle.DFAMembershipOracle<Symbol> DFA_ORACLE = new SimulatorOracle.DFASimulatorOracle<>(TARGET_DFA);

    public static int testLearnModel(DFA<?, Symbol> target, Alphabet<Symbol> alphabet,
                                     KearnsVaziraniDFA<Symbol> learner,
                                     EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle) {

        Experiment.DFAExperiment<Symbol> experiment = new Experiment.DFAExperiment<Symbol>(learner, eqOracle, alphabet);

        experiment.setLogModels(true);
        experiment.setProfile(true);

        experiment.run();

        Assert.assertEquals(experiment.getFinalHypothesis().size(), DFAs.minimize(target, alphabet).size());
        return (int) experiment.getRounds().getCount();
    }

    public KearnsVaziraniDFA<Symbol> learnClassic() {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(DFA_ORACLE, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        KearnsVaziraniDFA<Symbol> learner = new KearnsVaziraniDFA<>(ALPHABET, memOracle, true, AcexAnalyzers.LINEAR_FWD);

        int cexCounter = testLearnModel(TARGET_DFA, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;

    }

    public KearnsVaziraniDFA<Symbol> learnIncremental(KearnsVaziraniDFAState<Symbol> startingState) {
        LinkedList<Symbol> accWord = new LinkedList<>();
        accWord.add(ALPHABET.getSymbol(0));
        TARGET_DFA.setAccepting(TARGET_DFA.getState(accWord), true);

        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(DFA_ORACLE, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        KearnsVaziraniDFA<Symbol> learner = new IKearnsVaziraniDFA<>(ALPHABET, memOracle, AcexAnalyzers.LINEAR_FWD, startingState);
        int cexCounter = testLearnModel(TARGET_DFA, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;
    }

    public void benchmark() throws IOException {
        KearnsVaziraniDFA<Symbol> classicLearner = learnClassic();
        writeDotFile(classicLearner.getHypothesisModel(), ALPHABET, "./classic.dot");

        // TODO: It would be good to clone the tree at some point.
        KearnsVaziraniDFAState<Symbol> startingState = classicLearner.suspend();
        KearnsVaziraniDFA<Symbol> incLearner = learnIncremental(startingState);
        writeDotFile(incLearner.getHypothesisModel(), ALPHABET, "./incremental.dot");
    }

    // policy : convert into method throwing unchecked exception
    public static <S, I, T> void writeDotFile(Automaton<S, I, T> automaton, Collection<? extends I> inputAlphabet, String filepath) throws IOException {
        writeFile(automaton, inputAlphabet, filepath);
    }

    //policy:
    //  write dotfile with red double circeled start state
    public static <S, I, T> void writeFile(Automaton<S, I, T> automaton, Collection<? extends I> inputAlphabet, String filepath) throws IOException {
        BufferedWriter outstream = new BufferedWriter(new FileWriter(filepath));
        write(automaton, inputAlphabet, outstream);
        outstream.close();
    }

    /* write
     *   same as writeFile but then to Appendable instead of filepath
     *
     */
    public static <S, I, T> void write(Automaton<S, I, T> automaton, Collection<? extends I> inputAlphabet, Appendable out) {
        try {
            GraphDOT.write(automaton, inputAlphabet, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
