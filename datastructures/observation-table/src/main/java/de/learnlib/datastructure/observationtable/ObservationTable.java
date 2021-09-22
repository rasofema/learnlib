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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.learnlib.api.AccessSequenceTransformer;
import de.learnlib.api.oracle.MembershipOracle;
import net.automatalib.commons.util.Pair;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.signedness.qual.Signed;

/**
 * An observation table is a common method for learning algorithms to store organize their observations. This interface
 * defines a generic API for interacting with homogeneously interacting with observation tables regardless of their
 * implementation.
 * <p>
 * Instances implementing this interface can be obtained from learning algorithms implementing the {@link
 * ObservationTableFeature observation table feature} (or {@link OTLearner}s.
 * <p>
 * Basically, an observation table is a two-dimensional table, where both rows and columns are indexed by {@link Word}s.
 * The row indices are called <i>prefixes</i>, whereas the column indexes are referred to as <i>suffixes</i>. The table
 * is further vertically divided into two halves: the prefixes in the upper half are referred to as <i>short
 * prefixes</i> (these usually correspond to states in learned hypothesis automata), whereas the prefixes in the lower
 * half are referred to as <i>long prefixes</i>. Long prefixes must be one-letter extensions of short prefixes; they
 * refer to transitions in the hypothesis automaton. We refer to rows as <i>short prefix rows</i> or <i>long prefix
 * row</i>, depending on whether they occur in the upper or lower half of the table respectively.
 * <p>
 * The cells of the table are filled with observations for a given prefix and suffix combination. The type of
 * observations is generic and can be specified using the type parameter {@code O}.
 * <p>
 * There are two important properties of observation tables, which usually have to be satisfied in order to be able to
 * generate an automaton from an observation table: it must be both <i>closed</i> and <i>consistent</i>.
 * <p>
 * In a <b>closed</b> observation table, the contents of <i>each</i> long prefix row equal the contents of at least one
 * short prefix rows. <b>Consistency</b>, on the other hand, is satisfied when for every two distinct short prefix rows,
 * all rows indexed by one-letter extensions of the corresponding prefix with any input symbol also have the same
 * content.
 *
 * @param <I>
 *         input symbol type
 * @param <D>
 *         observation (output) domain type
 *
 * @author Malte Isberner
 */
public interface ObservationTable<I, D> extends AccessSequenceTransformer<I> {

    /**
     * Used to indicate that no distinguishing suffix exists in {@link #findDistinguishingSuffixIndex(Inconsistency)}
     * and {@link #findDistinguishingSuffixIndex(Row, Row)}.
     */
    int NO_DISTINGUISHING_SUFFIX = -1;

    /**
     * Retrieves the input alphabet used in this observation table.
     *
     * @return the input alphabet
     */
    Alphabet<I> getInputAlphabet();

    /**
     * Retrieves all prefixes (short and long) in the table. The prefixes are returned in no specified order.
     *
     * @return all prefixes in the table
     */
    default Collection<Word<I>> getAllPrefixes() {
        Collection<Word<I>> shortPrefixes = getShortPrefixes();
        Collection<Word<I>> longPrefixes = getLongPrefixes();
        List<Word<I>> result = new ArrayList<>(shortPrefixes.size() + longPrefixes.size());

        result.addAll(shortPrefixes);
        result.addAll(longPrefixes);

        return result;
    }

    /**
     * Retrieves the short prefixes in the table. The prefixes are returned in no specified order.
     *
     * @return the short prefixes in the table
     */
    default Collection<Word<I>> getShortPrefixes() {
        Collection<Row<I, D>> spRows = getShortPrefixRows();
        return spRows.stream().map(Row::getLabel).collect(Collectors.toList());

    }

    /**
     * Retrieves the long prefixes in the table. The prefixes are returned in no specified order.
     *
     * @return the long prefixes in the table
     */
    default Collection<Word<I>> getLongPrefixes() {
        Collection<Row<I, D>> lpRows = getLongPrefixRows();
        return lpRows.stream().map(Row::getLabel).collect(Collectors.toList());
    }

    Collection<Row<I, D>> getShortPrefixRows();

    Collection<Row<I, D>> getLongPrefixRows();

    /**
     * Returns the specified row of the observation table.
     *
     * @param idx
     *         the index of the row
     *
     * @return the row
     *
     * @throws IndexOutOfBoundsException
     *         if {@code idx} is less than 0 or greater than {@code number of rows - 1}.
     */
    Row<I, D> getRow(int idx);

    default @Nullable Row<I, D> getRow(Word<I> prefix) {
        for (Row<I, D> row : getAllRows()) {
            if (prefix.equals(row.getLabel())) {
                return row;
            }
        }

        return null;
    }

    default Collection<Row<I, D>> getAllRows() {
        Collection<Row<I, D>> spRows = getShortPrefixRows();
        Collection<Row<I, D>> lpRows = getLongPrefixRows();

        List<Row<I, D>> result = new ArrayList<>(spRows.size() + lpRows.size());
        result.addAll(spRows);
        result.addAll(lpRows);

        return result;
    }

    /**
     * Returns the total number of rows in this observation table. This number may be used as the upper bound for the
     * (row) ids of the table rows.
     *
     * @return the number of rows
     */
    default int numberOfRows() {
        return getShortPrefixRows().size() + getLongPrefixRows().size();
    }

    default int numberOfShortPrefixRows() {
        return getShortPrefixRows().size();
    }

    default int numberOfLongPrefixRows() {
        return getLongPrefixRows().size();
    }

    /**
     * Returns the number of distinct (regarding row values) rows in this observation table. This number may be used as
     * the upper bound for the (content ids of the table rows.
     *
     * @return the number of distinct rows
     */
    int numberOfDistinctRows();

