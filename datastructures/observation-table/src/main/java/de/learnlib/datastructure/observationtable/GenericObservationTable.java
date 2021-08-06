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
package de.learnlib.datastructure.observationtable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * Observation table class.
 * <p>
 * An observation table (OT) is the central data structure used by Angluin's L* algorithm, as described in the paper
 * "Learning Regular Sets from Queries and Counterexamples".
 * <p>
 * An observation table is a two-dimensional table, with rows indexed by prefixes, and columns indexed by suffixes. For
 * a prefix <code>u</code> and a suffix <code>v</code>, the respective cell contains the result of the membership query
 * <code>(u, v)</code>.
 * <p>
 * The set of prefixes (row labels) is divided into two disjoint sets: short and long prefixes. Each long prefix is a
 * one-letter extension of a short prefix; conversely, every time a prefix is added to the set of short prefixes, all
 * possible one-letter extensions are added to the set of long prefixes.
 * <p>
 * In order to derive a well-defined hypothesis from an observation table, it must satisfy two properties: closedness
 * and consistency. <ul> <li>An observation table is <b>closed</b> iff for each long prefix <code>u</code> there exists
 * a short prefix <code>u'</code> such that the row contents for both prefixes are equal. <li>An observation table is
 * <b>consistent</b> iff for every two short prefixes <code>u</code> and <code>u'</code> with identical row contents,
 * it holds that for every input symbol <code>a</code> the rows indexed by <code>ua</code> and <code>u'a</code> also
 * have identical contents. </ul>
 *
 * @param <I>
 *         input symbol type
 * @param <D>
 *         output domain type
 *
 * @author Malte Isberner
 */
public final class GenericObservationTable<I, D> implements MutableObservationTable<I, D> {
    private final List<RowImpl<I, D>> shortPrefixRows = new ArrayList<>();
    private final List<RowImpl<I, D>> longPrefixRows = new ArrayList<>();
    private final List<RowImpl<I, D>> allRows = new ArrayList<>();
    private final Map<RowContent<I, D>, RowImpl<I, D>> canonicalRows = new HashMap<>();
    private final Map<List<D>, RowContent<I, D>> contentToRowContent = new HashMap<>();
    private final Map<Word<I>, RowImpl<I, D>> rowMap = new HashMap<>();
    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixSet = new HashSet<>();
    private final Alphabet<I> alphabet;
    private int alphabetSize;
    private int numRows;
    private boolean initialConsistencyCheckRequired;

    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet.
     */
    public GenericObservationTable(Alphabet<I> alphabet) {
        this.alphabet = alphabet;
        this.alphabetSize = alphabet.size();
    }

    private static <I, D> void buildQueries(List<DefaultQuery<I, D>> queryList,
                                            Word<I> prefix,
                                            List<? extends Word<I>> suffixes) {
        for (Word<I> suffix : suffixes) {
            queryList.add(new DefaultQuery<>(prefix, suffix));
        }
    }

    @Override
    public List<List<Row<I, D>>> initialize(List<Word<I>> initialShortPrefixes,
                                         List<Word<I>> initialSuffixes,
                                         MembershipOracle<I, D> oracle) {
        if (!allRows.isEmpty()) {
            throw new IllegalStateException("Called initialize, but there are already rows present");
        }

        if (!checkPrefixClosed(initialShortPrefixes)) {
            throw new IllegalArgumentException("Initial short prefixes are not prefix-closed");
        }

        if (!initialShortPrefixes.get(0).isEmpty()) {
            throw new IllegalArgumentException("First initial short prefix MUST be the empty word!");
        }

        int numSuffixes = initialSuffixes.size();
        for (Word<I> suffix : initialSuffixes) {
            if (suffixSet.add(suffix)) {
                suffixes.add(suffix);
            }
        }

        int numPrefixes = alphabet.size() * initialShortPrefixes.size() + 1;

        List<DefaultQuery<I, D>> queries = new ArrayList<>(numPrefixes * numSuffixes);

        // PASS 1: Add short prefix rows
        for (Word<I> sp : initialShortPrefixes) {
            createSpRow(sp);
            buildQueries(queries, sp, suffixes);
        }

        // PASS 2: Add missing long prefix rows
        for (RowImpl<I, D> spRow : shortPrefixRows) {
            Word<I> sp = spRow.getLabel();
            for (int i = 0; i < alphabet.size(); i++) {
                I sym = alphabet.getSymbol(i);
                Word<I> lp = sp.append(sym);
                RowImpl<I, D> succRow = rowMap.get(lp);
                if (succRow == null) {
                    succRow = createLpRow(lp);
                    buildQueries(queries, lp, suffixes);
                }
                spRow.setSuccessor(i, succRow);
            }
        }

        oracle.processQueries(queries);

        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        for (RowImpl<I, D> spRow : shortPrefixRows) {
            List<D> rowContents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, rowContents, numSuffixes);
            if (!processContents(spRow, rowContents, true)) {
                initialConsistencyCheckRequired = true;
            }
        }

