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

import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.api.algorithm.feature.GlobalSuffixLearner;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import de.learnlib.datastructure.observationtable.Inconsistency;
import de.learnlib.datastructure.observationtable.OTLearner;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.util.MQUtil;
import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.concepts.SuffixOutput;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * An abstract base class for L*-style algorithms.
 * <p>
 * This class implements basic management features (table, alphabet, oracle) and the main loop of alternating
 * completeness and consistency checks. It does not take care of choosing how to initialize the table and hypothesis
 * construction.
 *
 * @param <A>
 *         automaton type
 * @param <I>
 *         input symbol type
 * @param <D>
 *         output domain type
 *
 * @author Malte Isberner
 */
public abstract class AbstractILStar<A, I, D>
        implements OTLearner<A, I, D>, GlobalSuffixLearner<A, I, D>, SupportsGrowingAlphabet<I> {

    protected final Alphabet<I> alphabet;
    protected final MembershipOracle<I, D> oracle;
    protected GenericObservationTable<I, D> table;

    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet.
     * @param oracle
     *         the membership oracle.
     */
    protected AbstractILStar(Alphabet<I> alphabet, MembershipOracle<I, D> oracle) {
        this.alphabet = alphabet;
        this.oracle = oracle;
        this.table = new GenericObservationTable<>(alphabet);
    }

    @Override
    public void startLearning() {
        List<Word<I>> prefixes = initialPrefixes();
        List<Word<I>> suffixes = initialSuffixes();
        List<List<Row<I, D>>> initialUnclosed = table.initialize(prefixes, suffixes, oracle);

        completeConsistentTable(initialUnclosed, table.isInitialConsistencyCheckRequired());
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, D> ceQuery) {
        if (!MQUtil.isCounterexample(ceQuery, hypothesisOutput())) {
            return false;
        }
        doRefineHypothesis(ceQuery);
        // TODO: We are no longer guaranteed to always increase in number of rows.
        //  I'm sure this breaks a tonne of theorems.
        return true;
    }

    protected abstract SuffixOutput<I, D> hypothesisOutput();

    protected void doRefineHypothesis(DefaultQuery<I, D> ceQuery) {
        List<List<Row<I, D>>> unclosed = incorporateCounterExample(ceQuery);
        completeConsistentTable(unclosed, true);
    }

    /**
     * Incorporates the information provided by a counterexample into the observation data structure.
     *
     * @param ce
     *         the query which contradicts the hypothesis
     *
     * @return the rows (equivalence classes) which became unclosed by adding the information.
     */
    protected List<List<Row<I, D>>> incorporateCounterExample(DefaultQuery<I, D> ce) {
        return ObservationTableCEXHandlers.handleClassicLStar(ce, table, oracle);
    }

    protected List<Word<I>> initialPrefixes() {
        return Collections.singletonList(Word.epsilon());
    }

    /**
     * Returns the list of initial suffixes which are used to initialize the table.
     *
     * @return the list of initial suffixes.
     */
    protected abstract List<Word<I>> initialSuffixes();

    /**
     * Iteratedly checks for unclosedness and inconsistencies in the table, and fixes any occurrences thereof. This
     * process is repeated until the observation table is both closed and consistent.
     *
     * @param unclosed
     *         the unclosed rows (equivalence classes) to start with.
     */
    protected boolean completeConsistentTable(List<List<Row<I, D>>> unclosed, boolean checkConsistency) {
        boolean refined = false;
        List<List<Row<I, D>>> unclosedIter = unclosed;
        // TODO: Something here isn't terminating,
        //  I think it's due to addSuffix not adding already present suffixes.
        //  And why is it adding a present suffix? Is it an indication of
        //  incorrect cell values?
        do {
            while (!unclosedIter.isEmpty()) {
                List<Row<I, D>> closingRows = selectClosingRows(unclosedIter);
                unclosedIter = table.toShortPrefixes(closingRows, oracle);
                refined = true;
            }

            if (checkConsistency) {
                Inconsistency<I, D> incons;

                do {
                    incons = table.findInconsistency();
                    incons = verifyInconsistency(incons);
                    if (incons != null) {

                        Word<I> newSuffix = analyzeInconsistency(incons);
                        unclosedIter = table.addSuffix(newSuffix, oracle);
                    }
                } while (unclosedIter.isEmpty() && incons != null);
            }
        } while (!unclosedIter.isEmpty());

        return refined;
    }

    /**
     * This method selects a set of rows to use for closing the table. It receives as input a list of row lists, such
     * that each (inner) list contains long prefix rows with (currently) identical contents, which have no matching
     * short prefix row. The outer list is the list of all those equivalence classes.
     *
     * @param unclosed
     *         a list of equivalence classes of unclosed rows.
     *
     * @return a list containing a representative row from each class to move to the short prefix part.
     */
    protected List<Row<I, D>> selectClosingRows(List<List<Row<I, D>>> unclosed) {
        List<Row<I, D>> closingRows = new ArrayList<>(unclosed.size());

        for (List<Row<I, D>> rowList : unclosed) {
            closingRows.add(rowList.get(0));
        }

        return closingRows;
    }

    /**
     * Analyzes an inconsistency. This analysis consists in determining the column in which the two successor rows
     * differ.
     *
     * @param incons
     *         the inconsistency description
     *
     * @return the suffix to add in order to fix the inconsistency
     */
    protected Word<I> analyzeInconsistency(Inconsistency<I, D> incons) {
        int inputIdx = alphabet.getSymbolIndex(incons.getSymbol());

        Row<I, D> succRow1 = incons.getFirstRow().getSuccessor(inputIdx);
        Row<I, D> succRow2 = incons.getSecondRow().getSuccessor(inputIdx);

        int numSuffixes = table.getSuffixes().size();

        for (int i = 0; i < numSuffixes; i++) {
            D val1 = table.cellContents(succRow1, i), val2 = table.cellContents(succRow2, i);
            if (!Objects.equals(val1, val2)) {
                I sym = alphabet.getSymbol(inputIdx);
                Word<I> suffix = table.getSuffixes().get(i);
                return suffix.prepend(sym);
            }
        }

        throw new IllegalArgumentException("Bogus inconsistency");
    }

    protected Inconsistency<I, D> verifyInconsistency(Inconsistency<I, D> incons) {
        if (incons == null) {
            return null;
        }

        int inputIdx = alphabet.getSymbolIndex(incons.getSymbol());
        List<Row<I, D>> rowsToVerify = new LinkedList<>();
        rowsToVerify.add(incons.getFirstRow());
        rowsToVerify.add(incons.getSecondRow());
        rowsToVerify.add(incons.getFirstRow().getSuccessor(inputIdx));
        rowsToVerify.add(incons.getSecondRow().getSuccessor(inputIdx));

        for (Row<I, D> row : rowsToVerify) {
            for (int suffIndex = 0; suffIndex < table.getSuffixes().size(); suffIndex++) {
                D correctValue = oracle.answerQuery(row.getLabel(), table.getSuffix(suffIndex));
                if (!table.cellContents(row, suffIndex).equals(correctValue)) {
                    table.correctCell(row.getLabel(), table.getSuffix(suffIndex), correctValue);
                    return null;
                }
            }
        }
        return incons;
    }

    @Override
    public Collection<Word<I>> getGlobalSuffixes() {
        return Collections.unmodifiableCollection(table.getSuffixes());
    }

    @Override
    public boolean addGlobalSuffixes(Collection<? extends Word<I>> newGlobalSuffixes) {
        List<List<Row<I, D>>> unclosed = table.addSuffixes(newGlobalSuffixes, oracle);
        if (unclosed.isEmpty()) {
            return false;
        }
        return completeConsistentTable(unclosed, false);
    }

    @Override
    public ObservationTable<I, D> getObservationTable() {
        return table;
    }

    @Override
    public void addAlphabetSymbol(I symbol) {

        if (!this.alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(this.alphabet).addSymbol(symbol);
        }

        final List<List<Row<I, D>>> unclosed = this.table.addAlphabetSymbol(symbol, oracle);
        completeConsistentTable(unclosed, true);
    }
}
