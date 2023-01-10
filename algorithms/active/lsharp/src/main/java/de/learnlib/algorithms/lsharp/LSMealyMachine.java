package de.learnlib.algorithms.lsharp;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.commons.util.Pair;
import net.automatalib.words.Alphabet;

public class LSMealyMachine<I, O> implements MealyMachine<LSState, I, Pair<LSState, I>, O> {
    private List<LSState> states;
    private LSState initialState;
    private Alphabet<I> inputAlphabet;
    private Alphabet<O> outputAlphabet;
    private HashMap<Pair<LSState, I>, Pair<LSState, O>> transFunction;

    @Override
    public Collection<Pair<LSState, I>> getTransitions(LSState state, I input) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public LSState getSuccessor(Pair<LSState, I> transition) {
        Pair<LSState, O> pair = transFunction.getOrDefault(transition, null);
        if (pair == null) {
            return null;
        }

        return pair.getFirst();
    }

    @Override
    public Collection<LSState> getStates() {
        return states;
    }

    @Override
    public @Nullable LSState getInitialState() {
        return initialState;
    }

    @Override
    public @Nullable LSState getSuccessor(LSState state, I input) {
        return this.getSuccessor(Pair.of(state, input));
    }

    @Override
    public @Nullable Pair<LSState, I> getTransition(LSState state, I input) {
        return Pair.of(state, input);
    }

    @Override
    public Void getStateProperty(LSState state) {
        return null;
    }

    @Override
    public O getTransitionProperty(Pair<LSState, I> transition) {
        Pair<LSState, O> pair = this.transFunction.getOrDefault(transition, null);
        if (pair == null) {
            return null;
        }

        return pair.getSecond();
    }

    @Override
    public O getTransitionOutput(Pair<LSState, I> transition) {
        return this.getTransitionProperty(transition.getFirst(), transition.getSecond());
    }

}
