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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.DeterministicAutomaton;
import net.automatalib.automata.FiniteAlphabetAutomaton;
import net.automatalib.automata.fsa.DFA;
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

    protected final Set<ICState<I, D>> states = new HashSet<>();
    private final Alphabet<I> alphabet;
    protected ICState<I, D> initialState;

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
        ICTransition<I, D> trans = getInternalTransition(state, input);
        return trans == null ? null : mapTransition(trans);
    }

    /**
     * Retrieves the <i>internal</i> transition (i.e., the {@link ICTransition} object) for a given state and input.
     * This method is required since the {@link DFA} interface requires the return value of {@link
     * #getTransition(ICState, Object)} to refer to the successor state directly.
     *
     * @param state
     *         the source state
     * @param input
     *         the input symbol triggering the transition
     *
     * @return the transition object
     */
    public ICTransition<I, D> getInternalTransition(ICState<I, D> state, I input) {
        int inputIdx = alphabet.getSymbolIndex(input);
        return getInternalTransition(state, inputIdx);
    }

    public ICTransition<I, D> getInternalTransition(ICState<I, D> start, int input) {
        for (ICState<I, D> state : states) {
            for (ICTransition<I, D> trans : state.incoming) {
                if (trans.getStart().equals(start) && trans.getInput().equals(alphabet.getSymbol(input))) {
                    return trans;
                }
            }
        }
        return null;
    }

    protected abstract T mapTransition(ICTransition<I, D> internalTransition);

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
        return Collections.unmodifiableSet(states);
    }

    @Override
    public int size() {
        return states.size();
    }

    public void setInitial(ICState<I, D> initial, boolean accepting) {
        states.add(initial);
        initialState = initial;
        initialState.accepting = accepting;
    }

    public void setAccepting(ICState<I, D> state, boolean accepting) {
        states.add(state);
        state.accepting = accepting;
    }

    public void addTransition(ICState<I, D> start, I input, ICState<I, D> dest) {
        states.add(start);
        states.add(dest);
        dest.addIncoming(new ICTransition<>(start, input));
    }

    public static final class ICEdge<I, D> {

        public final ICTransition<I, D> transition;
        public final ICState<I, D> target;

        public ICEdge(ICTransition<I, D> transition, ICState<I, D> target) {
            this.transition = transition;
            this.target = target;
        }
    }

    public class GraphView implements Graph<ICState<I, D>, ICEdge<I, D>> {

        @Override
        public Collection<ICState<I, D>> getNodes() {
            return states;
        }

        @Override
        public Collection<ICEdge<I, D>> getOutgoingEdges(ICState<I, D> node) {
            List<ICEdge<I, D>> result = new ArrayList<>();
            for (ICTransition<I, D> trans : node.incoming) {
                result.add(new ICEdge<>(trans, node));
            }
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
                    properties.put(EdgeAttrs.LABEL, String.valueOf(edge.transition.getInput()));
                    return true;
                }

            };
        }

    }
}
