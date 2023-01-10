package de.learnlib.algorithms.lsharp;

public class LSState implements Comparable<LSState> {
    private final Integer base;

    public LSState(Integer base) {
        this.base = base;
    }

    public Integer raw() {
        return this.base;
    }

    public int compareTo(LSState to) {
        return Integer.compare(this.base, to.raw());
    };
}
