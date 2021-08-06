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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.automatalib.words.Word;

public interface ShrinkableObservationtable<I, D> extends MutableObservationTable<I, D> {

    /**
     * Removes a suffix from the list of distinguishing suffixes. This is a convenience method that can be used as shorthand
     * for {@code removeSuffixes(Collections.singletonList(suffix))}.
     *
     * @param suffix the suffix to remove
     * @return a list of equivalence classes of unclosed rows
     */
    default List<List<Row<I, D>>> removeSuffix(Word<I> suffix) {
        return removeSuffixes(Collections.singletonList(suffix));
    }

    /**
     * Removes suffixes from the list of distinguishing suffixes.
     *
     * @param suffixes the suffixes to remove
     * @return a list of equivalence classes of unclosed rows
     */
    List<List<Row<I, D>>> removeSuffixes(Collection<? extends Word<I>> suffixes);

    List<List<Row<I, D>>> removeShortPrefixes(List<? extends Word<I>> shortPrefixes);
}
