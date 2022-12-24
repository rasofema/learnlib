package de.learnlib.algorithms.lsharp.ads;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;

public class ADSNode<S, I, O> {
    public List<S> initial;
    public List<S> current;
    public @Nullable I input;
    public HashMap<O, Integer> children;

    public ADSNode(List<S> initial, List<S> current) {
        this.initial = initial;
        this.current = current;
        this.children = new HashMap<>();
    }

    public ADSNode(Stream<S> block) {
        this.initial = block.collect(Collectors.toList());
        this.current = new LinkedList<>(this.initial);
        this.children = new HashMap<>();
    }
}
