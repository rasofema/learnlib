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
package de.learnlib.algorithms.continuous.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.continuous.dfa.ContinuousDFA;
import de.learnlib.algorithms.continuous.dfa.DFACacheOracle;
import de.learnlib.algorithms.ikv.dfa.IKearnsVaziraniDFA;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFAState;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.membership.MutatingSimulatorOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.commons.util.Pair;
import net.automatalib.commons.util.Triple;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.FastAlphabet;
import net.automatalib.words.impl.Symbol;

public class LearningBenchmark {
    private static final Alphabet<Symbol> ALPHABET = new FastAlphabet<>(new Symbol("0"), new Symbol("1"), new Symbol("2"));
    private static final PhiMetric<Symbol> PD = new PhiMetric<>(ALPHABET, 0.999);
    private static final Random RAND = new Random();

    private static Word<Symbol> sampleWord() {
        double ALPHA = 0.9;
        if (RAND.nextFloat() < ALPHA) {
            List<Symbol> alphas = new LinkedList<>(ALPHABET);
            Collections.shuffle(alphas, RAND);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }
    private static DefaultQuery<Symbol, Boolean> findCex(CompactDFA<Symbol> hyp, Counter counter, MembershipOracle.DFAMembershipOracle<Symbol> oracle, int limit) {
        Word<Symbol> input = sampleWord();
        Boolean output = oracle.answerQuery(input);
        while (hyp.computeOutput(input) == output) {
            if (counter.getCount() > limit) {
                return null;
            }
            input = sampleWord();
            output = oracle.answerQuery(input);
        }
        return new DefaultQuery<>(input, output);
    }

    private static Pair<KearnsVaziraniDFAState<Symbol>, List<Pair<Integer, CompactDFA<Symbol>>>> learnClassic(MembershipOracle.DFAMembershipOracle<Symbol> oracle, int limit) {
        List<Pair<Integer, CompactDFA<Symbol>>> results = new LinkedList<>();
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACacheOracle<Symbol> cacheOracle = new DFACacheOracle<>(queryOracle);
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(cacheOracle, "Number of membership queries");
        KearnsVaziraniDFA<Symbol> learner = new KearnsVaziraniDFA<>(ALPHABET, memOracle, false, AcexAnalyzers.BINARY_SEARCH_BWD);

        learner.startLearning();
        results.add(Pair.of((int) queryOracle.getCount(), new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel())));
        DefaultQuery<Symbol, Boolean> cex = findCex(new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel()), queryOracle.getCounter(), cacheOracle, limit);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) queryOracle.getCount(), new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel())));
            cex = findCex(new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel()), queryOracle.getCounter(), cacheOracle, limit);
        }

        return Pair.of(learner.suspend(), results);
    }

    private static List<Pair<Integer, CompactDFA<Symbol>>> learnIncremental(KearnsVaziraniDFAState<Symbol> state, MembershipOracle.DFAMembershipOracle<Symbol> oracle, int limit) {
        List<Pair<Integer, CompactDFA<Symbol>>> results = new LinkedList<>();
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACacheOracle<Symbol> cacheOracle = new DFACacheOracle<>(queryOracle);
        DFACounterOracle<Symbol> memOracle = new DFACounterOracle<>(cacheOracle, "Number of membership queries");
        IKearnsVaziraniDFA<Symbol> learner = new IKearnsVaziraniDFA<>(ALPHABET, memOracle, AcexAnalyzers.BINARY_SEARCH_FWD, state);

        learner.startLearning();
        results.add(Pair.of((int) queryOracle.getCount(), new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel())));
        DefaultQuery<Symbol, Boolean> cex = findCex(new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel()), queryOracle.getCounter(), cacheOracle, limit);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) queryOracle.getCount(), new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel())));
            cex = findCex(new CompactDFA<>((CompactDFA<Symbol>) learner.getHypothesisModel()), queryOracle.getCounter(), cacheOracle, limit);
        }

        return results;
    }

    private static List<Pair<Integer, CompactDFA<Symbol>>>  learnContinuous(MembershipOracle.DFAMembershipOracle<Symbol> oracle, int limit) {
        DFACounterOracle<Symbol> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");

        ContinuousDFA<Symbol> learner = new ContinuousDFA<>(ALPHABET, 0.9, queryOracle, RAND);
        return learner.learn(limit, limit / 2 / 100);
    }

    public static void runClassic(List<CompactDFA<Symbol>> targets, int limit) {
        System.out.println("=== CLASSIC ===");
        for (CompactDFA<Symbol> target : targets) {
            Pair<KearnsVaziraniDFAState<Symbol>, List<Pair<Integer, CompactDFA<Symbol>>>> learnerRes = learnClassic(new SimulatorOracle.DFASimulatorOracle<>(target), limit);
            List<Pair<Integer, Double>> classic = learnerRes.getSecond().stream()
                .parallel()
                .map(p -> Pair.of(p.getFirst(), PD.sim(target, p.getSecond())))
                .collect(Collectors.toList());
            List<Double> run = new LinkedList<>();
            for (int i = 0; i < classic.size() - 1; i++) {
                while (run.size() < classic.get(i + 1).getFirst()) {
                    run.add(classic.get(i).getSecond());
                }
            }

            run = run.stream().limit(limit).collect(Collectors.toList());
            while (run.size() < limit) {
                run.add(classic.get(classic.size() - 1).getSecond());
            }

            for (Double metric : run) {
                System.out.println(metric.toString());
            }
        }
    }

    public static void runIncremental(List<CompactDFA<Symbol>> targets, int limit) {
        System.out.println("=== INCREMENTAL ===");
        Pair<KearnsVaziraniDFAState<Symbol>, List<Pair<Integer, CompactDFA<Symbol>>>> learnRes = learnClassic(new SimulatorOracle.DFASimulatorOracle<>(targets.get(0)), limit);
        List<Pair<Integer, Double>> classic = learnRes.getSecond().stream()
            .parallel()
            .map(p -> Pair.of(p.getFirst(), PD.sim(targets.get(0), p.getSecond())))
            .collect(Collectors.toList());
        List<Double> runClassic = new LinkedList<>();
        for (int i = 0; i < classic.size() - 1; i++) {
            while (runClassic.size() < classic.get(i + 1).getFirst()) {
                runClassic.add(classic.get(i).getSecond());
            }
        }

        runClassic = runClassic.stream().limit(limit).collect(Collectors.toList());
        while (runClassic.size() < limit) {
            runClassic.add(classic.get(classic.size() - 1).getSecond());
        }

        for (Double metric : runClassic) {
            System.out.println(metric.toString());
        }

        List<Pair<Integer, CompactDFA<Symbol>>> result = learnIncremental(learnRes.getFirst(), new SimulatorOracle.DFASimulatorOracle<>(targets.get(1)), limit);
        List<Pair<Integer, Double>> incremental = result.stream()
            .parallel()
            .map(p -> Pair.of(p.getFirst(), PD.sim(targets.get(1), p.getSecond())))
            .collect(Collectors.toList());
        List<Double> run = new LinkedList<>();
        for (int i = 0; i < incremental.size() - 1; i++) {
            while (run.size() < incremental.get(i + 1).getFirst()) {
                run.add(incremental.get(i).getSecond());
            }
        }

        run = run.stream().limit(limit).collect(Collectors.toList());
        while (run.size() < limit) {
            run.add(incremental.get(incremental.size() - 1).getSecond());
        }

        for (Double metric : run) {
            System.out.println(metric.toString());
        }
    }

    public static void runContinuous(List<CompactDFA<Symbol>> targets, int limit) {
        MutatingSimulatorOracle.DFAMutatingSimulatorOracle<Symbol> ORACLE = new MutatingSimulatorOracle.DFAMutatingSimulatorOracle<>(limit, targets);
        System.out.println("=== CONTINUOUS ===");
        List<Pair<Integer, CompactDFA<Symbol>>> result = learnContinuous(ORACLE, limit * targets.size());
        List<Pair<Integer, Double>> dfas = result.stream()
            .parallel()
            .map(p -> Pair.of(p.getFirst(), PD.sim((CompactDFA<Symbol>) ORACLE.getTarget(p.getFirst()), p.getSecond())))
            .collect(Collectors.toList());
        List<Double> run = new LinkedList<>();
        for (int i = 0; i < dfas.size() - 1; i++) {
            while (run.size() < dfas.get(i + 1).getFirst()) {
                run.add(dfas.get(i).getSecond());
            }
        }

        run = run.stream().limit(limit * targets.size()).collect(Collectors.toList());
//        assert run.get(run.size() - 1).equals(1.0);
        while (run.size() < limit * targets.size()) {
            run.add(dfas.get(dfas.size() - 1).getSecond());
        }

        for (Double metric : run) {
            System.out.println(metric.toString());
        }
    }

    public static CompactDFA<Symbol> randomAutomatonGen(int size) {
        CompactDFA<Symbol> aut = new RandomAutomata(RAND).randomDFA(size, ALPHABET, true);
        while (aut.size() != size) {
            aut = new RandomAutomata(RAND).randomDFA(size, ALPHABET, true);
        }
        return aut;
    }

    public static CompactDFA<Symbol> randomTransMutation(CompactDFA<Symbol> source) {
        CompactDFA<Symbol> aut = new CompactDFA<>(source);
        Integer stateFrom = RAND.nextInt(source.size() - 1);
        Integer stateTo = RAND.nextInt(source.size() - 1);
        Symbol symbol = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));

        aut.removeTransition(stateFrom, symbol, aut.getTransition(stateFrom, symbol));
        aut.addTransition(stateFrom, symbol, stateTo);

        return aut;
    }

    public static CompactDFA<Symbol> randomAcceptanceMutation(CompactDFA<Symbol> source) {
        CompactDFA<Symbol> aut = new CompactDFA<>(source);
        Integer state = RAND.nextInt(source.size() - 1);
        aut.setAccepting(state, !aut.isAccepting(state));
        return aut;
    }

    public static CompactDFA<Symbol> randomAddStateMutation(CompactDFA<Symbol> source) {
        CompactDFA<Symbol> aut = new CompactDFA<>(source);
        Integer state = aut.addState(RAND.nextBoolean());

        for (Symbol a : source.getInputAlphabet()) {
            Integer sourceState = RAND.nextInt(aut.size() - 1);
            aut.removeTransition(sourceState, a, aut.getTransition(sourceState, a));
            aut.addTransition(sourceState, a, state);
        }

        for (Symbol a : source.getInputAlphabet()) {
            aut.addTransition(state, a, RAND.nextInt(aut.size() - 1));
        }

        return aut;
    }

    public static CompactDFA<Symbol> randomRemoveStateMutation(CompactDFA<Symbol> source) {
        CompactDFA<Symbol> aut = new CompactDFA<>(source);

        Integer state = aut.getInitialState();
        while (Objects.equals(state, aut.getInitialState())) {
            state = RAND.nextInt(aut.size() - 1);
        }

        Set<Triple<Integer, Symbol, Integer>> transitions = new HashSet<>();
        for (Integer sourceState : aut.getStates()) {
            for (Symbol a : aut.getInputAlphabet()) {
                Integer targetState = aut.getTransition(sourceState, a);
                assert targetState != null;
                if (targetState.equals(state)) {
                    transitions.add(Triple.of(sourceState, a, targetState));
                }
            }
        }

        for (Triple<Integer, Symbol, Integer> t : transitions) {
            Integer newTarget = state;
            while (newTarget.equals(state)) {
                newTarget = RAND.nextInt(aut.size() - 1);
            }

            aut.removeTransition(t.getFirst(), t.getSecond(), t.getThird());
            aut.addTransition(t.getFirst(), t.getSecond(), newTarget);
        }

        return aut;
    }

    public static CompactDFA<Symbol> randomAddFeature(CompactDFA<Symbol> source, int featureSize) {
        CompactDFA<Symbol> aut = new CompactDFA<>(source);
        CompactDFA<Symbol> feature = randomAutomatonGen(featureSize);

        Set<Triple<Integer, Symbol, Integer>> sourceTransitions = new HashSet<>();
        for (int i = 0; i < aut.size() / 10; i++) {
            for (Symbol a : aut.getInputAlphabet()) {
                Integer sourceState = RAND.nextInt(aut.size() - 1);
                sourceTransitions.add(Triple.of(sourceState, a, aut.getTransition(sourceState, a)));
            }
        }

        Map<Integer, Integer> oldToNew = new HashMap<>();
        for (Integer oldState : feature.getStates()) {
            Integer newState = aut.addState(feature.isAccepting(oldState));
            oldToNew.put(oldState, newState);
        }

        for (Integer oldState : feature.getStates()) {
            for (Symbol a : feature.getInputAlphabet()) {
                aut.addTransition(oldToNew.get(oldState), a, oldToNew.get(feature.getTransition(oldState, a)));
            }
        }

        for (Triple<Integer, Symbol, Integer> t : sourceTransitions) {
            aut.removeTransition(t.getFirst(), t.getSecond(), t.getThird());
            aut.addTransition(t.getFirst(), t.getSecond(), oldToNew.get(feature.getInitialState()));
        }

        return aut;
    }

    public static void benchmark(CompactDFA<Symbol> base, CompactDFA<Symbol> target, int limit) {
        List<CompactDFA<Symbol>> targets = new ArrayList<>(2);
        targets.add(base);
        targets.add(target);

//        runClassic(targets, limit);
//        runIncremental(targets, limit);
        runContinuous(targets, limit);
    }

    public static void benchmarkMutation(int size, int limit) {
        CompactDFA<Symbol> base = randomAutomatonGen(size);

        CompactDFA<Symbol> mutateTrans = randomTransMutation(base);
        CompactDFA<Symbol> mutateAcceptance = randomAcceptanceMutation(mutateTrans);
        CompactDFA<Symbol> mutateAddState = randomAddStateMutation(mutateAcceptance);
        CompactDFA<Symbol> mutation = randomRemoveStateMutation(mutateAddState);

        benchmark(base, mutation, limit);
    }

    public static void benchmarkFeature(int size, int limit) {
        CompactDFA<Symbol> base = randomAutomatonGen(size);
        CompactDFA<Symbol> baseWithFeature = randomAddFeature(base, 3);

        benchmark(base, baseWithFeature, limit);
    }

    private static double calculateSD(double[] runs) {
        double sum = 0.0, standardDeviation = 0.0;
        int length = runs.length;
        for(double num : runs) {
            sum += num;
        }
        double mean = sum/length;
        for(double num: runs) {
            standardDeviation += Math.pow(num - mean, 2);
        }
        return Math.sqrt(standardDeviation/length);
    }

    private static double computeSim(int size) {
        CompactDFA<Symbol> t0 = randomAutomatonGen(size);
        CompactDFA<Symbol> t1 = randomAutomatonGen(size);
        return PD.sim(t0, t1);
    }

    private static double[] averageSim(int n, int size) {
        ConcurrentHashMap<Integer, Double> metric = new ConcurrentHashMap<>();
        IntStream.range(0, n).parallel()
            .forEach(i -> metric.put(i, computeSim(size)));
        double[] runs = new double[n];
        for (int i  = 0; i < n; i++) {
            runs[i] = metric.get(i);
        }
        return runs;
    }

    public static void benchmarkAverage(int n, int size) {
        double[] results = averageSim(n, size);
        System.out.println("AVER: " + Arrays.stream(results).average().orElse(0.0));
        System.out.println("DEV: " + calculateSD(results));
    }

    public static void main(String[] args) {
        long seed = System.nanoTime();
        RAND.setSeed(seed);
        System.out.println("# SEED: " + seed);

        int baseSize = Integer.parseInt(args[0]);
        int limit = Integer.parseInt(args[2]);

        switch (args[1]) {
            case "MUT":
                benchmarkMutation(baseSize, limit);
                break;
            case "FEAT":
                benchmarkFeature(baseSize, limit);
                break;
            default:
                benchmarkAverage(limit, baseSize);
                break;
        }
    }
}
