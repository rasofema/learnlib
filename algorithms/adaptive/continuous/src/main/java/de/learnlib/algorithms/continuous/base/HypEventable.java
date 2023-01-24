package de.learnlib.algorithms.continuous.base;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.automatalib.automata.transducers.MealyMachine;

public interface HypEventable<I, O> {
    public @Nullable MealyMachine<?, I, ?, O> apply(@Nullable MealyMachine<?, I, ?, O> hyp);
}