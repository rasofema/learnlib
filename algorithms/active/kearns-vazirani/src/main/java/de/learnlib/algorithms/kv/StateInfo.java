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
package de.learnlib.algorithms.kv;

import java.util.HashSet;
import java.util.Set;

import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import net.automatalib.commons.util.Pair;
import net.automatalib.words.Word;

/**
 * The information associated with a state: it's access sequence (or access string), and the list of incoming
 * transitions.
 *
 * @param <I>
 *         input symbol type
 * @param <D>
 *         data type
 *
 * @author Malte Isberner
 */
public final class StateInfo<I, D> {
    public int id;
    public final Word<I> accessSequence;
    public AbstractWordBasedDTNode<I, D, StateInfo<I, D>> dtNode;
    private final Set<Pair<StateInfo<I, D>, I>> incoming;

    public StateInfo(int id, Word<I> accessSequence) {
        this.accessSequence = accessSequence.trimmed();
        this.id = id;
        this.incoming = new HashSet<>();
    }

    public void addIncoming(StateInfo<I, D> sourceState, I symbol) {
        incoming.add(Pair.of(sourceState, symbol));
    }

    public void removeIncoming(StateInfo<I, D> sourceState, I symbol) {
        incoming.removeIf(pair -> pair.getFirst().equals(sourceState) && pair.getSecond().equals(symbol));
    }

    public Set<Pair<StateInfo<I, D>, I>> fetchIncoming() {
        Set<Pair<StateInfo<I, D>, I>> out = new HashSet<>(incoming);
        incoming.clear();
        return out;
    }

    public void clearIncoming() {
        incoming.clear();
    }
}
