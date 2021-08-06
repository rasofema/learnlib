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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import de.learnlib.datastructure.observationtable.reader.SimpleObservationTable;
import net.automatalib.words.Word;

/**
 * Mock-up observation table for testing writers.
 *
 * @param <I>
 *         input symbol type
 * @param <D>
 *         observation (output) domain type
 *
 * @author frohme
 */
public class MockedObservationTable<I, D> extends SimpleObservationTable<I, D> {
    private final Map<List<D>, RowContent<I, D>> contentsToRowContent;

    private final List<RowImpl<I, D>> rows;
    private final List<RowImpl<I, D>> shortPrefixes;
    private final List<RowImpl<I, D>> longPrefixes;

    MockedObservationTable(List<? extends Word<I>> suffixes) {
        super(suffixes);
        this.contentsToRowContent = new HashMap<>();

        this.rows = new LinkedList<>();
        this.shortPrefixes = new LinkedList<>();
        this.longPrefixes = new LinkedList<>();
    }

    void addShortPrefix(final Word<I> prefix, List<D> contents) {
        shortPrefixes.add(addPrefix(prefix, contents));
    }

    void addLongPrefix(final Word<I> prefix, List<D> contents) {
        longPrefixes.add(addPrefix(prefix, contents));
    }

    private RowImpl<I, D> addPrefix(final Word<I> prefix, List<D> contents) {
        Preconditions.checkArgument(getSuffixes().size() == contents.size());

        final RowImpl<I, D> row = new RowImpl<>(prefix, rows.size());
        final RowContent<I, D> rowContent = contentsToRowContent.computeIfAbsent(contents, k -> {
            Set<Row<I, D>> associatedRows = new HashSet<>();
            associatedRows.add(row);
            return new RowContent<>(contents, associatedRows);
        });

        contentsToRowContent.put(contents, rowContent);
        row.setRowContent(rowContent);
        rows.add(row);

        return row;
    }

    @Override
    public Collection<Row<I, D>> getShortPrefixRows() {
        return Collections.unmodifiableList(shortPrefixes);
    }

    @Override
    public Collection<Row<I, D>> getLongPrefixRows() {
        return Collections.unmodifiableList(longPrefixes);
    }

    @Override
    public RowImpl<I, D> getRow(int idx) {
        return rows.get(idx);
    }

    @Override
    public int numberOfDistinctRows() {
        return contentsToRowContent.size();
    }
}
