/* Copyright (C) 2018
 * This file is part of the PhD research project entitled
 * Inferring models from Evolving Systems and Product Families.
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
package de.learnlib.algorithms.dlstar;

import de.learnlib.algorithms.dlstar.AbstractAutomatonDLStar;
import de.learnlib.algorithms.dlstar.AbstractAutomatonDLStar.StateInfo;
import de.learnlib.algorithms.dlstar.AbstractDLStarState;
import de.learnlib.datastructure.observationtable.DynamicObservationTable;

import java.util.List;

/**
 * Class that contains all data that represent the internal state of the {@link AbstractAutomatonDLStar} learner and its
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
 *  @author Carlos Diego Nascimento Damasceno (damascenodiego@usp.br)
 */
public class AutomatonDLStarState<I, D, AI, S> extends AbstractDLStarState<I, D> {

    private final AI hypothesis;
    private final List<StateInfo<S, I>> stateInfos;

    AutomatonDLStarState(final DynamicObservationTable<I, D> observationTable,
                         final AI hypothesis,
                         final List<StateInfo<S, I>> stateInfos) {
        super(observationTable);
        this.hypothesis = hypothesis;
        this.stateInfos = stateInfos;
    }

    AI getHypothesis() {
        return hypothesis;
    }

    List<AbstractAutomatonDLStar.StateInfo<S, I>> getStateInfos() {
        return stateInfos;
    }
}
