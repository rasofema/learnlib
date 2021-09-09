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
package de.learnlib.algorithms.ikv;

import java.util.Map;

import de.learnlib.algorithms.ikv.AbstractAutomatonILStar.StateInfo;
import de.learnlib.datastructure.observationtable.GenericObservationTable;
import de.learnlib.datastructure.observationtable.RowContent;

/**
 * Class that contains all data that represent the internal state of the {@link AbstractAutomatonILStar} learner and its
 * DFA and Mealy implementations.
 *
 * @param <I>
 *         The input alphabet type.
 * @param <D>
 *         The output domain type.
 * @param <AI>
 *         The hypothesis type.
 * @param <S>
 *         The hypothesis state type.
 *
 * @author bainczyk
 */
public class AutomatonILStarState<I, D, AI, S> {

    private final GenericObservationTable<I, D> observationTable;
    private final AI hypothesis;
    private final Map<RowContent<I, D>, StateInfo<S, I, D>> stateInfos;

    AutomatonILStarState(final GenericObservationTable<I, D> observationTable,
                         final AI hypothesis,
                         final Map<RowContent<I, D>, StateInfo<S, I, D>> stateInfos) {
        this.observationTable = observationTable;
        this.hypothesis = hypothesis;
        this.stateInfos = stateInfos;
    }

    GenericObservationTable<I, D> getObservationTable() {
        return observationTable;
    }

    AI getHypothesis() {
        return hypothesis;
    }

    Map<RowContent<I, D>, StateInfo<S, I, D>> getStateInfos() {
        return stateInfos;
    }
}
