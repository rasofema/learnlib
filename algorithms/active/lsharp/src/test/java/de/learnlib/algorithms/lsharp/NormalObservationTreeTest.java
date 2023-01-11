package de.learnlib.algorithms.lsharp;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.testng.Assert;
import org.testng.annotations.Test;

import de.learnlib.oracle.equivalence.MealyRandomWordsEQOracle;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.commons.util.Pair;
import net.automatalib.serialization.InputModelDeserializer;
import net.automatalib.serialization.dot.DOTParsers;
import net.automatalib.words.Word;

public class NormalObservationTreeTest {

    private LSMealyMachine<String, String> readMealy(String filename) throws IOException {
        InputModelDeserializer<String, CompactMealy<String, String>> parser = DOTParsers
                .mealy(new CompactMealy.Creator<String, String>(), DOTParsers.DEFAULT_MEALY_EDGE_PARSER);
        InputStream res = this.getClass().getResourceAsStream(filename);
        CompactMealy<String, String> target = parser.readModel(res).model;
        return new LSMealyMachine<>(target.getInputAlphabet(), target);
    }

    private List<Pair<Word<String>, Word<String>>> tryGenInputs(LSMealyMachine<String, String> mealy, Integer count) {
        Random rand = new Random();
        rand.setSeed(42);
        MealyRandomWordsEQOracle<String, String> eqOracle = new MealyRandomWordsEQOracle<>(null, 0, 100, count, rand);
        return eqOracle.generateTestWords(mealy, mealy.getInputAlphabet()).map(i -> Pair.of(i, mealy.computeOutput(i)))
                .collect(Collectors.toList());
    }

    @Test
    public void xferSeqMantained() throws IOException {
        LSMealyMachine<String, String> fsm = readMealy("/models/BitVise.dot");
        List<Pair<Word<String>, Word<String>>> tests = tryGenInputs(fsm, 100);
        NormalObservationTree<String, String> ret = new NormalObservationTree<>(fsm.getInputAlphabet());

        for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
            Word<String> is = tests.get(testIndex).getFirst();
            Word<String> os = tests.get(testIndex).getSecond();

            ret.insertObservation(null, is, os);
            Assert.assertNotNull(ret.getSucc(ret.defaultState(), is));
            for (int inIndex = 0; inIndex < testIndex; inIndex++) {
                Word<String> iis = tests.get(inIndex).getFirst();
                LSState ds = ret.getSucc(ret.defaultState(), iis);
                Assert.assertNotNull(ds);
                Word<String> rxAcc = ret.getAccessSeq(ds);
                Assert.assertTrue(iis.equals(rxAcc), "Failed at testIndex " + testIndex + "and inINdex " + inIndex
                        + ", \n after inserting" + is.toString());
                for (int i = 0; i < iis.length(); i++) {
                    Word<String> pref = iis.prefix(i);
                    Word<String> suff = iis.suffix(iis.length() - i);

                    LSState prefDest = ret.getSucc(ret.defaultState(), pref);
                    Assert.assertNotNull(prefDest);
                    Word<String> xferSeq = ret.getTransferSeq(ds, prefDest);
                    Assert.assertTrue(suff.equals(xferSeq));
                }
            }
        }
    }

    @Test
    public void accessSeqMantained() throws IOException {
        LSMealyMachine<String, String> fsm = readMealy("/models/BitVise.dot");
        List<Pair<Word<String>, Word<String>>> tests = tryGenInputs(fsm, 500);
        NormalObservationTree<String, String> ret = new NormalObservationTree<>(fsm.getInputAlphabet());

        for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
            Word<String> is = tests.get(testIndex).getFirst();
            Word<String> os = tests.get(testIndex).getSecond();

            ret.insertObservation(null, is, os);
            Assert.assertNotNull(ret.getSucc(ret.defaultState(), is));
            for (int inIndex = 0; inIndex < testIndex; inIndex++) {
                Word<String> iis = tests.get(inIndex).getFirst();
                LSState ds = ret.getSucc(ret.defaultState(), iis);
                Assert.assertNotNull(ds);
                Word<String> rxAcc = ret.getAccessSeq(ds);
                Assert.assertTrue(iis.equals(rxAcc), "Failed at testIndex " + testIndex + "and inINdex " + inIndex
                        + ", \n after inserting" + is.toString());
            }
        }

        for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
            Word<String> is = tests.get(testIndex).getFirst();
            LSState dest = ret.getSucc(ret.defaultState(), is);
            Assert.assertNotNull(dest, "Seq number " + testIndex + " : " + is + " is not in tree?!");
            Word<String> accSeq = ret.getAccessSeq(dest);
            Assert.assertTrue(is.equals(accSeq));
        }

    }

}
