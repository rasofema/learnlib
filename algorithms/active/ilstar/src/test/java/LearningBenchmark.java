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
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Symbol;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class LearningBenchmark {
    private static final SimpleDFA example = new SimpleDFA();
    private static final DFA<?, Symbol> targetDFA = example.getReferenceAutomaton();
    private static final Alphabet<Symbol> alphabet = example.getAlphabet();
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

            Assert.assertNotEquals(maxRounds, 0);

            learner.refineHypothesis(ce);
        }

        DFA<?, Symbol> hyp = learner.getHypothesisModel();

        Assert.assertEquals(hyp.size(), target.size());
        return cexCounter;
    }

    public ObservationTable<Symbol, Boolean> learnClassic() {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(dfaOracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        ClassicLStarDFA<Symbol> learner = new ClassicLStarDFA<>(alphabet, memOracle);

        int cexCounter = testLearnModel(targetDFA, alphabet, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        System.out.println("Table:\n" + learner.getObservationTable().toString());
        return learner.getObservationTable();

    }

    public ObservationTable<Symbol, Boolean> learnIncremental(GenericObservationTable<Symbol, Boolean> startingOT) {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(dfaOracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");
        EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle = new WpMethodEQOracle<>(queryOracle, 4);

        OTLearner<? extends DFA<?, Symbol>, Symbol, Boolean> learner = new ExtensibleILStarDFA<>(alphabet, memOracle, startingOT);
        int cexCounter = testLearnModel(targetDFA, alphabet, learner, eqOracle);

        System.out.println("Number of total queries: " + queryOracle.getCount());
        System.out.println("Number of membership queries: " + memOracle.getCount());
        System.out.println("Number of equivalence queries: " + cexCounter);
        System.out.println("Number of queries used in equivalence: " + (queryOracle.getCount() - memOracle.getCount()));
        System.out.println("Table:\n" + learner.getObservationTable().toString());
        return learner.getObservationTable();
    }

    public void benchmark() {
        ObservationTable<Symbol, Boolean> ot = learnClassic();
        learnIncremental((GenericObservationTable<Symbol, Boolean>) ot);
    }
}
