package de.learnlib.algorithms.dlstar.dfa;

import java.util.Collections;
import java.util.List;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithms.lstar.closing.ClosingStrategies;
import de.learnlib.api.oracle.MembershipOracle;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class ClassicDLStarDFA<I> extends ExtensibleDLStarDFA<I> {

    @GenerateBuilder
    public ClassicDLStarDFA(Alphabet<I> alphabet, List<Word<I>> initialPrefixes, List<Word<I>> initialSuffixes, MembershipOracle<I, Boolean> oracle) {
        super(alphabet,
            oracle,
            initialPrefixes,
            initialSuffixes,
            ObservationTableCEXHandlers.CLASSIC_LSTAR,
            ClosingStrategies.CLOSE_FIRST);
    }
}
