package de.learnlib.algorithms.lsharp.ads;

import java.util.LinkedList;

import org.testng.Assert;
import org.testng.annotations.Test;

import de.learnlib.algorithms.lsharp.LSState;
import de.learnlib.algorithms.lsharp.NormalObservationTree;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

public class ADSTreeTest {
    private final Alphabet<Integer> alpha = Alphabets.integers(0, 12);
    private final Integer[][] inputSeqs = { { 0 }, { 1 }, { 2 }, { 3 }, { 4 }, { 5 }, { 6 }, { 7 }, { 8 }, { 9 },
            { 10 }, { 11 }, { 12 },
            { 2, 3, 8, 8, 8, 1, 12, 7, 4, 7, 5, 0, 2, 7, 6, 6, 6, 8, 3, 12, 11, 8, 8, 7, 3, 2, }, };
    private final Integer[][] outputSeqs = { { 0 }, { 0 }, { 0 }, { 0 }, { 0 }, { 1 }, { 0 }, { 1 }, { 0 }, { 2 },
            { 1 }, { 2 }, { 1 }, { 0, 3, 3, 3, 3, 3, 1, 1, 3, 1, 1, 3, 3, 1, 3, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, } };

    @Test
    public void adsRegression() {
        NormalObservationTree<Integer, Integer> tree = new NormalObservationTree<>(alpha);
        for (int i = 0; i < inputSeqs.length; i++) {
            tree.insertObservation(null, Word.fromSymbols(inputSeqs[i]), Word.fromSymbols(outputSeqs[i]));
        }

        LinkedList<LSState> block = new LinkedList<>();
        block.add(new LSState(0));
        block.add(new LSState(3));
        ADSTree<LSState, Integer, Integer> x = new ADSTree<>(tree, block, Integer.MAX_VALUE);

        Assert.assertTrue(x.getScore() == 2);
    }
}
