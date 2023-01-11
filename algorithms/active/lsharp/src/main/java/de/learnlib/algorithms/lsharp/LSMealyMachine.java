package de.learnlib.algorithms.lsharp;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.automatalib.automata.concepts.InputAlphabetHolder;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.commons.util.Pair;
import net.automatalib.words.Alphabet;

public class LSMealyMachine<I, O> implements InputAlphabetHolder<I>, MealyMachine<LSState, I, Pair<LSState, I>, O> {
    private List<LSState> states;
    private LSState initialState;
    private Alphabet<I> inputAlphabet;
    private HashMap<Pair<LSState, I>, Pair<LSState, O>> transFunction;

    public LSMealyMachine(Alphabet<I> inputAlphabet, List<LSState> states, LSState initialState,
            HashMap<Pair<LSState, I>, Pair<LSState, O>> transFunction) {
        this.states = states;
        this.initialState = initialState;
        this.inputAlphabet = inputAlphabet;
        this.transFunction = transFunction;
    }

    public <S extends Comparable<S>> LSMealyMachine(Alphabet<I> inputAlphabet, MealyMachine<S, I, ?, O> mealy) {
        this.inputAlphabet = inputAlphabet;
        this.transFunction = new HashMap<>();

        HashMap<S, LSState> toState = new HashMap<>();
        toState.put(mealy.getInitialState(), new LSState(0));
        mealy.getStates().stream().forEach(s -> toState.computeIfAbsent(s, k -> new LSState(toState.size())));
        states = new LinkedList<>(toState.values());

        for (S s : mealy.getStates()) {
            for (I i : inputAlphabet) {
                LSState p = toState.get(s);
                O o = mealy.getOutput(s, i);
                LSState q = toState.get(mealy.getSuccessor(s, i));
                transFunction.put(Pair.of(p, i), Pair.of(q, o));
            }
        }

        this.initialState = toState.get(mealy.getInitialState());
    }

    @Override
    public Collection<Pair<LSState, I>> getTransitions(LSState state, I input) {
        Pair<LSState, I> trans = getTransition(state, input);
        return trans == null ? Collections.emptySet() : Collections.singleton(trans);
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
    public Alphabet<I> getInputAlphabet() {
        return this.inputAlphabet;
    }

    @Override
    public O getTransitionOutput(Pair<LSState, I> transition) {
        if (transFunction.containsKey(transition)) {
            return transFunction.get(transition).getSecond();
        }

        return null;
    }

}
