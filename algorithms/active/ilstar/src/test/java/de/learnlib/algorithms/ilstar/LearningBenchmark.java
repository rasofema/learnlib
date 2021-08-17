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
        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learner,
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle) {
        int cexCounter = 0;

        learner.startLearning();

        while (true) {
            DFA<?, Symbol> hyp = learner.getHypothesisModel();

            DefaultQuery<Symbol, Boolean> ce = eqOracle.findCounterExample(hyp, alphabet);
            cexCounter++;

            if (ce == null) {
                break;
            }

            // Assert.assertNotEquals(maxRounds, 0);

            learner.refineHypothesis(ce);
        }

        DFA<?, Symbol> hyp = learner.getHypothesisModel();

         Assert.assertEquals(hyp.size(), DFAs.minimize(target, alphabet).size());
        return cexCounter;
    }

    public OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learnClassic() {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(DFA_ORACLE, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        ClassicLStarDFA<Symbol> learner = new ClassicLStarDFA<>(ALPHABET, memOracle);

        int cexCounter = testLearnModel(TARGET_DFA, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;

    }

    public OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learnIncremental(GenericObservationTable<Symbol, Boolean> startingOT) {
        LinkedList<Symbol> accWord = new LinkedList<>();
        // accWord.add(alphabet.getSymbol(0));
        // accWord.add(alphabet.getSymbol(0));
        // accWord.add(alphabet.getSymbol(0));
        TARGET_DFA.setAccepting(TARGET_DFA.getState(accWord), false);

        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(DFA_ORACLE, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learner = new ExtensibleILStarDFA<>(ALPHABET, memOracle, startingOT);
        int cexCounter = testLearnModel(TARGET_DFA, ALPHABET, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;
    }

    public void benchmark() throws IOException {
        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> classicLearner = learnClassic();
        writeDotFile(classicLearner.getHypothesisModel(), ALPHABET, "./classic.dot");

        GenericObservationTable<Symbol, Boolean> startingOT = new GenericObservationTable<>((GenericObservationTable<Symbol, Boolean>) classicLearner.getObservationTable());
        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> incLearner = learnIncremental(startingOT);
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
