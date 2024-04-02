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

package de.learnlib.algorithm.incremental.dfa;

import de.learnlib.example.DefaultLearningExample;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.FastAlphabet;
import net.automatalib.alphabet.impl.Symbol;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.MutableDFA;
import net.automatalib.util.automaton.builder.AutomatonBuilders;

public class SimpleDFA extends DefaultLearningExample.DefaultDFALearningExample<Symbol<Character>> {
    public static final Symbol<Character> IN_ZERO = new Symbol<Character>('0');

    public SimpleDFA() {
        super(constructMachine());
    }

    /**
     * Construct and return a machine representation of this example.
     *
     * @return machine instance of the example
     */
    public static CompactDFA<Symbol<Character>> constructMachine() {
        return constructMachine(new CompactDFA<>(createInputAlphabet()));
    }

    public static <S, T, A extends MutableDFA<S, ? super Symbol<Character>>> A constructMachine(A machine) {
        return AutomatonBuilders.forDFA(machine).withInitial("s0").from("s0").on(IN_ZERO).to("s1").from("s1")
                .on(IN_ZERO).to("s2").from("s2").on(IN_ZERO).to("s0").withAccepting("s0").create();
    }

    public static Alphabet<Symbol<Character>> createInputAlphabet() {
        return new FastAlphabet<>(IN_ZERO);
    }

    public static SimpleDFA createExample() {
        return new SimpleDFA();
    }

}
