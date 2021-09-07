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
import java.util.Comparator;
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

    public GenericObservationTable(GenericObservationTable<I, D> table) {
        this.alphabet = table.alphabet;
        this.alphabetSize = table.alphabetSize;
        this.suffixes.addAll(table.suffixes);
        this.suffixSet.addAll(table.suffixSet);

        Map<RowContent<I, D>, RowContent<I, D>> oldContentToNewContent = new HashMap<>();
        Map<RowImpl<I, D>, RowImpl<I, D>> oldRowToNewRow = new HashMap<>();
        for (Map.Entry<List<D>, RowContent<I, D>> entry : table.contentToRowContent.entrySet()) {
            RowContent<I, D> newContent = new RowContent<>(entry.getKey());
            this.contentToRowContent.put(entry.getKey(), newContent);
            oldContentToNewContent.put(entry.getValue(), newContent);
        }

        for (RowImpl<I, D> row : table.allRows) {
            RowImpl<I, D> newRow;
            if (row.isShortPrefixRow()) {
                newRow = new RowImpl<>(row.getLabel(), this.alphabet.size());
                this.shortPrefixRows.add(newRow);
            } else {
                newRow = new RowImpl<>(row.getLabel());
                this.longPrefixRows.add(newRow);
            }
            oldRowToNewRow.put(row, newRow);
            this.allRows.add(newRow);
            this.rowMap.put(newRow.getLabel(), newRow);

            newRow.setRowContent(oldContentToNewContent.get(row.getRowContent()));
            oldContentToNewContent.get(row.getRowContent()).addAssociatedRow(newRow);

            if (table.canonicalRows.containsValue(row)) {
                this.canonicalRows.put(newRow.getRowContent(), newRow);
            }
        }

        for (Map.Entry<RowImpl<I, D>, RowImpl<I, D>> entry : oldRowToNewRow.entrySet()) {
            for (int index = 0; index < this.alphabet.size(); index++) {
                if (entry.getKey().isShortPrefixRow()) {
                    entry.getValue().setSuccessor(index, oldRowToNewRow.get(entry.getKey().getSuccessor(index)));
                }
            }
        }

        this.initialConsistencyCheckRequired = table.initialConsistencyCheckRequired;
    }

    public GenericObservationTable(Alphabet<I> alphabet, List<Word<I>> shortPrefixes, List<Word<I>> longPrefixes,
                                   List<Word<I>> suffixes, List<List<D>> shortPrefixRows, List<List<D>> longPrefixRows) {
        this.alphabet = alphabet;
        this.alphabetSize = this.alphabet.size();
        this.suffixes.addAll(suffixes);
        this.suffixSet.addAll(suffixes);

        for (int index = 0; index < shortPrefixes.size(); index++) {
            RowImpl<I, D> newRow = new RowImpl<>(shortPrefixes.get(index), this.alphabet.size());
            this.rowMap.put(shortPrefixes.get(index), newRow);
            if (!contentToRowContent.containsKey(shortPrefixRows.get(index))) {
                RowContent<I, D> newContent = new RowContent<>(shortPrefixRows.get(index));
                contentToRowContent.put(shortPrefixRows.get(index), newContent);
                this.canonicalRows.put(newContent, newRow);
            }
            newRow.setRowContent(contentToRowContent.get(shortPrefixRows.get(index)));
            RowContent<I, D> newContent = newRow.getRowContent();
            if (newContent != null) {
                newContent.addAssociatedRow(newRow);
            }

            this.allRows.add(newRow);
            this.shortPrefixRows.add(newRow);
        }

        for (int index = 0; index < longPrefixes.size(); index++) {
            RowImpl<I, D> newRow = new RowImpl<>(longPrefixes.get(index));
            this.rowMap.put(longPrefixes.get(index), newRow);
            if (!contentToRowContent.containsKey(longPrefixRows.get(index))) {
                RowContent<I, D> newContent = new RowContent<>(longPrefixRows.get(index));
                contentToRowContent.put(longPrefixRows.get(index), newContent);
            }
            newRow.setRowContent(contentToRowContent.get(longPrefixRows.get(index)));
            RowContent<I, D> newContent = newRow.getRowContent();
            if (newContent != null) {
                newContent.addAssociatedRow(newRow);
            }

            this.allRows.add(newRow);
            this.longPrefixRows.add(newRow);
        }

        for (RowImpl<I, D> row : this.shortPrefixRows) {
            for (int index = 0; index < this.alphabet.size(); index++) {
                row.setSuccessor(index, this.rowMap.get(row.getLabel().append(alphabet.getSymbol(index))));
            }
        }

        // TODO: Check this.
        this.initialConsistencyCheckRequired = false;
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
        RowImpl<I, D> newRow = new RowImpl<>(prefix, alphabet.size());
        allRows.add(newRow);
        rowMap.put(prefix, newRow);
        shortPrefixRows.add(newRow);
        return newRow;
    }

    private RowImpl<I, D> createLpRow(Word<I> prefix) {
        RowImpl<I, D> newRow = new RowImpl<>(prefix);
        allRows.add(newRow);
        rowMap.put(prefix, newRow);
        longPrefixRows.add(newRow);
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
            final RowImpl<I, D> row = rowMap.get(r.getLabel());
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
        for (RowImpl<I, D> row : allRows) {
            for (Word<I> suf : suffixes) {
                // FIXME: This logic only applies to DFAs! In Mealy machines cell(a.ab) != cell(aa.b),
                //  so we can't blanket correct every matching word. However, what we can do is set
                //  the corrected value to match suffix size.
                if (row.getLabel().concat(suf).equals(prefix.concat(suffix))) {
                    List<D> correctedContents = new LinkedList<>();
                    RowContent<I, D> rowContent = row.getRowContent();
                    if (rowContent != null) {
                        correctedContents.addAll(rowContent.getContents());
                    }
                    correctedContents.set(suffixes.indexOf(suf), correctValue);
                    processContents(row, correctedContents, row.isShortPrefixRow());
                }
            }
        }

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

    private void deleteRow(Row<I, D> row, boolean cleanupContents) {
        RowContent<I, D> associatedContent = row.getRowContent();
        if (associatedContent != null) {
            associatedContent.removeAssociatedRow(row);
            if (associatedContent.getAssociatedRows().size() == 0 && cleanupContents) {
                contentToRowContent.remove(associatedContent.getContents());
                canonicalRows.remove(associatedContent);
            }
        }
        allRows.remove((RowImpl<I, D>) row);
        if (row.isShortPrefixRow()) {
            shortPrefixRows.remove(row);
            for (int index = 0; index < alphabet.size(); index++) {
                deleteRow(row.getSuccessor(index), cleanupContents);
            }
        } else {
            longPrefixRows.remove(row);
        }
    }

    public boolean minimiseTable() {
        // TODO: A property critical for our table to be kept up-to-date is the idea that "table not correct => cex will be returned",
        //  however this only holds if the observation table is kept minimal. This is because we could have a table that
        //  represents a correct but not minimal DFA, and that way no cex will be returned fixing incorrect cells causing non-minimallity.
        //  --
        //  As such, we want to minimise the table every time it becomes closed and consistent. Not before, as we don't want to remove behaviour that
        //  could be critical in detecting unclosedness or inconsistency.
        //  --
        //  I have an algorithm for this, not sure it is the most efficient but it is correct. I'll implement it soon.

        boolean minimised = false;
        for (RowContent<I, D> content : contentToRowContent.values()) {
            List<Row<I, D>> associatedShortRows = content.getAssociatedRows().stream()
                .filter(Row::isShortPrefixRow)
                .collect(Collectors.toList());

            while (associatedShortRows.size() > 1) {
                Row<I, D> biggestLexLabelRow = associatedShortRows.stream()
                    // TODO: This may not work with complex alphabets as it relies on string rep.
                    .max(Comparator.comparing(row -> row.getLabel().toString().replaceAll("\\s", "").replaceAll("ε", "")))
                    .orElse(null);

                if (biggestLexLabelRow.getLabel().size() != 0) {
                    minimised = true;
                    Word<I> prefixLabel = biggestLexLabelRow.getLabel().prefix(biggestLexLabelRow.getLabel().size() - 1);
                    if (rowMap.get(prefixLabel).isShortPrefixRow()) {
                        makeLong((RowImpl<I, D>) biggestLexLabelRow);
                    } else {
                        deleteRow(biggestLexLabelRow, false);
                    }
                }
                associatedShortRows = content.getAssociatedRows().stream()
                    .filter(Row::isShortPrefixRow)
                    .collect(Collectors.toList());
            }
        }

        contentToRowContent.entrySet().removeIf(entry -> entry.getValue().getAssociatedRows().isEmpty());

        for (int sufIndex = suffixes.size() -1; sufIndex > 0; sufIndex--) {
            List<Word<I>> newSuffixes = new ArrayList<>(suffixes);
            newSuffixes.remove(sufIndex);
            int finalSufIndex = sufIndex;
            GenericObservationTable<I, D> hypotheticalTable = new GenericObservationTable<>(
                alphabet,
                shortPrefixRows.stream().map(RowImpl::getLabel).collect(Collectors.toList()),
                longPrefixRows.stream().map(RowImpl::getLabel).collect(Collectors.toList()),
                newSuffixes,
                shortPrefixRows.stream()
                    .map(row -> new ArrayList<>(row.getRowContent().getContents()))
                    .peek(newContent -> newContent.remove(finalSufIndex))
                    .collect(Collectors.toList()),
                longPrefixRows.stream()
                    .map(row -> new ArrayList<>(row.getRowContent().getContents()))
                    .peek(newContent -> newContent.remove(finalSufIndex))
                    .collect(Collectors.toList())
            );

            if (hypotheticalTable.findInconsistency() == null) {
                minimised = true;
                this.allRows.clear();
                this.allRows.addAll(hypotheticalTable.allRows);
                this.shortPrefixRows.clear();
                this.shortPrefixRows.addAll(hypotheticalTable.shortPrefixRows);
                this.longPrefixRows.clear();
                this.longPrefixRows.addAll(hypotheticalTable.longPrefixRows);
                this.suffixes.clear();
                this.suffixes.addAll(hypotheticalTable.suffixes);
                this.suffixSet.clear();
                this.suffixSet.addAll(hypotheticalTable.suffixSet);
                this.rowMap.clear();
                this.rowMap.putAll(hypotheticalTable.rowMap);
                this.contentToRowContent.clear();
                this.contentToRowContent.putAll(hypotheticalTable.contentToRowContent);
                this.canonicalRows.clear();
                this.canonicalRows.putAll(hypotheticalTable.canonicalRows);
            }
        }
        return minimised;
    }

    private void makeShort(RowImpl<I, D> row) {
        if (row.isShortPrefixRow()) {
            return;
        }

        longPrefixRows.remove(row);
        shortPrefixRows.add(row);
        // shortPrefixRows.sort(Comparator.comparing(shortRow -> shortRow.getLabel().toString()));
        row.makeShort(alphabet.size());
    }

    private void makeLong(RowImpl<I, D> row) {
        if (!row.isShortPrefixRow()) {
            return;
        }

        shortPrefixRows.remove(row);
        longPrefixRows.add(row);
        // longPrefixRows.sort(Comparator.comparing(longRow -> longRow.getLabel().toString()));

        for (int index = 0; index < alphabet.size(); index++) {
            deleteRow(row.getSuccessor(index), false);
        }

        row.makeLong();
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

    @Override
    public Collection<Row<I, D>> getAllRows() {
        return Collections.unmodifiableList(allRows);
    }
}
