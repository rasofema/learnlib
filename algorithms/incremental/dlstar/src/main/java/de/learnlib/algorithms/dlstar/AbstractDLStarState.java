/* Copyright (C) 2018
 * This file is part of the PhD research project entitled
 * 'Inferring models from Evolving Systems and Product Families'
 * developed by Carlos Diego Nascimento Damasceno at the
 * University of Sao Paulo (ICMC-USP).
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

import de.learnlib.algorithms.lstar.AbstractLStar;
import de.learnlib.datastructure.observationtable.DynamicObservationTable;
import de.learnlib.datastructure.observationtable.GenericObservationTable;

import java.io.Serializable;

/**
 * Class that contains all data that represent the internal state of the {@link AbstractDLStar} learner.
 *
 * @param <I>
 *         The input alphabet type.
 * @param <D>
 *         The output domain type.
 *
 * @author Carlos Diego Nascimento Damasceno (damascenodiego@usp.br)
 */
public abstract class AbstractDLStarState<I, D> implements Serializable {

    private final DynamicObservationTable<I, D> observationTable;

    AbstractDLStarState(final DynamicObservationTable<I, D> observationTable) {
        this.observationTable = observationTable;
    }

    DynamicObservationTable<I, D> getObservationTable() {
        return observationTable;
    }
}
