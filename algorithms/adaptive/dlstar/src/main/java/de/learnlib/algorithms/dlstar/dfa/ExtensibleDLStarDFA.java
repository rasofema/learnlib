package de.learnlib.algorithms.dlstar.dfa;

import java.util.Collections;
import java.util.List;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithms.dlstar.AbstractExtensibleAutomatonDLStar;
import de.learnlib.algorithms.lstar.AbstractExtensibleAutomatonLStar;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandler;
import de.learnlib.algorithms.lstar.closing.ClosingStrategy;
import de.learnlib.algorithms.lstar.dfa.LStarDFAUtil;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.datastructure.observationtable.OTLearner.OTLearnerDFA;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.automata.concepts.SuffixOutput;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class ExtensibleDLStarDFA<I>
    extends AbstractExtensibleAutomatonDLStar<DFA<?, I>, I, Boolean, Integer, Integer, Boolean, Void, CompactDFA<I>>
    implements OTLearnerDFA<I> {

    public ExtensibleDLStarDFA(Alphabet<I> alphabet,
                               MembershipOracle<I, Boolean> oracle,
                               List<Word<I>> initialSuffixes,
                               ObservationTableCEXHandler<? super I, ? super Boolean> cexHandler,
                               ClosingStrategy<? super I, ? super Boolean> closingStrategy) {
        this(alphabet, oracle, Collections.singletonList(Word.epsilon()), initialSuffixes, cexHandler, closingStrategy);
    }

    @GenerateBuilder(defaults = AbstractExtensibleAutomatonLStar.BuilderDefaults.class)
    public ExtensibleDLStarDFA(Alphabet<I> alphabet,
                               MembershipOracle<I, Boolean> oracle,
                               List<Word<I>> initialPrefixes,
                               List<Word<I>> initialSuffixes,
                               ObservationTableCEXHandler<? super I, ? super Boolean> cexHandler,
                               ClosingStrategy<? super I, ? super Boolean> closingStrategy) {
        super(alphabet,
            oracle,
            new CompactDFA<>(alphabet),
            initialPrefixes,
            LStarDFAUtil.ensureSuffixCompliancy(initialSuffixes),
            cexHandler,
            closingStrategy);
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
