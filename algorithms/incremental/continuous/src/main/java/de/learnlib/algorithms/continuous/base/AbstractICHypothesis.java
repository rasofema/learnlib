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
package de.learnlib.algorithms.continuous.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.DeterministicAutomaton;
import net.automatalib.automata.FiniteAlphabetAutomaton;
import net.automatalib.commons.util.Pair;
import net.automatalib.graphs.Graph;
import net.automatalib.visualization.DefaultVisualizationHelper;
import net.automatalib.visualization.VisualizationHelper;
import net.automatalib.words.Alphabet;
import net.automatalib.words.GrowingAlphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * Hypothesis DFA for the {@link de.learnlib.algorithms.continuous.dfa.ContinuousDFA algorithm}.
 *
 * @param <I>
 *         input symbol type
 *
 * @author Malte Isberner
 */
public abstract class AbstractICHypothesis<I, T> implements DeterministicAutomaton<Word<I>, I, T>,
                                                                FiniteAlphabetAutomaton<Word<I>, I, T>,
                                                                SupportsGrowingAlphabet<I> {

    private final Alphabet<I> alphabet;
    public Word<I> initialState;
    public final Map<Pair<Word<I>, I>, Word<I>> transitions = new HashMap<>();
    public final Map<Word<I>, Boolean> acceptance = new HashMap<>();

    /**
     * Constructor.
     *
     * @param alphabet
     *         the input alphabet
     */
    public AbstractICHypothesis(Alphabet<I> alphabet) {
        this.alphabet = alphabet;
    }

    @Override
    public Word<I> getInitialState() {
        return initialState;
    }

    @Override
    public T getTransition(Word<I> state, I input) {
        return mapTransition(Pair.of(state, input));
    }

    protected abstract T mapTransition(Pair<Word<I>, I> internalTransition);

    @Override
    public Alphabet<I> getInputAlphabet() {
        return alphabet;
    }

    @Override
    public GraphView graphView() {
        return new GraphView();
    }

    @Override
    public void addAlphabetSymbol(I symbol) {
        final GrowingAlphabet<I> growingAlphabet = Alphabets.toGrowingAlphabetOrThrowException(this.alphabet);

        if (!growingAlphabet.containsSymbol(symbol)) {
            growingAlphabet.addSymbol(symbol);
        }
    }

    @Override
    public Collection<Word<I>> getStates() {
        return Collections.unmodifiableSet(acceptance.keySet());
    }

    @Override
    public int size() {
        return acceptance.size();
    }

    public void setInitial(Word<I> initial) {
        initialState = initial;
    }

    public void setAccepting(Word<I> state, boolean accepting) {
        acceptance.put(state, accepting);
    }

    public boolean isAccepting(Word<I> state) {
        return acceptance.getOrDefault(state, false);
    }


    public void addTransition(Word<I> start, I input, Word<I> dest) {
        transitions.put(Pair.of(start, input), dest);
    }

    public static final class ICEdge<I> {

        public final Pair<Word<I>, I> transition;
        public final Word<I> target;

        public ICEdge(Pair<Word<I>, I> transition, Word<I> target) {
            this.transition = transition;
            this.target = target;
        }
    }

    public class GraphView implements Graph<Word<I>, ICEdge<I>> {

        @Override
        public Collection<Word<I>> getNodes() {
            return acceptance.keySet();
        }

        @Override
        public Collection<ICEdge<I>> getOutgoingEdges(Word<I> node) {
            List<ICEdge<I>> result = new ArrayList<>();
            transitions.entrySet().stream()
                .filter(e -> e.getKey().getFirst().equals(node))
                .forEach(e -> result.add(new ICEdge<>(e.getKey(), e.getValue())));
            return result;
        }

        @Override
        public Word<I> getTarget(ICEdge<I> edge) {
            return edge.target;
        }

        @Override
        public VisualizationHelper<Word<I>, ICEdge<I>> getVisualizationHelper() {
            return new DefaultVisualizationHelper<Word<I>, ICEdge<I>>() {

                @Override
                public boolean getEdgeProperties(Word<I> src,
                                                 ICEdge<I> edge,
                                                 Word<I> tgt,
                                                 Map<String, String> properties) {
                    properties.put(EdgeAttrs.LABEL, String.valueOf(edge.transition.getSecond()));
                    return true;
                }

            };
        }

    }
}
