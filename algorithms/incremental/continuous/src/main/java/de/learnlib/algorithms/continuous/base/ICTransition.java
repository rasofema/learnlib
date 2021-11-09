package de.learnlib.algorithms.continuous.base;

import java.util.Objects;

public final class ICTransition<I, D> {
    private final ICState<I, D> start;
    private final I input;

    public ICTransition(ICState<I, D> start, I input) {
        this.start = start;
        this.input = input;
    }

    public ICState<I, D> getStart() {
        return start;
    }

    public I getInput() {
        return input;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ICTransition<?, ?> that = (ICTransition<?, ?>) o;
        return Objects.equals(start, that.start) && Objects.equals(input, that.input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, input);
    }
}
