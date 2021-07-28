import de.learnlib.algorithms.ilstar.dfa.ExtensibleILStarDFA;
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFA;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import de.learnlib.datastructure.observationtable.OTLearner;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.equivalence.WpMethodEQOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import net.automatalib.automata.Automaton;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.MutableDFA;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Symbol;
import org.testng.Assert;
import org.testng.annotations.Test;

import net.automatalib.serialization.dot.GraphDOT;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

@Test
public class LearningBenchmark {
    private static final MutableDFA<Integer, Symbol> targetDFA = SimpleDFA.constructMachine();
    private static final Alphabet<Symbol> alphabet = SimpleDFA.createInputAlphabet();
    private static final MembershipOracle.DFAMembershipOracle<Symbol> dfaOracle = new SimulatorOracle.DFASimulatorOracle<>(targetDFA);

    public static int testLearnModel(DFA<?, Symbol> target, Alphabet<Symbol> alphabet,
        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learner,
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle) {
        int maxRounds = target.size();
        int cexCounter = 0;

        learner.startLearning();

        while (maxRounds-- > 0) {
            DFA<?, Symbol> hyp = learner.getHypothesisModel();

            DefaultQuery<Symbol, Boolean> ce = eqOracle.findCounterExample(hyp, alphabet);
            cexCounter++;

            if (ce == null) {
                break;
            }

//            Assert.assertNotEquals(maxRounds, 0);

            learner.refineHypothesis(ce);
        }

        DFA<?, Symbol> hyp = learner.getHypothesisModel();

        Assert.assertEquals(hyp.size(), target.size());
        return cexCounter;
    }

    public OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learnClassic() {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(dfaOracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        ClassicLStarDFA<Symbol> learner = new ClassicLStarDFA<>(alphabet, memOracle);

        int cexCounter = testLearnModel(targetDFA, alphabet, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;

    }

    public OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learnIncremental(GenericObservationTable<Symbol, Boolean> startingOT) {
        LinkedList<Symbol> accWord = new LinkedList<>();
        accWord.add(alphabet.getSymbol(0));
        accWord.add(alphabet.getSymbol(0));
        accWord.add(alphabet.getSymbol(0));
        targetDFA.setAccepting(targetDFA.getState(accWord), true);

        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(dfaOracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learner = new ExtensibleILStarDFA<>(alphabet, memOracle, startingOT);
        int cexCounter = testLearnModel(targetDFA, alphabet, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        return learner;
    }

    public void benchmark() throws IOException {
        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> classicLearner = learnClassic();
        writeDotFile(classicLearner.getHypothesisModel(), alphabet, "./classic.dot");

        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> incLearner = learnIncremental((GenericObservationTable<Symbol, Boolean>) classicLearner.getObservationTable());
        writeDotFile(incLearner.getHypothesisModel(), alphabet, "./incremental.dot");
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
