/* Copyright (C) 2013-2021 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.algorithms.continuous.dfa;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.continuous.util.PhiMetric;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.equivalence.WpMethodEQOracle;
import de.learnlib.oracle.membership.MutatingSimulatorOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import de.learnlib.util.Experiment;
import net.automatalib.automata.Automaton;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.FastAlphabet;
import net.automatalib.words.impl.Symbol;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class LearningBenchmark {
    private static final Alphabet<Symbol> ALPHABET = new FastAlphabet<>(new Symbol("0"), new Symbol("1"), new Symbol("2"));
    private static Long RAND_SEED = (new Random()).nextLong();
    private static Logger log = Logger.getLogger("LearningBenchmark");

    private static CompactDFA<Symbol> TARGET = (new RandomAutomata(new Random(RAND_SEED))).randomDFA(10, ALPHABET);
    private static MembershipOracle.DFAMembershipOracle<Symbol> ORACLE = new SimulatorOracle.DFASimulatorOracle<>(TARGET);

    private static int testLearnModel(DFA<?, Symbol> target, Alphabet<Symbol> alphabet,
                                     KearnsVaziraniDFA<Symbol> learner,
                                     EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle) {

        Experiment.DFAExperiment<Symbol> experiment = new Experiment.DFAExperiment<Symbol>(learner, eqOracle, alphabet);

        experiment.setLogModels(true);
        experiment.setProfile(true);

        experiment.run();

        Assert.assertEquals(experiment.getFinalHypothesis().size(), DFAs.minimize(target, alphabet).size());
        Assert.assertTrue(DFAs.acceptsEmptyLanguage(DFAs.xor(experiment.getFinalHypothesis(), DFAs.minimize(target, alphabet), alphabet)));
        return (int) experiment.getRounds().getCount();
    }

    private List<CompactDFA<Symbol>> learnContinuous(MembershipOracle.DFAMembershipOracle<Symbol> oracle) {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");

        ContinuousDFA<Symbol> learner = new ContinuousDFA<>(ALPHABET, 0.9, memOracle);
        return learner.learn(8000);
    }

    private List<Double> benchmark(Random RAND) throws IOException {
        List<CompactDFA<Symbol>> targets = new LinkedList<>();
        for (int i = 0; i < 2; i++) {
            targets.add((new RandomAutomata(RAND)).randomDFA(10, ALPHABET));
        }
        MutatingSimulatorOracle.DFAMutatingSimulatorOracle<Symbol> ORACLE = new MutatingSimulatorOracle.DFAMutatingSimulatorOracle<>(2000, targets);

        List<CompactDFA<Symbol>> dfas = learnContinuous(ORACLE);
        writeDotFile(dfas.get(dfas.size() - 1), ALPHABET, "./continuous.dot");

        List<Double> metrics = new LinkedList<>();
        PhiMetric<Symbol> pd = new PhiMetric<>(ALPHABET, 0.9);
        for (int i = 0; i < dfas.size(); i++) {
            metrics.add(pd.sim((CompactDFA<Symbol>) ORACLE.getTarget(i), dfas.get(i)));
        }

        assert metrics.stream().filter(m -> m == 1.0).count() >= targets.size();
        return metrics;
    }

    private List<Double> acceptanceMutation(Random RAND) throws IOException {
        List<CompactDFA<Symbol>> targets = new LinkedList<>();
        targets.add((new RandomAutomata(RAND)).randomDFA(10, ALPHABET));

        CompactDFA<Symbol> t1 = new CompactDFA<>(targets.get(0));
        Integer state = RAND.nextInt(11);
        t1.setAccepting(state, !t1.isAccepting(state));
        targets.add(t1);

        MutatingSimulatorOracle.DFAMutatingSimulatorOracle<Symbol> ORACLE = new MutatingSimulatorOracle.DFAMutatingSimulatorOracle<>(2000, targets);

        List<CompactDFA<Symbol>> dfas = learnContinuous(ORACLE);
        writeDotFile(dfas.get(dfas.size() - 1), ALPHABET, "./continuous.dot");

        List<Double> metrics = new LinkedList<>();
        PhiMetric<Symbol> pd = new PhiMetric<>(ALPHABET, 0.9);
        for (int i = 0; i < dfas.size(); i++) {
            metrics.add(pd.sim((CompactDFA<Symbol>) ORACLE.getTarget(i), dfas.get(i)));
        }

        assert metrics.stream().filter(m -> m == 1.0).count() >= targets.size();
        return metrics;
    }

    private List<Double> transitionMutation(Random RAND) {
        List<CompactDFA<Symbol>> targets = new LinkedList<>();
        targets.add((new RandomAutomata(RAND)).randomDFA(20, ALPHABET));

        CompactDFA<Symbol> t1 = new CompactDFA<>(targets.get(0));
        Integer stateFrom = RAND.nextInt(21);
        Integer stateTo = RAND.nextInt(21);
        Symbol symbol = ALPHABET.getSymbol(RAND.nextInt(ALPHABET.size()));

        t1.removeTransition(stateFrom, symbol, t1.getTransition(stateFrom, symbol));
        t1.addTransition(stateFrom, symbol, stateTo);

        targets.add(t1);

        MutatingSimulatorOracle.DFAMutatingSimulatorOracle<Symbol> ORACLE = new MutatingSimulatorOracle.DFAMutatingSimulatorOracle<>(4000, targets);

        List<CompactDFA<Symbol>> dfas = learnContinuous(ORACLE);

        Map<Integer, Double> metrics = new ConcurrentHashMap<>();
        PhiMetric<Symbol> pd = new PhiMetric<>(ALPHABET, 0.9);
        IntStream.range(0, dfas.size()).boxed()
            .parallel()
            .forEach(i -> metrics.put(i, pd.sim((CompactDFA<Symbol>) ORACLE.getTarget(i), dfas.get(i))));

        List<Double> metricsList = new LinkedList<>();
        for (int i = 0; i < dfas.size(); i++) {
            metricsList.add(metrics.get(i) > 0.999 ? 1.0 : metrics.get(i));
        }

//        assert metricsList.stream().filter(m -> m == 1.0).count() >= targets.size();
        return metricsList;
    }

    public void repeat() {
        Random RAND = new Random();
        long seed = /*RAND.nextLong()*/ 1673067670938585872L;
        log.info("SEED: " + seed);
        RAND.setSeed(seed);
        List<List<Double>> allMetrics = new LinkedList<>();

        AtomicInteger progress = new AtomicInteger(0);
        IntStream.range(0, 200).boxed()
            .parallel()
            .forEach(i -> {
                log.info("ITER: " + progress.incrementAndGet());
                allMetrics.add(transitionMutation(RAND));
            });

        List<Double> averageMetrics = new LinkedList<>(allMetrics.get(0));
        allMetrics.remove(0);

        for (List<Double> metrics : allMetrics) {
            for (int i = 0; i < metrics.size(); i++) {
                averageMetrics.set(i, averageMetrics.get(i) + metrics.get(i));
            }
        }

        for (int i = 0; i < averageMetrics.size(); i++) {
            averageMetrics.set(i, averageMetrics.get(i) / (allMetrics.size() + 1));
        }

        averageMetrics.forEach(m -> log.info(m > 0.999 ? "1.0" : m.toString()));
    }

    // policy : convert into method throwing unchecked exception
    private static <S, I, T> void writeDotFile(Automaton<S, I, T> automaton, Collection<? extends I> inputAlphabet, String filepath) throws IOException {
        BufferedWriter outstream = new BufferedWriter(new FileWriter(filepath));
        try {
            GraphDOT.write(automaton, inputAlphabet, outstream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        outstream.close();
    }
}
