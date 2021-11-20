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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.continuous.util.PhiMetric;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.equivalence.DFARandomWordsEQOracle;
import de.learnlib.oracle.membership.MutatingSimulatorOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import de.learnlib.util.Experiment;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.commons.util.Pair;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.FastAlphabet;
import net.automatalib.words.impl.Symbol;
import org.checkerframework.checker.units.qual.K;
import org.testng.annotations.Test;

@Test
public class LearningBenchmark {
    private static final Alphabet<Symbol> ALPHABET = new FastAlphabet<>(new Symbol("0"), new Symbol("1"), new Symbol("2"));

    private static int testLearnModel(DFA<?, Symbol> target, Alphabet<Symbol> alphabet,
                                     KearnsVaziraniDFA<Symbol> learner,
                                     EquivalenceOracle<? super DFA<?, Symbol>, Symbol, Boolean> eqOracle) {

        Experiment.DFAExperiment<Symbol> experiment = new Experiment.DFAExperiment<Symbol>(learner, eqOracle, alphabet);

        experiment.setLogModels(true);
        experiment.setProfile(true);

        experiment.run();

        assert experiment.getFinalHypothesis().size() == DFAs.minimize(target, alphabet).size();
        assert DFAs.acceptsEmptyLanguage(DFAs.xor(experiment.getFinalHypothesis(), DFAs.minimize(target, alphabet), alphabet));
        return (int) experiment.getRounds().getCount();
    }

