package de.learnlib.algorithms.lsharp.ads;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.automatalib.automata.transducers.MealyMachine;

enum SplittingType {
    SEP_INJ, SEP_NON_INJ, XFER_INJ, XFER_NON_INJ, USELESS;
}

public class SplittingNode<S, I, O> {
    public List<S> label;
    public HashMap<O, Integer> children = new HashMap<>();
    public HashMap<I, List<S>> successors = new HashMap<>();
    public SepSeq<I> sepSeq = new SepSeq<>(null, new LinkedList<>());
    public HashMap<I, SplittingType> splitMap = new HashMap<>();

    public SplittingNode(List<S> block) {
        this.label = new LinkedList<>(new HashSet<>(block));
    }

    public List<I> inputsOfType(SplittingType type) {
        return this.splitMap.entrySet().stream().filter(e -> e.getValue().equals(type)).map(e -> e.getKey())
                .collect(Collectors.toList());
    }

    public boolean hasState(S state) {
        return this.label.contains(state);
    }

    public boolean isSeparated() {
        return !this.children.isEmpty();
    }

    public Integer size() {
        return this.label.size();
    }

    public Void analyse(MealyMachine<S, I, ?, O> mealy) {
        // TODO: analyse function.
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SplittingNode<?, ?, ?>) {
            SplittingNode<?, ?, ?> casted = (SplittingNode<?, ?, ?>) other;
            boolean labelsEq = this.label.equals(casted.label);
            if (!labelsEq) {
                return false;
            }

            boolean seqEq = this.sepSeq.equals(casted.sepSeq);
            if (!seqEq) {
                return false;
            }

            Set<O> selfOuts = this.children.keySet();
            Set<?> otherOuts = casted.children.keySet();
            boolean outsEq = selfOuts.equals(otherOuts);

            if (!outsEq) {
                return false;
            }

            return true;
        }

        return false;
    }
}
