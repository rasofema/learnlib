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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RowContent<I, D> {
    private List<D> contents;
    private Set<Row<I, D>> associatedRows;
    private int id;

    public RowContent(List<D> contents) {
        this(contents, new LinkedHashSet<>());
    }

    public RowContent(List<D> contents, Set<Row<I, D>> associatedRows) {
        this.contents = contents;
        this.associatedRows = associatedRows;
        updateId();
    }

    public List<D> getContents() {
        return contents;
    }

    public void setContents(List<D> contents) {
        this.contents = contents;
        updateId();
    }

    public Set<Row<I, D>> getAssociatedRows() {
        return associatedRows;
    }

    public void setAssociatedRows(Set<Row<I, D>> associatedRows) {
        this.associatedRows = associatedRows;
    }

    public void addAssociatedRows(Collection<Row<I, D>> associatedRows) {
        this.associatedRows.addAll(associatedRows);
    }

    public void addAssociatedRow(Row<I, D> associatedRow) {
        this.associatedRows.add(associatedRow);
    }

    public void removeAssociatedRows(Collection<Row<I, D>> associatedRows) {
        this.associatedRows.removeAll(associatedRows);
    }

    public void removeAssociatedRow(Row<I, D> associatedRow) {
        this.associatedRows.remove(associatedRow);
    }

    public int getId() {
        return id;
    }

    private void updateId() {
        this.id = this.contents.hashCode();
    }


    public D get(int index) {
        return this.contents.get(index);
    }

    public D set(int index, D element) {
        return this.contents.set(index, element);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RowContent<?, ?> that = (RowContent<?, ?>) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