    private static Word<Symbol> sampleWord() {
        if ((new Random()).nextFloat() < 0.9) {
            List<Symbol> alphas = new LinkedList<>(ALPHABET);
            Collections.shuffle(alphas);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }
    private static DefaultQuery<Symbol, Boolean> findCex(CompactDFA<Symbol> hyp, Counter counter, MembershipOracle.DFAMembershipOracle<Symbol> oracle) {
        Word<Symbol> input = sampleWord();
        Boolean output = oracle.answerQuery(input);
        while (hyp.computeOutput(input) == output) {
            if (counter.getCount() > 4000) {
                return null;
            }
            input = sampleWord();
            output = oracle.answerQuery(input);
        }
        return new DefaultQuery<>(input, output);
    }

    private static List<Pair<Integer, CompactDFA<Symbol>>> learnClassic(MembershipOracle.DFAMembershipOracle<Symbol> oracle, int limit) {
        List<Pair<Integer, CompactDFA<Symbol>>> results = new LinkedList<>();
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACacheOracle<Symbol> cacheOracle = new DFACacheOracle<>(queryOracle);
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(cacheOracle, "Number of membership queries");
        KearnsVaziraniDFA<Symbol> learner = new KearnsVaziraniDFA<>(ALPHABET, memOracle, false, false, AcexAnalyzers.BINARY_SEARCH_BWD);

        learner.startLearning();
        results.add(Pair.of((int) queryOracle.getCount(), new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel())));
        DefaultQuery<Symbol, Boolean> cex = findCex(new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel()), queryOracle.getCounter(), cacheOracle);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) queryOracle.getCount(), new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel())));
            cex = findCex(new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel()), queryOracle.getCounter(), cacheOracle);
        }

        return results;
    }

    private static List<CompactDFA<Symbol>> learnContinuous(MembershipOracle.DFAMembershipOracle<Symbol> oracle) {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(queryOracle, "Number of membership queries");

        ContinuousDFA<Symbol> learner = new ContinuousDFA<>(ALPHABET, 0.9, memOracle);
        return learner.learn(8000);
    }

    private List<Double> benchmark(Random RAND) {
        List<CompactDFA<Symbol>> targets = new LinkedList<>();
        for (int i = 0; i < 2; i++) {
            targets.add((new RandomAutomata(RAND)).randomDFA(10, ALPHABET));
        }
        MutatingSimulatorOracle.DFAMutatingSimulatorOracle<Symbol> ORACLE = new MutatingSimulatorOracle.DFAMutatingSimulatorOracle<>(2000, targets);

        List<CompactDFA<Symbol>> dfas = learnContinuous(ORACLE);

        List<Double> metrics = new LinkedList<>();
        PhiMetric<Symbol> pd = new PhiMetric<>(ALPHABET, 0.9);
        for (int i = 0; i < dfas.size(); i++) {
            metrics.add(pd.sim((CompactDFA<Symbol>) ORACLE.getTarget(i), dfas.get(i)));
        }

        assert metrics.stream().filter(m -> m == 1.0).count() >= targets.size();
        return metrics;
    }

    private List<Double> acceptanceMutation(Random RAND) {
        List<CompactDFA<Symbol>> targets = new LinkedList<>();
        targets.add((new RandomAutomata(RAND)).randomDFA(10, ALPHABET));

        CompactDFA<Symbol> t1 = new CompactDFA<>(targets.get(0));
        Integer state = RAND.nextInt(11);
        t1.setAccepting(state, !t1.isAccepting(state));
        targets.add(t1);

        MutatingSimulatorOracle.DFAMutatingSimulatorOracle<Symbol> ORACLE = new MutatingSimulatorOracle.DFAMutatingSimulatorOracle<>(2000, targets);

        List<CompactDFA<Symbol>> dfas = learnContinuous(ORACLE);

        List<Double> metrics = new LinkedList<>();
        PhiMetric<Symbol> pd = new PhiMetric<>(ALPHABET, 0.9);
        for (int i = 0; i < dfas.size(); i++) {
            metrics.add(pd.sim((CompactDFA<Symbol>) ORACLE.getTarget(i), dfas.get(i)));
        }

        assert metrics.stream().filter(m -> m == 1.0).count() >= targets.size();
        return metrics;
    }

    private static Pair<List<Double>, List<Double>> transitionMutation(Random RAND) {
        List<CompactDFA<Symbol>> targets = new LinkedList<>();
        targets.add((new RandomAutomata(RAND)).randomDFA(50, ALPHABET));

        CompactDFA<Symbol> t1 = new CompactDFA<>(targets.get(0));
        Integer stateFrom = RAND.nextInt(51);
        Integer stateTo = RAND.nextInt(51);
        Symbol symbol = ALPHABET.getSymbol(RAND.nextInt(ALPHABET.size()));

        t1.removeTransition(stateFrom, symbol, t1.getTransition(stateFrom, symbol));
        t1.addTransition(stateFrom, symbol, stateTo);

        targets.add(t1);

        PhiMetric<Symbol> pd = new PhiMetric<>(ALPHABET, 0.9);
        List<Double> classicMetrics = new LinkedList<>();
        for (CompactDFA<Symbol> target : targets) {
            List<Pair<Integer, Double>> classic = learnClassic(new SimulatorOracle.DFASimulatorOracle<>(target), 4000).stream()
                .parallel()
                .map(p -> Pair.of(p.getFirst(), pd.sim(target, p.getSecond())))
                .collect(Collectors.toList());

            List<Double> run = new LinkedList<>();
            for (int i = 0; i < classic.size() - 1; i++) {
                while (run.size() < classic.get(i + 1).getFirst()) {
                    run.add(classic.get(i).getSecond());
                }
            }

            run = run.stream().limit(4000).collect(Collectors.toList());
            while (run.size() < 4000) {
                run.add(classic.get(classic.size() - 1).getSecond());
            }

            classicMetrics.addAll(run);
        }


        MutatingSimulatorOracle.DFAMutatingSimulatorOracle<Symbol> ORACLE = new MutatingSimulatorOracle.DFAMutatingSimulatorOracle<>(4000, targets);

        List<CompactDFA<Symbol>> dfas = learnContinuous(ORACLE);

        // Sample every 5, otherwise too slow.
        ConcurrentHashMap<Integer, Double> metrics = new ConcurrentHashMap<>();
        IntStream.range(0, dfas.size()).boxed()
            .filter(n -> n % 10 == 0)
            .parallel()
            .forEach(i -> metrics.put(i, pd.sim((CompactDFA<Symbol>) ORACLE.getTarget(i), dfas.get(i))));

        List<Double> metricsList = new LinkedList<>();
        TreeMap<Integer, Double> orderedMetrics = new TreeMap<>(metrics);
        for (Double metric : orderedMetrics.values()) {
            for (int j = 0; j < 10; j++) {
                metricsList.add(metric > 0.999 ? 1.0 : metric);
            }
        }

        return Pair.of(classicMetrics, metricsList);
    }

    private static List<Double> average(List<List<Double>> inMetrics) {
        List<List<Double>> allMetrics = new LinkedList<>(inMetrics);
        Map<Integer, Integer> count = new HashMap<>();
        List<Double> average = new LinkedList<>(allMetrics.get(0));
        for (int i = 0; i < allMetrics.get(0).size(); i++) {
            count.put(i, 1);
        }
        allMetrics.remove(0);

        for (List<Double> metric : allMetrics) {
            for (int i = 0; i < metric.size(); i++)
                if (average.size() > i) {
                    average.set(i, average.get(i) + metric.get(i));
                    count.put(i, count.getOrDefault(i, 0) + 1);
                } else {
                    average.add(metric.get(i));
                }
        }

        for (int i = 0; i < average.size(); i++) {
            average.set(i, average.get(i) / count.getOrDefault(i, 0));
        }

        return average;
    }

    @Test
    public static void repeat() {
        Random RAND = new Random();
        long seed = /*RAND.nextLong()*/ 1673067670938585872L;
        System.out.println("SEED: " + seed);
        RAND.setSeed(seed);
        ConcurrentHashMap<Integer, Pair<List<Double>, List<Double>>> allMetrics = new ConcurrentHashMap<>();
        AtomicInteger progress = new AtomicInteger(0);
        IntStream.range(0, 10).boxed()
            .parallel()
            .forEach(i -> {
                System.out.println("ITER: " + progress.incrementAndGet());
                allMetrics.put(i, transitionMutation(RAND));
            });

        List<List<Double>> classicMetrics = allMetrics.values().stream()
            .map(Pair::getFirst).collect(Collectors.toList());
        List<List<Double>> continuousMetrics = allMetrics.values().stream()
            .map(Pair::getSecond).collect(Collectors.toList());

        System.out.println("=== CLASSIC ===");
        average(classicMetrics).forEach(m -> System.out.println(m > 0.999 ? "1.0" : m.toString()));

        System.out.println("=== CONTINUOUS ===");
        average(continuousMetrics).forEach(m -> System.out.println(m > 0.999 ? "1.0" : m.toString()));
    }
}
