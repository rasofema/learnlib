package de.learnlib.datastructure.observationtable;

import java.util.*;

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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RowContent<?, ?> that = (RowContent<?, ?>) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
