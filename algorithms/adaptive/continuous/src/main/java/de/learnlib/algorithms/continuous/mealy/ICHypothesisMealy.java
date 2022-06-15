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
package de.learnlib.algorithms.continuous.mealy;

import java.util.HashMap;
import java.util.Map;

import de.learnlib.algorithms.continuous.base.AbstractICHypothesis;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.commons.util.Pair;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class ICHypothesisMealy<I, O> extends AbstractICHypothesis<I>
        implements MealyMachine<Word<I>, I, Pair<Word<I>, I>, O> {
    public final Map<Pair<Word<I>, I>, O> outputs = new HashMap<>();

    public ICHypothesisMealy(Alphabet<I> alphabet) {
        super(alphabet);
    }

    @Override
    public Void getStateProperty(Word<I> state) {
        return null;
    }

    @Override
    public O getTransitionProperty(Pair<Word<I>, I> transition) {
        return outputs.get(transition);
    }

    @Override
    public O getTransitionOutput(Pair<Word<I>, I> transition) {
        return outputs.get(transition);
    }

    public void addTransition(Word<I> start, I input, Word<I> dest, O output) {
        transitions.put(Pair.of(start, input), dest);
        outputs.put(Pair.of(start, input), output);
    }

}
