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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.DeterministicAutomaton;
import net.automatalib.automata.FiniteAlphabetAutomaton;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.commons.util.Pair;
import net.automatalib.commons.util.Triple;
import net.automatalib.graphs.Graph;
import net.automatalib.visualization.DefaultVisualizationHelper;
import net.automatalib.visualization.VisualizationHelper;
import net.automatalib.words.Alphabet;
import net.automatalib.words.GrowingAlphabet;
import net.automatalib.words.impl.Alphabets;

/**
 * Hypothesis DFA for the {@link de.learnlib.algorithms.continuous.dfa.ContinuousDFA algorithm}.
 *
 * @param <I>
 *         input symbol type
 *
 * @author Malte Isberner
 */
public abstract class AbstractICHypothesis<I, D, T> implements DeterministicAutomaton<ICState<I, D>, I, T>,
                                                                FiniteAlphabetAutomaton<ICState<I, D>, I, T>,
                                                                SupportsGrowingAlphabet<I> {

    private final Alphabet<I> alphabet;
    public ICState<I, D> initialState;
    public final Map<Pair<ICState<I, D>, I>, ICState<I, D>> transitions = new HashMap<>();
    public final Map<ICState<I, D>, Boolean> acceptance = new HashMap<>();

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
    public ICState<I, D> getInitialState() {
        return initialState;
    }

    @Override
    public T getTransition(ICState<I, D> state, I input) {
        return mapTransition(Pair.of(state, input));
    }

    protected abstract T mapTransition(Pair<ICState<I, D>, I> internalTransition);

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
    public Collection<ICState<I, D>> getStates() {
        return Collections.unmodifiableSet(acceptance.keySet());
    }

    @Override
    public int size() {
        return acceptance.size();
    }

    public void setInitial(ICState<I, D> initial) {
        initialState = initial;
    }

    public void setAccepting(ICState<I, D> state, boolean accepting) {
        acceptance.put(state, accepting);
    }

    public void addTransition(ICState<I, D> start, I input, ICState<I, D> dest) {
        transitions.put(Pair.of(start, input), dest);
    }

    public static final class ICEdge<I, D> {

        public final Pair<ICState<I, D>, I> transition;
        public final ICState<I, D> target;

        public ICEdge(Pair<ICState<I, D>, I> transition, ICState<I, D> target) {
            this.transition = transition;
            this.target = target;
        }
    }

    public class GraphView implements Graph<ICState<I, D>, ICEdge<I, D>> {

        @Override
        public Collection<ICState<I, D>> getNodes() {
            return acceptance.keySet();
        }

        @Override
        public Collection<ICEdge<I, D>> getOutgoingEdges(ICState<I, D> node) {
            List<ICEdge<I, D>> result = new ArrayList<>();
            transitions.entrySet().stream()
                .filter(e -> e.getKey().getFirst().equals(node))
                .forEach(e -> result.add(new ICEdge<>(e.getKey(), e.getValue())));
            return result;
        }

        @Override
        public ICState<I, D> getTarget(ICEdge<I, D> edge) {
            return edge.target;
        }

        @Override
        public VisualizationHelper<ICState<I, D>, ICEdge<I, D>> getVisualizationHelper() {
            return new DefaultVisualizationHelper<ICState<I, D>, ICEdge<I, D>>() {

                @Override
                public boolean getEdgeProperties(ICState<I, D> src,
                                                 ICEdge<I, D> edge,
                                                 ICState<I, D> tgt,
                                                 Map<String, String> properties) {
                    properties.put(EdgeAttrs.LABEL, String.valueOf(edge.transition.getSecond()));
                    return true;
                }

            };
        }

    }
}
