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

import de.learnlib.api.oracle.MembershipOracle;
import net.automatalib.words.Word;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface ShrinkableObservationtable<I, D> extends ObservationTable<I, D> {

    /**
     * Initializes an observation table using a specified set of suffixes.
     *
     * @param initialSuffixes
     *         the set of initial column labels.
     * @param oracle
     *         the {@link MembershipOracle} to use for performing queries
     *
     * @return a list of equivalence classes of unclosed rows
     */
    List<List<Row<I, D>>> initialize(List<Word<I>> initialShortPrefixes,
                                  List<Word<I>> initialSuffixes,
                                  MembershipOracle<I, D> oracle);

    /**
     * Checks whether this observation table has been initialized yet (i.e., contains any rows).
     *
     * @return {@code true} iff the table has been initialized
     */
    boolean isInitialized();

    boolean isInitialConsistencyCheckRequired();

    /**
     * Adds a suffix to the list of distinguishing suffixes. This is a convenience method that can be used as shorthand
     * for {@code addSufixes(Collections.singletonList(suffix), oracle)}.
     *
     * @param suffix
     *         the suffix to add
     * @param oracle
     *         the membership oracle
     *
     * @return a list of equivalence classes of unclosed rows
     */
    default List<List<Row<I, D>>> addSuffix(Word<I> suffix, MembershipOracle<I, D> oracle) {
        return addSuffixes(Collections.singletonList(suffix), oracle);
    }

    /**
     * Removes a suffix from the list of distinguishing suffixes. This is a convenience method that can be used as shorthand
     * for {@code removeSuffixes(Collections.singletonList(suffix))}.
     *
     * @param suffix
     *         the suffix to remove
     * @return a list of equivalence classes of unclosed rows
     */
    default List<List<Row<I, D>>> removeSuffix(Word<I> suffix) {
        return removeSuffixes(Collections.singletonList(suffix));
    }

    /**
     * Adds suffixes to the list of distinguishing suffixes.
     *
     * @param newSuffixes
     *         the suffixes to add
     * @param oracle
     *         the membership oracle
     *
     * @return a list of equivalence classes of unclosed rows
     */
    List<List<Row<I, D>>> addSuffixes(Collection<? extends Word<I>> newSuffixes, MembershipOracle<I, D> oracle);

    /**
     * Removes suffixes from the list of distinguishing suffixes.
     *
     * @param suffixes
     *         the suffixes to remove
     *
     * @return a list of equivalence classes of unclosed rows
     */
    List<List<Row<I, D>>> removeSuffixes(Collection<? extends Word<I>> suffixes);

    List<List<Row<I, D>>> addShortPrefixes(List<? extends Word<I>> shortPrefixes, MembershipOracle<I, D> oracle);

    List<List<Row<I, D>>> removeShortPrefixes(List<? extends Word<I>> shortPrefixes);

    List<List<Row<I, D>>> correctWord(Word<I> word, D correctValue);

    /**
     * Moves the specified rows to the set of short prefix rows. If some of the specified rows already are short prefix
     * rows, they are ignored (unless they do not have any contents, in which case they are completed).
     *
     * @param lpRows
     *         the rows to move to the set of short prefix rows
     * @param oracle
     *         the membership oracle
     *
     * @return a list of equivalence classes of unclosed rows
     */
    List<List<Row<I, D>>> toShortPrefixes(List<Row<I, D>> lpRows, MembershipOracle<I, D> oracle);

    /**
     * Moves the specified rows to the set of long prefix rows. If some of the specified rows already are long prefix
     * rows, they are ignored (unless they do not have any contents, in which case they are completed).
     * Useful when preforming table minimisation.
     *
     * @param spRows
     *         the rows to move to the set of long prefix rows
     * @param oracle
     *         the membership oracle
     *
     * @return a list of equivalence classes of unclosed rows
     */
    List<List<Row<I, D>>> toLongPrefixes(List<Row<I, D>> spRows, MembershipOracle<I, D> oracle);

    List<List<Row<I, D>>> addAlphabetSymbol(I symbol, MembershipOracle<I, D> oracle);

}
