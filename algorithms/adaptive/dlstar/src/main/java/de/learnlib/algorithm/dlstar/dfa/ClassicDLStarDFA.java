package de.learnlib.algorithm.dlstar.dfa;

import java.util.List;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithm.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithm.lstar.closing.ClosingStrategies;
import de.learnlib.oracle.MembershipOracle;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.word.Word;

public class ClassicDLStarDFA<I> extends ExtensibleDLStarDFA<I> {

    @GenerateBuilder
    public ClassicDLStarDFA(Alphabet<I> alphabet, List<Word<I>> initialPrefixes, List<Word<I>> initialSuffixes,
            MembershipOracle<I, Boolean> oracle) {
        super(alphabet, oracle, initialPrefixes, initialSuffixes, ObservationTableCEXHandlers.CLASSIC_LSTAR,
                ClosingStrategies.CLOSE_FIRST);
    }
}
