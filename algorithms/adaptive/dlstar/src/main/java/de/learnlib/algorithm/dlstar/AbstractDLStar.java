/* Copyright (C) 2018
 * This file is part of the PhD research project entitled
 * 'Inferring models from Evolving Systems and Product Families'
 * developed by Carlos Diego Nascimento Damasceno at the
 * University of Sao Paulo (ICMC-USP).
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
package de.learnlib.algorithm.dlstar;

import de.learnlib.algorithm.GlobalSuffixLearner;
import de.learnlib.algorithm.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.datastructure.observationtable.*;
import de.learnlib.datastructure.observationtable.writer.ObservationTableASCIIWriter;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.util.MQUtil;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.alphabet.SupportsGrowingAlphabet;
import net.automatalib.automaton.concept.SuffixOutput;
import net.automatalib.word.Word;

import java.io.IOException;
import java.util.*;

/**
 * An abstract base class for the extended version of the L*-style algorithms
 * named Dynamic L*.
 * <p>
 * This class implements basic management features (table, alphabet, oracle) and
 * the main loop of alternating completeness and consistency checks. It does not
 * take care of choosing how to initialize the table and hypothesis
 * construction. The main difference of it stands on the way that the
 * Observation Table is handled at the initialization.
 *
 * @param <A> automaton type
 * @param <I> input symbol type
 * @param <D> output domain type
 *
 * @author Carlos Diego Nascimento Damasceno (damascenodiego@usp.br)
 */
public abstract class AbstractDLStar<A, I, D>
        implements OTLearner<A, I, D>, GlobalSuffixLearner<A, I, D>, SupportsGrowingAlphabet<I> {

    protected final Alphabet<I> alphabet;
    protected final MembershipOracle<I, D> oracle;
    protected DynamicObservationTable<I, D> table;
    private boolean logObservationTable;

    /**
     * Constructor.
     *
     * @param alphabet the learning alphabet.
     * @param oracle   the membership oracle.
     */
    protected AbstractDLStar(Alphabet<I> alphabet, MembershipOracle<I, D> oracle) {
        this.alphabet = alphabet;
        this.oracle = oracle;
        this.table = new DynamicObservationTable<>(alphabet);
        this.logObservationTable = false;
    }

    @Override
    public void startLearning() {
        List<Word<I>> prefixes = initialPrefixes();
        List<Word<I>> suffixes = initialSuffixes();
        List<List<Row<I>>> initialUnclosed = table.initialize(prefixes, suffixes, oracle);

        completeConsistentTable(initialUnclosed, table.isInitialConsistencyCheckRequired());
        logObservationTable("startLearning");
    }

    @Override
    public final boolean refineHypothesis(DefaultQuery<I, D> ceQuery) {
        if (!MQUtil.isCounterexample(ceQuery, hypothesisOutput())) {
            return false;
        }
        int oldDistinctRows = table.numberOfDistinctRows();
        doRefineHypothesis(ceQuery);
        assert (table.numberOfDistinctRows() > oldDistinctRows);
        logObservationTable("refineHypothesis");
        return true;
    }

    protected abstract SuffixOutput<I, D> hypothesisOutput();

    protected void doRefineHypothesis(DefaultQuery<I, D> ceQuery) {
        List<List<Row<I>>> unclosed = incorporateCounterExample(ceQuery);
        completeConsistentTable(unclosed, true);
    }

    /**
     * Incorporates the information provided by a counterexample into the
     * observation data structure.
     *
     * @param ce the query which contradicts the hypothesis
     *
     * @return the rows (equivalence classes) which became unclosed by adding the
     *         information.
     */
    protected List<List<Row<I>>> incorporateCounterExample(DefaultQuery<I, D> ce) {
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

    protected boolean completeConsistentTable(List<List<Row<I>>> unclosed, boolean checkConsistency) {
        boolean refined = false;
        List<List<Row<I>>> unclosedIter = unclosed;
        do {
            while (!unclosedIter.isEmpty()) {
                List<Row<I>> closingRows = selectClosingRows(unclosedIter);
                unclosedIter = table.toShortPrefixes(closingRows, oracle);
                refined = true;
            }

            if (checkConsistency) {
                Inconsistency<I> incons;

                do {
                    incons = table.findInconsistency();
                    if (incons != null) {
                        Word<I> newSuffix = analyzeInconsistency(incons);
                        unclosedIter = table.addSuffix(newSuffix, oracle);
                    }
                } while (unclosedIter.isEmpty() && (incons != null));
            }
        } while (!unclosedIter.isEmpty());

        return refined;
    }

    /**
     * This method selects a set of rows to use for closing the table. It receives
     * as input a list of row lists, such that each (inner) list contains long
     * prefix rows with (currently) identical contents, which have no matching short
     * prefix row. The outer list is the list of all those equivalence classes.
     *
     * @param unclosed a list of equivalence classes of unclosed rows.
     *
     * @return a list containing a representative row from each class to move to the
     *         short prefix part.
     */
    protected List<Row<I>> selectClosingRows(List<List<Row<I>>> unclosed) {
        List<Row<I>> closingRows = new ArrayList<>(unclosed.size());

        for (List<Row<I>> rowList : unclosed) {
            closingRows.add(rowList.get(0));
        }

        return closingRows;
    }

    /**
     * Analyzes an inconsistency. This analysis consists in determining the column
     * in which the two successor rows differ.
     *
     * @param incons the inconsistency description
     *
     * @return the suffix to add in order to fix the inconsistency
     */
    protected Word<I> analyzeInconsistency(Inconsistency<I> incons) {
        int inputIdx = alphabet.getSymbolIndex(incons.getSymbol());

        Row<I> succRow1 = incons.getFirstRow().getSuccessor(inputIdx);
        Row<I> succRow2 = incons.getSecondRow().getSuccessor(inputIdx);

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

    @Override
    public Collection<Word<I>> getGlobalSuffixes() {
        return Collections.unmodifiableCollection(table.getSuffixes());
    }

    @Override
    public boolean addGlobalSuffixes(Collection<? extends Word<I>> newGlobalSuffixes) {
        List<List<Row<I>>> unclosed = table.addSuffixes(newGlobalSuffixes, oracle);
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
            this.alphabet.asGrowingAlphabetOrThrowException().addSymbol(symbol);
        }

        final List<List<Row<I>>> unclosed = this.table.addAlphabetSymbol(symbol, oracle);
        completeConsistentTable(unclosed, true);
    }

    protected void logObservationTable(String method_name) {
        if (this.logObservationTable) {
            StringBuffer sb = new StringBuffer();
            sb.append("\n");
            try {
                new ObservationTableASCIIWriter<>().write(getObservationTable(), sb);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void setLogObservationTable(boolean logOT) {
        this.logObservationTable = logOT;
    }
}
