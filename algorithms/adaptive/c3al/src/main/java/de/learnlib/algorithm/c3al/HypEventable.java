package de.learnlib.algorithm.c3al;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.automatalib.automaton.transducer.MealyMachine;

public interface HypEventable<I, O> {
    public @Nullable MealyMachine<?, I, ?, O> apply(@Nullable MealyMachine<?, I, ?, O> hyp);
}