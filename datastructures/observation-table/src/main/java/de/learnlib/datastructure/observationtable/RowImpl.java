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

import net.automatalib.commons.smartcollections.ResizingArrayStorage;
import net.automatalib.words.Word;

final class RowImpl<I, D> implements Row<I, D> {

    private final Word<I> label;
    private RowContent<I, D> rowContent;
    private boolean isShortRow = false;
    private ResizingArrayStorage<RowImpl<I, D>> successors;

    /**
     * Constructor for short label rows.
     *
     * @param label
     *         the label (label) of this row
     * @param rowId
     *         the unique row identifier
     * @param alphabetSize
     *         the size of the alphabet, used for initializing the successor array
     */
    RowImpl(Word<I> label, int alphabetSize) {
        this(label);
        makeShort(alphabetSize);
    }

    /**
     * Constructor.
     *
     * @param label
     *         the label (label) of this row
     * @param rowId
     *         the unique row identifier
     */
    RowImpl(Word<I> label) {
        this.label = label;
    }

    /**
     * Makes this row a short label row. This leads to a successor array being created. If this row already is a short
     * label row, nothing happens.
     *
     * @param initialAlphabetSize
     *         the size of the input alphabet.
     */
    void makeShort(int initialAlphabetSize) {
        if (isShortRow) {
            return;
        }
        isShortRow = true;
        this.successors = new ResizingArrayStorage<>(RowImpl.class, initialAlphabetSize);
    }

    void makeLong() {
        this.isShortRow = false;
        this.successors = null;
    }

    @Override
    public RowImpl<I, D> getSuccessor(int inputIdx) {
        return successors.array[inputIdx];
    }

    /**
     * Sets the successor row for this short label row and the given alphabet symbol (by index). If this is no short
     * label row, an exception might occur.
     *
     * @param inputIdx
     *         the index of the alphabet symbol.
     * @param succ
     *         the successor row
     */
    void setSuccessor(int inputIdx, RowImpl<I, D> succ) {
        successors.array[inputIdx] = succ;
    }

    @Override
    public Word<I> getLabel() {
        return label;
    }

    @Override
    public RowContent<I, D> getRowContent() {
        return rowContent;
    }

    /**
     * Sets the ID of the row contents.
     *
     * @param rowContent
     *         the RowContent object
     */
    void setRowContent(RowContent<I, D> rowContent) {
        this.rowContent = rowContent;
        this.rowContent.addAssociatedRow(this);
    }

    @Override
    public boolean isShortPrefixRow() {
        return isShortRow;
    }

    boolean hasContents() {
        return rowContent != null;
    }

    /**
     * See {@link ResizingArrayStorage#ensureCapacity(int)}.
     */
    void ensureInputCapacity(int capacity) {
        this.successors.ensureCapacity(capacity);
    }
}
