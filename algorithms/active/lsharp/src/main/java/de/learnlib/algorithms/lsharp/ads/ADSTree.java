package de.learnlib.algorithms.lsharp.ads;

import java.util.List;

import net.automatalib.automata.transducers.MealyMachine;

public class ADSTree<S, I, O> {
    public ArenaTree<ADSNode<S, I, O>, Void> tree;
    public List<Integer> roots;
    public Integer initialIndex;
    public Integer currentIndex;
    public Long seed;

    public ADSTree(MealyMachine<S, I, ?, O> mealy, SplittingTree splittingTree, List<S> initialLabel, Long seed) {

    }
}
