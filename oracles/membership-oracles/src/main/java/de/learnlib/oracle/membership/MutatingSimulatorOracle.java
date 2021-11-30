package de.learnlib.oracle.membership;

import java.util.List;

import net.automatalib.automata.concepts.SuffixOutput;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.Word;

public class MutatingSimulatorOracle<I, D> extends SimulatorOracle<I, D> {
    private final List<? extends SuffixOutput<I, D>> automata;
    private int pointer = 0;
    private int counter  = 0;
    private final int limit;

    public MutatingSimulatorOracle(int limit, List<? extends SuffixOutput<I, D>> automata) {
        super(automata.get(0));
        this.automata = automata;
        this.limit = limit;

    }

    @Override
    public D answerQuery(Word<I> prefix, Word<I> suffix) {
        counter++;
        if (counter == limit) {
            counter = 0;
            if (pointer != automata.size() - 1) {
                pointer++;
                automaton = automata.get(pointer);
            }
        }
        return super.answerQuery(prefix, suffix);
    }

    public SuffixOutput<I, D> getTarget(int n) {
        return automata.get((n - 1) / limit);
    }

    public static class DFAMutatingSimulatorOracle<I> extends MutatingSimulatorOracle<I, Boolean> implements SingleQueryOracleDFA<I> {

        public DFAMutatingSimulatorOracle(int limit, List<? extends DFA<?, I>> automata) {
            super(limit, automata);
        }
    }

}