    default boolean isClosed() {
        return findUnclosedRow() == null;
    }

    // TODO: Lazy n^2 implementation, can probably make it 2n, something like that.
    default @Nullable Row<I, D> findUnclosedRow() {
        for (Row<I, D> lpRow : getLongPrefixRows()) {
            RowContent<I, D> rowContent = lpRow.getRowContent();
            if (rowContent != null) {
                boolean foundSPRow = false;
                for (Row<I, D> associatedRow : rowContent.getAssociatedRows()) {
                    if (getShortPrefixRows().contains(associatedRow)) {
                        foundSPRow = true;
                    }
                }
                if (!foundSPRow) {
                    return lpRow;
                }
            }
        }

        return null;
    }

    default List<List<Row<I, D>>> findUnclosedRows() {
        Set<RowContent<I, D>> spRowContents = getShortPrefixRows().stream()
            .map(Row::getRowContent)
            .collect(Collectors.toSet());

        Set<RowContent<I, D>> unclosedSet = getLongPrefixRows().stream()
            .filter(row -> !spRowContents.contains(row.getRowContent()))
            .map(Row::getRowContent)
            .collect(Collectors.toSet());

        // Get the set of unclosed row contents,
        return unclosedSet.stream()
            // Map them to the associated rows,
            .map(con -> con.getAssociatedRows().stream()
                // Keep only the long rows,
                .filter(row -> !row.isShortPrefixRow())
                // Collect them into lists,
                .collect(Collectors.toList()))
            // Collect this stream of lists into a list.
            .collect(Collectors.toList());
    }

    default @Nullable Word<I> findDistinguishingSuffix(Inconsistency<I, D> inconsistency) {
        int suffixIndex = findDistinguishingSuffixIndex(inconsistency);
        if (suffixIndex != NO_DISTINGUISHING_SUFFIX) {
            return null;
        }
        return getSuffix(suffixIndex);
    }

    /**
     * @param row1
     *         the first row
     * @param row2
     *         the second row
     *
     * @return the suffix distinguishing the contents of the two rows
     */
    default @Nullable Word<I> findDistinguishingSuffix(Row<I, D> row1, Row<I, D> row2) {
        int suffixIndex = findDistinguishingSuffixIndex(row1, row2);
        if (suffixIndex != NO_DISTINGUISHING_SUFFIX) {
            return null;
        }
        return getSuffix(suffixIndex);
    }

    /**
     * @return the suffix (column) index where the contents of the rows differ, or {@code #NO_DISTINGUISHING_SUFFIX} if
     * the contents of the rows are equal.
     */
    default @Signed int findDistinguishingSuffixIndex(Inconsistency<I, D> inconsistency) {
        Row<I, D> row1 = inconsistency.getFirstRow();
        Row<I, D> row2 = inconsistency.getSecondRow();
        int symIdx = getInputAlphabet().getSymbolIndex(inconsistency.getSymbol());

        return findDistinguishingSuffixIndex(row1.getSuccessor(symIdx), row2.getSuccessor(symIdx));
    }

    /**
     * @param row1
     *         the first row
     * @param row2
     *         the second row
     *
     * @return the suffix (column) index where the contents of the rows differ, or {@code #NO_DISTINGUISHING_SUFFIX} if
     * the contents of the rows are equal.
     */
    default @Signed int findDistinguishingSuffixIndex(Row<I, D> row1, Row<I, D> row2) {
        for (int i = 0; i < getSuffixes().size(); i++) {
            if (!Objects.equals(cellContents(row1, i), cellContents(row2, i))) {
                return i;
            }
        }

        return NO_DISTINGUISHING_SUFFIX;
    }

    /**
     * Retrieves a suffix by its (column) index.
     *
     * @param index
     *         the index
     *
     * @return the suffix
     */
    default Word<I> getSuffix(int index) {
        return getSuffixes().get(index);
    }

    /**
     * Retrieves all suffixes in the table.
     *
     * @return all suffixes in the table
     */
    List<Word<I>> getSuffixes();

    default int numberOfSuffixes() {
        return getSuffixes().size();
    }

    default boolean isConsistent() {
        return findInconsistency() == null;
    }

    // TODO: Probably could be improved with the new design.
    //  Currently just uses the same overall algo.
    default @Nullable Inconsistency<I, D> findInconsistency() {
        final Map<RowContent<I, D>, Row<I, D>> canonicalRows = new HashMap<>();
        final Alphabet<I> alphabet = getInputAlphabet();

        for (Row<I, D> spRow : getShortPrefixRows()) {
            RowContent<I, D> rowContent = spRow.getRowContent();

            Row<I, D> canRow = canonicalRows.getOrDefault(rowContent, null);
            if (!canonicalRows.containsKey(rowContent)) {
                canonicalRows.put(rowContent, spRow);
                continue;
            }

            for (int i = 0; i < alphabet.size(); i++) {
                RowContent<I, D> spSuccContent = spRow.getSuccessor(i).getRowContent();
                RowContent<I, D> canSuccContent = canRow.getSuccessor(i).getRowContent();
                if (spSuccContent != canSuccContent) {
                    return new Inconsistency<>(canRow, spRow, alphabet.getSymbol(i));
                }
            }
        }

        return null;
    }

    default Row<I, D> getRowSuccessor(Row<I, D> row, I sym) {
        return row.getSuccessor(getInputAlphabet().getSymbolIndex(sym));
    }

    default D cellContents(Row<I, D> row, int columnId) {
        RowContent<I, D> rowContent = row.getRowContent();
        if (rowContent == null) {
            return null;
        }
        return rowContent.get(columnId);
    }

}
