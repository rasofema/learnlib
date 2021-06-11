import de.learnlib.examples.DefaultLearningExample;
import net.automatalib.automata.fsa.MutableDFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.builders.AutomatonBuilders;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.FastAlphabet;
import net.automatalib.words.impl.Symbol;

public class SimpleDFA extends DefaultLearningExample.DefaultDFALearningExample<Symbol> {
    public static final Symbol IN_ZERO = new Symbol("0");
    public static final Symbol OUT_ONE = new Symbol("1");

    public SimpleDFA() {
        super(constructMachine());
    }

    /**
     * Construct and return a machine representation of this example.
     *
     * @return machine instance of the example
     */
    public static CompactDFA<Symbol> constructMachine() {
        return constructMachine(new CompactDFA<>(createInputAlphabet()));
    }

    public static <S, T, A extends MutableDFA<S, ? super Symbol>> A constructMachine(A machine) {

        // @formatter:off
        return AutomatonBuilders.forDFA(machine)
            .withInitial("s0")
            .from("s0")
            .on(IN_ZERO).to("s1")
            .from("s1")
            .on(IN_ZERO).to("s2")
            .from("s2")
            .on(IN_ZERO).to("s3")
            .from("s3")
            .on(IN_ZERO).to("s4")
            .from("s4")
            .on(IN_ZERO).to("s5")
            .from("s5")
            .on(IN_ZERO).to("s0")
            .withAccepting("s0")
            .create();
        // @formatter:on
    }

    public static Alphabet<Symbol> createInputAlphabet() {
        return new FastAlphabet<>(IN_ZERO);
    }

    public static SimpleDFA createExample() {
        return new SimpleDFA();
    }

}
