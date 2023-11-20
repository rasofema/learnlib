package de.learnlib.algorithm.dlstar.dfa;

import java.util.Collections;
import java.util.List;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithm.dlstar.AbstractExtensibleAutomatonDLStar;
import de.learnlib.algorithm.lstar.AbstractExtensibleAutomatonLStar;
import de.learnlib.algorithm.lstar.ce.ObservationTableCEXHandler;
import de.learnlib.algorithm.lstar.closing.ClosingStrategy;
import de.learnlib.algorithm.lstar.dfa.LStarDFAUtil;
import de.learnlib.datastructure.observationtable.OTLearner.OTLearnerDFA;
import de.learnlib.oracle.MembershipOracle;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.concept.SuffixOutput;
import net.automatalib.automaton.fsa.CompactDFA;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.word.Word;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;

public class ExtensibleDLStarDFA<I>
        extends AbstractExtensibleAutomatonDLStar<DFA<?, I>, I, Boolean, Integer, Integer, Boolean, Void, CompactDFA<I>>
        implements OTLearnerDFA<I> {

    public ExtensibleDLStarDFA(Alphabet<I> alphabet, MembershipOracle<I, Boolean> oracle, List<Word<I>> initialSuffixes,
            ObservationTableCEXHandler<? super I, ? super Boolean> cexHandler,
            ClosingStrategy<? super I, ? super Boolean> closingStrategy) {
        this(alphabet, oracle, Collections.singletonList(Word.epsilon()), initialSuffixes, cexHandler, closingStrategy);
    }

    @GenerateBuilder(defaults = AbstractExtensibleAutomatonLStar.BuilderDefaults.class)
    public ExtensibleDLStarDFA(Alphabet<I> alphabet, MembershipOracle<I, Boolean> oracle, List<Word<I>> initialPrefixes,
            List<Word<I>> initialSuffixes, ObservationTableCEXHandler<? super I, ? super Boolean> cexHandler,
            ClosingStrategy<? super I, ? super Boolean> closingStrategy) {
        super(alphabet, oracle, new CompactDFA<>(alphabet), initialPrefixes,
                LStarDFAUtil.ensureSuffixCompliancy(initialSuffixes), cexHandler, closingStrategy);
    }

    @Override
    protected DFA<?, I> exposeInternalHypothesis() {
        return internalHyp;
    }

    @Override
    protected Boolean stateProperty(ObservationTable<I, Boolean> table, Row<I> stateRow) {
        return table.cellContents(stateRow, 0);
    }

    @Override
    protected Void transitionProperty(ObservationTable<I, Boolean> table, Row<I> stateRow, int inputIdx) {
        return null;
    }

    @Override
    protected SuffixOutput<I, Boolean> hypothesisOutput() {
        return internalHyp;
    }

}
