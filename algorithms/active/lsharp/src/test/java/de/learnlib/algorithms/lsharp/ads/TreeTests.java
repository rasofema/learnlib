package de.learnlib.algorithms.lsharp.ads;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.testng.Assert;

import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.serialization.InputModelDeserializer;
import net.automatalib.serialization.dot.DOTParsers;

public class TreeTests {

    public static void allStatesSplit() throws IOException {
        InputModelDeserializer<String, CompactMealy<String, String>> parser = DOTParsers
                .mealy(new CompactMealy.Creator<String, String>(), DOTParsers.DEFAULT_MEALY_EDGE_PARSER);
        CompactMealy<String, String> target = parser.readModel(
                new File("/Users/tiferrei/Developer/LearningBenchmark/lsharp/tests/src_models/hypothesis_6.dot")).model;
        LinkedList<Integer> label = new LinkedList<>(target.getStates());
        SplittingTree<Integer, String, String> sTree = new SplittingTree<>(target, target.getInputAlphabet(), label);
        for (Integer x : new LinkedList<>(target.getStates())) {
            for (Integer y : new LinkedList<>(target.getStates())) {
                LinkedList<Integer> block = new LinkedList<>();
                block.add(x);
                block.add(y);
                Assert.assertNotNull(sTree.getLCA(block));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        allStatesSplit();
    }
}
