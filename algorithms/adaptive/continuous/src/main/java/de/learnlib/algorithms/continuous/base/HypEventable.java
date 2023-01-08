package de.learnlib.algorithms.continuous.base;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.commons.util.Pair;

public interface HypEventable<I, O> {
    public @Nullable Pair<Integer, MealyMachine<?, I, ?, O>> apply(Pair<Integer, MealyMachine<?, I, ?, O>> hyp);
}