        Set<RowContent<I, D>> unclosed = new LinkedHashSet<>();

        for (RowImpl<I, D> spRow : shortPrefixRows) {
            for (int i = 0; i < alphabet.size(); i++) {
                RowImpl<I, D> succRow = spRow.getSuccessor(i);
                if (succRow.isShortPrefixRow()) {
                    continue;
                }
                List<D> rowContents = new ArrayList<>(numSuffixes);
                fetchResults(queryIt, rowContents, numSuffixes);
                processContents(succRow, rowContents, false);

                Set<RowContent<I, D>> spRowContents = shortPrefixRows.stream()
                    .map(RowImpl::getRowContent)
                    .collect(Collectors.toSet());

                if (!spRowContents.contains(succRow.getRowContent())) {
                    unclosed.add(succRow.getRowContent());
                }
            }
        }

        // Get the set of unclosed row contents,
        return unclosed.stream()
            // Map them to the associated rows,
            .map(con -> con.getAssociatedRows().stream()
                // Keep only the long rows,
                .filter(row -> !row.isShortPrefixRow())
                // Collect them into lists,
                .collect(Collectors.toList()))
            // Collect this stream of lists into a list.
            .collect(Collectors.toList());
    }

    private static <I> boolean checkPrefixClosed(Collection<? extends Word<I>> initialShortPrefixes) {
        Set<Word<I>> prefixes = new HashSet<>(initialShortPrefixes);

        for (Word<I> pref : initialShortPrefixes) {
            if (!pref.isEmpty() && !prefixes.contains(pref.prefix(-1))) {
                return false;
            }
        }

        return true;
    }

    private RowImpl<I, D> createSpRow(Word<I> prefix) {
        RowImpl<I, D> newRow = new RowImpl<>(prefix, numRows++, alphabet.size());
        allRows.add(newRow);
        rowMap.put(prefix, newRow);
        shortPrefixRows.add(newRow);
        return newRow;
    }

    private RowImpl<I, D> createLpRow(Word<I> prefix) {
        RowImpl<I, D> newRow = new RowImpl<>(prefix, numRows++);
        allRows.add(newRow);
        rowMap.put(prefix, newRow);
        int idx = longPrefixRows.size();
        longPrefixRows.add(newRow);
        newRow.setLpIndex(idx);
        return newRow;
    }

    /**
     * Fetches the given number of query responses and adds them to the specified output list. Also, the query iterator
     * is advanced accordingly.
     *
     * @param queryIt
     *         the query iterator
     * @param output
     *         the output list to write to
     * @param numSuffixes
     *         the number of suffixes (queries)
     */
    private static <I, D> void fetchResults(Iterator<DefaultQuery<I, D>> queryIt, List<D> output, int numSuffixes) {
        for (int j = 0; j < numSuffixes; j++) {
            DefaultQuery<I, D> qry = queryIt.next();
            output.add(qry.getOutput());
        }
    }

    private boolean processContents(RowImpl<I, D> row, List<D> rowContents, boolean makeCanonical) {
        boolean added = false;
        RowContent<I, D> oldRowContent = row.getRowContent();
        RowContent<I, D> rowContent = contentToRowContent.getOrDefault(rowContents, null);
        if (rowContent == null) {
            rowContent = new RowContent<>(rowContents);
            contentToRowContent.put(rowContents, rowContent);
            if (makeCanonical) {
                canonicalRows.put(rowContent, row);
            }
            added = true;
        }

        row.setRowContent(rowContent);
        rowContent.addAssociatedRow(row);

        if (oldRowContent != null) {
            oldRowContent.removeAssociatedRow(row);
            if (oldRowContent.getAssociatedRows().size() == 0) {
                contentToRowContent.remove(oldRowContent.getContents());
                canonicalRows.remove(oldRowContent);
            }
        }

        return added;
    }

    @Override
    public int numberOfDistinctRows() {
        return contentToRowContent.size();
    }

    @Override
    public List<List<Row<I, D>>> addSuffix(Word<I> suffix, MembershipOracle<I, D> oracle) {
        return addSuffixes(Collections.singletonList(suffix), oracle);
    }

    @Override
    public List<List<Row<I, D>>> addSuffixes(Collection<? extends Word<I>> newSuffixes, MembershipOracle<I, D> oracle) {
        // we need a stable iteration order, and only List guarantees this
        List<Word<I>> newSuffixList = new ArrayList<>();
        for (Word<I> suffix : newSuffixes) {
            if (suffixSet.add(suffix)) {
                newSuffixList.add(suffix);
            }
        }

        if (newSuffixList.isEmpty()) {
            return Collections.emptyList();
        }

        int numNewSuffixes = newSuffixList.size();

        int rowCount = allRows.size();
        List<DefaultQuery<I, D>> queries = new ArrayList<>(rowCount * numNewSuffixes);

        for (RowImpl<I, D> row : allRows) {
            buildQueries(queries, row.getLabel(), newSuffixList);
        }

        oracle.processQueries(queries);

        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();
        int oldSuffixCount = suffixes.size();
        this.suffixes.addAll(newSuffixList);

        for (RowImpl<I, D> row : allRows) {
            RowContent<I, D> rowContent = row.getRowContent();
            List<D> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
            if (rowContent != null) {
                newContents.addAll(rowContent.getContents().subList(0, oldSuffixCount));
            }
            fetchResults(queryIt, newContents, numNewSuffixes);
            processContents(row, newContents, row.isShortPrefixRow());
        }

        Set<RowContent<I, D>> unclosed = new LinkedHashSet<>();

        for (RowImpl<I, D> spRow : shortPrefixRows) {
            for (int i = 0; i < alphabet.size(); i++) {
                RowImpl<I, D> succRow = spRow.getSuccessor(i);
                if (succRow.isShortPrefixRow()) {
                    continue;
                }

                Set<RowContent<I, D>> spRowContents = shortPrefixRows.stream()
                    .map(RowImpl::getRowContent)
                    .collect(Collectors.toSet());

                if (!spRowContents.contains(succRow.getRowContent())) {
                    unclosed.add(succRow.getRowContent());
                }
            }
        }

        // Get the set of unclosed row contents,
        return unclosed.stream()
            // Map them to the associated rows,
            .map(con -> con.getAssociatedRows().stream()
                // Keep only the long rows,
                .filter(row -> !row.isShortPrefixRow())
                // Collect them into lists,
                .collect(Collectors.toList()))
            // Collect this stream of lists into a list.
            .collect(Collectors.toList());
    }

    @Override
    public boolean isInitialConsistencyCheckRequired() {
        return initialConsistencyCheckRequired;
    }

    @Override
    public List<List<Row<I, D>>> addShortPrefixes(List<? extends Word<I>> shortPrefixes, MembershipOracle<I, D> oracle) {
        List<Row<I, D>> toSpRows = new ArrayList<>();

        for (Word<I> sp : shortPrefixes) {
            RowImpl<I, D> row = rowMap.get(sp);
            if (row != null) {
                if (row.isShortPrefixRow()) {
                    continue;
                }
            } else {
                row = createSpRow(sp);
            }
            toSpRows.add(row);
        }

        return toShortPrefixes(toSpRows, oracle);
    }

    @Override
    public List<List<Row<I, D>>> toShortPrefixes(List<Row<I, D>> lpRows, MembershipOracle<I, D> oracle) {
        List<RowImpl<I, D>> freshSpRows = new ArrayList<>();
        List<RowImpl<I, D>> freshLpRows = new ArrayList<>();

        for (Row<I, D> r : lpRows) {
            final RowImpl<I, D> row = allRows.get(r.getRowId());
            if (row.isShortPrefixRow()) {
                if (row.hasContents()) {
                    continue;
                }
                freshSpRows.add(row);
            } else {
                makeShort(row);
                if (!row.hasContents()) {
                    freshSpRows.add(row);
                }
            }

            Word<I> prefix = row.getLabel();

            for (int i = 0; i < alphabet.size(); i++) {
                I sym = alphabet.getSymbol(i);
                Word<I> lp = prefix.append(sym);
                RowImpl<I, D> lpRow = rowMap.get(lp);
                if (lpRow == null) {
                    lpRow = createLpRow(lp);
                    freshLpRows.add(lpRow);
                }
                row.setSuccessor(i, lpRow);
            }
        }

        int numSuffixes = suffixes.size();

        int numFreshRows = freshSpRows.size() + freshLpRows.size();
        List<DefaultQuery<I, D>> queries = new ArrayList<>(numFreshRows * numSuffixes);
        buildRowQueries(queries, freshSpRows, suffixes);
        buildRowQueries(queries, freshLpRows, suffixes);

        oracle.processQueries(queries);
        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        for (RowImpl<I, D> row : freshSpRows) {
            List<D> contents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, contents, numSuffixes);
            processContents(row, contents, true);
        }

        Set<RowContent<I, D>> unclosed = new LinkedHashSet<>();

        for (RowImpl<I, D> row : freshLpRows) {
            List<D> contents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, contents, numSuffixes);
            processContents(row, contents, false);

            Set<RowContent<I, D>> spRowContents = shortPrefixRows.stream()
                .map(RowImpl::getRowContent)
                .collect(Collectors.toSet());

            if (!spRowContents.contains(row.getRowContent())) {
                unclosed.add(row.getRowContent());
            }
        }

        // Get the set of unclosed row contents,
        return unclosed.stream()
            // Map them to the associated rows,
            .map(con -> con.getAssociatedRows().stream()
                // Keep only the long rows,
                .filter(row -> !row.isShortPrefixRow())
                // Collect them into lists,
                .collect(Collectors.toList()))
            // Collect this stream of lists into a list.
            .collect(Collectors.toList());
    }

    @Override
    public List<List<Row<I, D>>> correctCell(Word<I> prefix, Word<I> suffix, D correctValue) {
        RowImpl<I, D> incorrectRow = rowMap.get(prefix);
        List<D> correctedContents = new LinkedList<>();
        RowContent<I, D> rowContent = incorrectRow.getRowContent();
        if (rowContent != null) {
            correctedContents.addAll(rowContent.getContents());
        }
        correctedContents.set(suffixes.indexOf(suffix), correctValue);
        processContents(incorrectRow, correctedContents, incorrectRow.isShortPrefixRow());

        // TODO: Correcting cells affects closedness - and maybe consistency.
        //  Unclosed EQ classes need to be reported back to the algo.
        Set<RowContent<I, D>> spRowContents = shortPrefixRows.stream()
            .map(RowImpl::getRowContent)
            .collect(Collectors.toSet());

        Set<RowContent<I, D>> unclosed = longPrefixRows.stream()
            .filter(row -> !spRowContents.contains(row.getRowContent()))
            .map(RowImpl::getRowContent)
            .collect(Collectors.toSet());

        // Get the set of unclosed row contents,
        return unclosed.stream()
            // Map them to the associated rows,
            .map(con -> con.getAssociatedRows().stream()
                // Keep only the long rows,
                .filter(row -> !row.isShortPrefixRow())
                // Collect them into lists,
                .collect(Collectors.toList()))
            // Collect this stream of lists into a list.
            .collect(Collectors.toList());
    }

    private void makeShort(RowImpl<I, D> row) {
        if (row.isShortPrefixRow()) {
            return;
        }

        int lastIdx = longPrefixRows.size() - 1;
        RowImpl<I, D> last = longPrefixRows.get(lastIdx);
        int rowIdx = row.getLpIndex();
        longPrefixRows.remove(lastIdx);
        if (last != row) {
            longPrefixRows.set(rowIdx, last);
            last.setLpIndex(rowIdx);
        }

        shortPrefixRows.add(row);
        row.makeShort(alphabet.size());
    }

    private static <I, D> void buildRowQueries(List<DefaultQuery<I, D>> queryList,
                                               List<? extends Row<I, D>> rows,
                                               List<? extends Word<I>> suffixes) {
        for (Row<I, D> row : rows) {
            buildQueries(queryList, row.getLabel(), suffixes);
        }
    }

    @Override
    public RowImpl<I, D> getRow(int rowId) {
        return allRows.get(rowId);
    }

    @Override
    public int numberOfRows() {
        return shortPrefixRows.size() + longPrefixRows.size();
    }

    @Override
    public List<Word<I>> getSuffixes() {
        return suffixes;
    }

    @Override
    public boolean isInitialized() {
        return !allRows.isEmpty();
    }

    @Override
    public Alphabet<I> getInputAlphabet() {
        return alphabet;
    }

    @Override
    public Word<I> transformAccessSequence(Word<I> word) {
        Row<I, D> current = rowMap.get(Word.epsilon());
        for (I sym : word) {
            current = getRowSuccessor(current, sym);
            RowContent<I, D> currentContent = current.getRowContent();
            current = shortPrefixRows.stream()
                .filter(row -> row.getRowContent() == currentContent)
                .findFirst()
                .orElse(null);
            assert current != null;
        }

        return current.getLabel();
    }

    @Override
    public boolean isAccessSequence(Word<I> word) {
        Row<I, D> current = rowMap.get(Word.epsilon());

        for (I sym : word) {
            current = getRowSuccessor(current, sym);
            if (!isCanonical(current)) {
                return false;
            }
        }

        return true;
    }

    private boolean isCanonical(Row<I, D> row) {
        if (!row.isShortPrefixRow()) {
            return false;
        }
        return canonicalRows.get(row.getRowContent()) == row;
    }

    @Override
    public List<List<Row<I, D>>> addAlphabetSymbol(I symbol, final MembershipOracle<I, D> oracle) {

        if (!alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(alphabet).addSymbol(symbol);
        }

        final int newAlphabetSize = alphabet.size();

        if (this.isInitialized() && this.alphabetSize < newAlphabetSize) {
            this.alphabetSize = newAlphabetSize;
            final int newSymbolIdx = alphabet.getSymbolIndex(symbol);

            final List<RowImpl<I, D>> shortPrefixes = shortPrefixRows;
            final List<RowImpl<I, D>> newLongPrefixes = new ArrayList<>(shortPrefixes.size());

            for (RowImpl<I, D> prefix : shortPrefixes) {
                prefix.ensureInputCapacity(newAlphabetSize);

                final Word<I> newLongPrefix = prefix.getLabel().append(symbol);
                final RowImpl<I, D> longPrefixRow = createLpRow(newLongPrefix);

                newLongPrefixes.add(longPrefixRow);
                prefix.setSuccessor(newSymbolIdx, longPrefixRow);
            }

            final int numLongPrefixes = newLongPrefixes.size();
            final int numSuffixes = this.numberOfSuffixes();
            final List<DefaultQuery<I, D>> queries = new ArrayList<>(numLongPrefixes * numSuffixes);

            buildRowQueries(queries, newLongPrefixes, getSuffixes());
            oracle.processQueries(queries);

            final Iterator<DefaultQuery<I, D>> queryIterator = queries.iterator();
            final List<List<Row<I, D>>> result = new ArrayList<>(numLongPrefixes);

            for (RowImpl<I, D> row : newLongPrefixes) {
                final List<D> contents = new ArrayList<>(numSuffixes);

                fetchResults(queryIterator, contents, numSuffixes);

                if (processContents(row, contents, false)) {
                    result.add(Collections.singletonList(row));
                }
            }
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Row<I, D>> getShortPrefixRows() {
        return Collections.unmodifiableList(shortPrefixRows);
    }

    @Override
    public Collection<Row<I, D>> getLongPrefixRows() {
        return Collections.unmodifiableList(longPrefixRows);
    }
}
