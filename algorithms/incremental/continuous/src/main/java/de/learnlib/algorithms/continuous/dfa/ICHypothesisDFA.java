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
package de.learnlib.algorithms.continuous.dfa;

import de.learnlib.algorithms.continuous.base.AbstractICHypothesis;
import de.learnlib.algorithms.continuous.base.ICState;
import de.learnlib.algorithms.continuous.base.ICTransition;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.Alphabet;

public class ICHypothesisDFA<I> extends AbstractICHypothesis<I, Boolean, ICState<I, Boolean>>
        implements DFA<ICState<I, Boolean>, I>{

    public ICHypothesisDFA(Alphabet<I> alphabet) {
        super(alphabet);
    }

    @Override
    public ICState<I, Boolean> getSuccessor(ICState<I, Boolean> transition) {
        return transition;
    }

    @Override
    protected ICState<I, Boolean> mapTransition(ICTransition<I, Boolean> internalTransition) {
        for (ICState<I, Boolean> state : states) {
            if (state.incoming.contains(internalTransition)) {
                return state;
            }
        }
        return null;
    }

    @Override
    public boolean isAccepting(ICState<I, Boolean> state) {
        return state.isAccepting();
    }

    @Override
    public Void getTransitionProperty(ICState<I, Boolean> transition) {
        return null;
    }
}
