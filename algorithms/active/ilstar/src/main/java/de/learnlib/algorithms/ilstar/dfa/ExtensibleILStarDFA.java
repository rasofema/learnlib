package de.learnlib.algorithms.ilstar.dfa;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithms.lstar.closing.ClosingStrategies;
import de.learnlib.algorithms.lstar.dfa.ExtensibleLStarDFA;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

import java.util.List;

public class ExtensibleILStarDFA<I> extends ExtensibleLStarDFA<I> {

    @GenerateBuilder
    public ExtensibleILStarDFA(Alphabet<I> alphabet, MembershipOracle<I, Boolean> oracle, GenericObservationTable<I, Boolean> startingOT) {
        super(alphabet, oracle, (List<Word<I>>) startingOT.getShortPrefixes(), startingOT.getSuffixes(), ObservationTableCEXHandlers.CLASSIC_LSTAR, ClosingStrategies.CLOSE_FIRST);
        this.table = startingOT;
    }

    @Override
    public void startLearning() {
        updateInternalHypothesis();
    }
}
