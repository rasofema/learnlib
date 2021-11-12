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
import net.automatalib.automata.fsa.DFA;
import net.automatalib.commons.util.Pair;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class ICHypothesisDFA<I> extends AbstractICHypothesis<I, Word<I>>
        implements DFA<Word<I>, I> {

    public ICHypothesisDFA(Alphabet<I> alphabet) {
        super(alphabet);
    }

    @Override
    public Word<I> getSuccessor(Word<I> transition) {
        return transition;
    }

    @Override
    protected Word<I> mapTransition(Pair<Word<I>, I> internalTransition) {
        return transitions.getOrDefault(internalTransition, null);
    }

    @Override
    public Void getTransitionProperty(Word<I> transition) {
        return null;
    }
}
