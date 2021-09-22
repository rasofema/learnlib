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
package de.learnlib.algorithms.ilstar.dfa;

import java.util.List;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithms.ilstar.AbstractExtensibleAutomatonILStar;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandler;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithms.lstar.closing.ClosingStrategies;
import de.learnlib.algorithms.lstar.closing.ClosingStrategy;
import de.learnlib.algorithms.lstar.dfa.LStarDFAUtil;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import de.learnlib.datastructure.observationtable.OTLearner.OTLearnerDFA;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.automata.concepts.SuffixOutput;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * An implementation of Angluin's L* algorithm for learning DFAs, as described in the paper "Learning Regular Sets from
 * Queries and Counterexamples".
 *
 * @param <I>
 *         input symbol class.
 *
 * @author Malte Isberner
 */
public class ExtensibleILStarDFA<I>
    extends AbstractExtensibleAutomatonILStar<DFA<?, I>, I, Boolean, Integer, Integer, Boolean, Void, CompactDFA<I>>
    implements OTLearnerDFA<I> {

    public ExtensibleILStarDFA(Alphabet<I> alphabet, MembershipOracle<I, Boolean> oracle, GenericObservationTable<I, Boolean> startingOT) {
        this(alphabet, oracle, (List<Word<I>>) startingOT.getShortPrefixes(), startingOT.getSuffixes(), ObservationTableCEXHandlers.INCREMENTAL_LSTAR, ClosingStrategies.CLOSE_FIRST);
        this.table = startingOT;
    }

    @GenerateBuilder(defaults = AbstractExtensibleAutomatonILStar.BuilderDefaults.class)
    public ExtensibleILStarDFA(Alphabet<I> alphabet,
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
    public void startLearning() {
        updateInternalHypothesis();
    }

    @Override
    protected DFA<?, I> exposeInternalHypothesis() {
        return DFAs.minimize(internalHyp);
    }

    @Override
    protected Boolean stateProperty(ObservationTable<I, Boolean> table, Row<I, Boolean> stateRow) {
        return table.cellContents(stateRow, 0);
    }

    @Override
    protected Void transitionProperty(ObservationTable<I, Boolean> table, Row<I, Boolean> stateRow, int inputIdx) {
        return null;
    }

    @Override
    protected SuffixOutput<I, Boolean> hypothesisOutput() {
        return DFAs.minimize(internalHyp);
    }

}
