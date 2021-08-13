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
package de.learnlib.algorithms.ilstar;

import de.learnlib.api.Resumable;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.datastructure.observationtable.RowContent;
import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.MutableDeterministic;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract base class for algorithms that produce (subclasses of) {@link MutableDeterministic} automata.
 * <p>
 * This class provides the L*-style hypothesis construction. Implementing classes solely have to specify how state and
 * transition properties should be derived.
 *
 * @param <A>
 *         automaton type, must be a subclass of {@link MutableDeterministic}
 * @param <I>
 *         input symbol type
 * @param <D>
 *         output domain type
 * @param <SP>
 *         state property type
 * @param <TP>
 *         transition property type
 *
 * @author Malte Isberner
 */
public abstract class AbstractAutomatonILStar<A, I, D, S, T, SP, TP, AI extends MutableDeterministic<S, I, T, SP, TP> & SupportsGrowingAlphabet<I>>
        extends AbstractILStar<A, I, D> implements Resumable<AutomatonILStarState<I, D, AI, S>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAutomatonILStar.class);

    protected AI internalHyp;
    protected Map<RowContent<I, D>, StateInfo<S, I, D>> stateInfos = new LinkedHashMap<>();

    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet
     * @param oracle
     *         the learning oracle
     */
    protected AbstractAutomatonILStar(Alphabet<I> alphabet, MembershipOracle<I, D> oracle, AI internalHyp) {
        super(alphabet, oracle);
        this.internalHyp = internalHyp;
        internalHyp.clear();
    }

    @Override
    public A getHypothesisModel() {
        return exposeInternalHypothesis();
    }

    protected abstract A exposeInternalHypothesis();

    @Override
    public void startLearning() {
        super.startLearning();
        updateInternalHypothesis();
    }

    /**
     * Performs the L*-style hypothesis construction. For creating states and transitions, the {@link
     * #stateProperty(ObservationTable, Row)} and {@link #transitionProperty(ObservationTable, Row, int)} methods are
     * used to derive the respective properties.
     */
    @SuppressWarnings("argument.type.incompatible")
    // all added nulls to stateInfos will be correctly set to non-null values
    protected void updateInternalHypothesis() {
        if (!table.isInitialized()) {
            throw new IllegalStateException("Cannot update internal hypothesis: not initialized");
        }

        internalHyp.clear();
        stateInfos.clear();

        // TODO: Is there a quicker way than iterating over *all* rows?
        // FIRST PASS: Create new hypothesis states
        for (Row<I, D> sp : table.getShortPrefixRows()) {
            if (!stateInfos.containsKey(sp.getRowContent())) {
                S state = createState(sp.getLabel().getClass() == Word.epsilon().getClass(), sp);
                stateInfos.put(sp.getRowContent(), new StateInfo<>(sp, state));
            }
        }

        // SECOND PASS: Create hypothesis transitions
        for (StateInfo<S, I, D> info : stateInfos.values()) {
            Row<I, D> sp = info.getRow();
            S state = info.getState();

            for (int i = 0; i < alphabet.size(); i++) {
                I input = alphabet.getSymbol(i);

                Row<I, D> succ = sp.getSuccessor(i);
                RowContent<I, D> succRowContent = succ.getRowContent();

                S succState = stateInfos.get(succRowContent).getState();

                setTransition(state, input, succState, sp, i);
            }
        }
    }

    /**
     * Derives a state property from the corresponding row.
     *
     * @param table
     *         the current observation table
     * @param stateRow
     *         the row for which the state is created
     *
     * @return the state property of the corresponding state
     */
    protected abstract SP stateProperty(ObservationTable<I, D> table, Row<I, D> stateRow);

    protected S createState(boolean initial, Row<I, D> row) {
        SP prop = stateProperty(table, row);
        if (initial) {
            return internalHyp.addInitialState(prop);
        }
        return internalHyp.addState(prop);
    }

    protected void setTransition(S from, I input, S to, Row<I, D> fromRow, int inputIdx) {
        TP prop = transitionProperty(table, fromRow, inputIdx);
        internalHyp.setTransition(from, input, to, prop);
    }

    /**
     * Derives a transition property from the corresponding transition.
     * <p>
     * N.B.: Not the transition row is passed to this method, but the row for the outgoing state. The transition row can
     * be retrieved using {@link Row#getSuccessor(int)}.
     *
     * @param stateRow
     *         the row for the source state
     * @param inputIdx
     *         the index of the input symbol to consider
     *
     * @return the transition property of the corresponding transition
     */
    protected abstract TP transitionProperty(ObservationTable<I, D> table, Row<I, D> stateRow, int inputIdx);

    @Override
    protected final void doRefineHypothesis(DefaultQuery<I, D> ceQuery) {
        refineHypothesisInternal(ceQuery);
        updateInternalHypothesis();
    }

    protected void refineHypothesisInternal(DefaultQuery<I, D> ceQuery) {
        super.doRefineHypothesis(ceQuery);
    }

    @Override
    public void addAlphabetSymbol(I symbol) {
        super.addAlphabetSymbol(symbol);

        this.internalHyp.addAlphabetSymbol(symbol);

        if (this.table.isInitialized()) {
            this.updateInternalHypothesis();
        }
    }

    @Override
    public AutomatonILStarState<I, D, AI, S> suspend() {
        return new AutomatonILStarState<>(table, internalHyp, stateInfos);
    }

    @Override
    public void resume(final AutomatonILStarState<I, D, AI, S> state) {
        this.table = state.getObservationTable();
        this.internalHyp = state.getHypothesis();
        this.stateInfos = state.getStateInfos();

        final Alphabet<I> oldAlphabet = this.table.getInputAlphabet();
        if (!oldAlphabet.equals(this.alphabet)) {
            LOGGER.warn(
                    "The current alphabet '{}' differs from the resumed alphabet '{}'. Future behavior may be inconsistent",
                    this.alphabet,
                    oldAlphabet);
        }
    }

    static final class StateInfo<S, I, D> {

        private final Row<I, D> row;
        private final S state;

        StateInfo(Row<I, D> row, S state) {
            this.row = row;
            this.state = state;
        }

        public Row<I, D> getRow() {
            return row;
        }

        public S getState() {
            return state;
        }

        // IDENTITY SEMANTICS!
    }
}
