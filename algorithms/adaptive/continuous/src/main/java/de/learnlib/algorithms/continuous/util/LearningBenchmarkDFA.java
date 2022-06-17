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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.continuous.dfa.ContinuousDFA;
import de.learnlib.algorithms.dlstar.dfa.ClassicDLStarDFA;
import de.learnlib.algorithms.incremental.dfa.IKearnsVaziraniDFA;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFAState;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.cache.dfa.DFACacheOracle;
import de.learnlib.filter.cache.dfa.DFACaches;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.filter.statistic.oracle.DFACounterOracle;
import de.learnlib.oracle.membership.MutatingSimulatorOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.commons.util.Pair;
import net.automatalib.commons.util.Triple;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

public class LearningBenchmarkDFA {
    private static final Alphabet<Character> ALPHABET = Alphabets.characters('0', '2');
    private static final PhiMetric<Character> PD = new PhiMetric<>(ALPHABET, 0.999, false);
    private static final Random RAND = new Random();

    private static Word<Character> sampleWord() {
        double ALPHA = 0.9;
        if (RAND.nextFloat() < ALPHA) {
            List<Character> alphas = new LinkedList<>(ALPHABET);
            Collections.shuffle(alphas, RAND);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    private static DefaultQuery<Character, Boolean> findCex(CompactDFA<Character> hyp, Counter counter,
            MembershipOracle.DFAMembershipOracle<Character> oracle, int limit) {
        Word<Character> input = sampleWord();
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

    @SuppressWarnings("unchecked")
    private static Pair<KearnsVaziraniDFAState<Character>, List<Pair<Integer, CompactDFA<Character>>>> learnClassic(
            MembershipOracle.DFAMembershipOracle<Character> oracle, int limit) {
        List<Pair<Integer, CompactDFA<Character>>> results = new LinkedList<>();
        DFACounterOracle<Character> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACacheOracle<Character> cacheOracle = DFACaches.createCache(ALPHABET, queryOracle);
        DFACounterOracle<Character> memOracle = new DFACounterOracle<>(cacheOracle, "Number of membership queries");
        KearnsVaziraniDFA<Character> learner = new KearnsVaziraniDFA<>(ALPHABET, memOracle, false,
                AcexAnalyzers.BINARY_SEARCH_BWD);

        learner.startLearning();
        results.add(Pair.of((int) queryOracle.getCount(),
                new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel())));
        DefaultQuery<Character, Boolean> cex = findCex(
                new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel()), queryOracle.getCounter(),
                cacheOracle, limit);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) queryOracle.getCount(),
                    new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel())));
            cex = findCex(new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel()),
                    queryOracle.getCounter(), cacheOracle, limit);
        }

        return Pair.of(learner.suspend(), results);
    }

    @SuppressWarnings("unchecked")
    private static Pair<ClassicDLStarDFA<Character>, List<Pair<Integer, CompactDFA<Character>>>> learnPDLStar(
            List<Word<Character>> initialPrefixes, List<Word<Character>> initialSuffixes,
            MembershipOracle.DFAMembershipOracle<Character> oracle, int limit) {
        List<Pair<Integer, CompactDFA<Character>>> results = new LinkedList<>();
        DFACounterOracle<Character> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACacheOracle<Character> cacheOracle = DFACaches.createCache(ALPHABET, queryOracle);
        DFACounterOracle<Character> memOracle = new DFACounterOracle<>(cacheOracle, "Number of membership queries");
        ClassicDLStarDFA<Character> learner = new ClassicDLStarDFA<>(ALPHABET, initialPrefixes, initialSuffixes,
                memOracle);
        learner.startLearning();
        results.add(Pair.of((int) queryOracle.getCount(),
                new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel())));
        DefaultQuery<Character, Boolean> cex = findCex(
                new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel()), queryOracle.getCounter(),
                cacheOracle, limit);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) queryOracle.getCount(),
                    new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel())));
            cex = findCex(new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel()),
                    queryOracle.getCounter(), cacheOracle, limit);
        }

        return Pair.of(learner, results);
    }

    @SuppressWarnings("unchecked")
    private static List<Pair<Integer, CompactDFA<Character>>> learnIncremental(KearnsVaziraniDFAState<Character> state,
            MembershipOracle.DFAMembershipOracle<Character> oracle, int limit) {
        List<Pair<Integer, CompactDFA<Character>>> results = new LinkedList<>();
        DFACounterOracle<Character> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");
        DFACacheOracle<Character> cacheOracle = DFACaches.createCache(ALPHABET, queryOracle);
        DFACounterOracle<Character> memOracle = new DFACounterOracle<>(cacheOracle, "Number of membership queries");
        IKearnsVaziraniDFA<Character> learner = new IKearnsVaziraniDFA<>(ALPHABET, memOracle,
                AcexAnalyzers.BINARY_SEARCH_FWD, state);

        learner.startLearning();
        results.add(Pair.of((int) queryOracle.getCount(),
                new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel())));
        DefaultQuery<Character, Boolean> cex = findCex(
                new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel()), queryOracle.getCounter(),
                cacheOracle, limit);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) queryOracle.getCount(),
                    new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel())));
            cex = findCex(new CompactDFA<>((CompactDFA<Character>) learner.getHypothesisModel()),
                    queryOracle.getCounter(), cacheOracle, limit);
        }

        return results;
    }

    private static List<Pair<Integer, CompactDFA<Character>>> learnContinuous(
            MembershipOracle.DFAMembershipOracle<Character> oracle, int limit) {
        DFACounterOracle<Character> queryOracle = new DFACounterOracle<>(oracle, "Number of total queries");

        ContinuousDFA<Character> learner = new ContinuousDFA<>(ALPHABET, 0.9, queryOracle, RAND);
        return learner.learn(limit, limit / 2 / 100);
    }

    public static void runClassic(List<CompactDFA<Character>> targets, int limit) {
        System.out.println("=== CLASSIC ===");
        for (CompactDFA<Character> target : targets) {
            Pair<KearnsVaziraniDFAState<Character>, List<Pair<Integer, CompactDFA<Character>>>> learnerRes = learnClassic(
                    new SimulatorOracle.DFASimulatorOracle<>(target), limit);
            List<Pair<Integer, Double>> classic = learnerRes.getSecond().stream().parallel()
                    .map(p -> Pair.of(p.getFirst(), PD.sim(target, p.getSecond()))).collect(Collectors.toList());
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

    public static void runPDLStar(List<CompactDFA<Character>> targets, int limit) {
        System.out.println("=== PDLStar ===");
        Pair<ClassicDLStarDFA<Character>, List<Pair<Integer, CompactDFA<Character>>>> learnRes = learnPDLStar(
                Collections.singletonList(Word.epsilon()), Collections.singletonList(Word.epsilon()),
                new SimulatorOracle.DFASimulatorOracle<>(targets.get(0)), limit);
        List<Pair<Integer, Double>> classic = learnRes.getSecond().stream().parallel()
                .map(p -> Pair.of(p.getFirst(), PD.sim(targets.get(0), p.getSecond()))).collect(Collectors.toList());
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

        Pair<ClassicDLStarDFA<Character>, List<Pair<Integer, CompactDFA<Character>>>> result = learnPDLStar(
                new LinkedList<>(learnRes.getFirst().getObservationTable().getShortPrefixes()),
                learnRes.getFirst().getObservationTable().getSuffixes(),
                new SimulatorOracle.DFASimulatorOracle<>(targets.get(1)), limit);
        List<Pair<Integer, Double>> incremental = result.getSecond().stream().parallel()
                .map(p -> Pair.of(p.getFirst(), PD.sim(targets.get(1), p.getSecond()))).collect(Collectors.toList());
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

    public static void runIncremental(List<CompactDFA<Character>> targets, int limit) {
        System.out.println("=== INCREMENTAL ===");
        Pair<KearnsVaziraniDFAState<Character>, List<Pair<Integer, CompactDFA<Character>>>> learnRes = learnClassic(
                new SimulatorOracle.DFASimulatorOracle<>(targets.get(0)), limit);
        List<Pair<Integer, Double>> classic = learnRes.getSecond().stream().parallel()
                .map(p -> Pair.of(p.getFirst(), PD.sim(targets.get(0), p.getSecond()))).collect(Collectors.toList());
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

        List<Pair<Integer, CompactDFA<Character>>> result = learnIncremental(learnRes.getFirst(),
                new SimulatorOracle.DFASimulatorOracle<>(targets.get(1)), limit);
        List<Pair<Integer, Double>> incremental = result.stream().parallel()
                .map(p -> Pair.of(p.getFirst(), PD.sim(targets.get(1), p.getSecond()))).collect(Collectors.toList());
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

    public static void runContinuous(List<CompactDFA<Character>> targets, int limit) {
        MutatingSimulatorOracle.DFAMutatingSimulatorOracle<Character> ORACLE = new MutatingSimulatorOracle.DFAMutatingSimulatorOracle<>(
                limit, targets);
        System.out.println("=== CONTINUOUS ===");
        List<Pair<Integer, CompactDFA<Character>>> result = learnContinuous(ORACLE, limit * targets.size());
        List<Pair<Integer, Double>> dfas = result.stream().parallel()
                .map(p -> Pair.of(p.getFirst(),
                        PD.sim((CompactDFA<Character>) ORACLE.getTarget(p.getFirst()), p.getSecond())))
                .collect(Collectors.toList());
        List<Double> run = new LinkedList<>();
        for (int i = 0; i < dfas.size() - 1; i++) {
            while (run.size() < dfas.get(i + 1).getFirst()) {
                run.add(dfas.get(i).getSecond());
            }
        }

        run = run.stream().limit(limit * targets.size()).collect(Collectors.toList());
        assert run.get(run.size() - 1).equals(1.0);
        while (run.size() < limit * targets.size()) {
            run.add(dfas.get(dfas.size() - 1).getSecond());
        }

        for (Double metric : run) {
            System.out.println(metric.toString());
        }
    }

    public static CompactDFA<Character> randomAutomatonGen(int size) {
        CompactDFA<Character> aut = RandomAutomata.randomDFA(RAND, size, ALPHABET, true);
        while (aut.size() != size) {
            aut = RandomAutomata.randomDFA(RAND, size, ALPHABET, true);
        }
        return aut;
    }

    public static CompactDFA<Character> randomTransMutation(CompactDFA<Character> source) {
        CompactDFA<Character> aut = new CompactDFA<>(source);
        Integer stateFrom = RAND.nextInt(source.size() - 1);
        Integer stateTo = RAND.nextInt(source.size() - 1);
        Character symbol = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));

        aut.removeTransition(stateFrom, symbol, aut.getTransition(stateFrom, symbol));
        aut.addTransition(stateFrom, symbol, stateTo);

        return aut;
    }

    public static CompactDFA<Character> randomAcceptanceMutation(CompactDFA<Character> source) {
        CompactDFA<Character> aut = new CompactDFA<>(source);
        Integer state = RAND.nextInt(source.size() - 1);
        aut.setAccepting(state, !aut.isAccepting(state));
        return aut;
    }

    public static CompactDFA<Character> randomAddStateMutation(CompactDFA<Character> source) {
        CompactDFA<Character> aut = new CompactDFA<>(source);
        Integer state = aut.addState(RAND.nextBoolean());

        for (Character a : source.getInputAlphabet()) {
            Integer sourceState = RAND.nextInt(aut.size() - 1);
            aut.removeTransition(sourceState, a, aut.getTransition(sourceState, a));
            aut.addTransition(sourceState, a, state);
        }

        for (Character a : source.getInputAlphabet()) {
            aut.addTransition(state, a, RAND.nextInt(aut.size() - 1));
        }

        return aut;
    }

    public static CompactDFA<Character> randomRemoveStateMutation(CompactDFA<Character> source) {
        CompactDFA<Character> aut = new CompactDFA<>(source);

        Integer state = aut.getInitialState();
        while (Objects.equals(state, aut.getInitialState())) {
            state = RAND.nextInt(aut.size() - 1);
        }

        Set<Triple<Integer, Character, Integer>> transitions = new HashSet<>();
        for (Integer sourceState : aut.getStates()) {
            for (Character a : aut.getInputAlphabet()) {
                Integer targetState = aut.getTransition(sourceState, a);
                assert targetState != null;
                if (targetState.equals(state)) {
                    transitions.add(Triple.of(sourceState, a, targetState));
                }
            }
        }

        for (Triple<Integer, Character, Integer> t : transitions) {
            Integer newTarget = state;
            while (newTarget.equals(state)) {
                newTarget = RAND.nextInt(aut.size() - 1);
            }

            aut.removeTransition(t.getFirst(), t.getSecond(), t.getThird());
            aut.addTransition(t.getFirst(), t.getSecond(), newTarget);
        }

        return aut;
    }

    public static CompactDFA<Character> randomAddFeature(CompactDFA<Character> source, int featureSize) {
        CompactDFA<Character> aut = new CompactDFA<>(source);
        CompactDFA<Character> feature = randomAutomatonGen(featureSize);

        Set<Triple<Integer, Character, Integer>> sourceTransitions = new HashSet<>();
        for (int i = 0; i < featureSize; i++) {
            for (Character a : aut.getInputAlphabet()) {
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
            for (Character a : feature.getInputAlphabet()) {
                aut.addTransition(oldToNew.get(oldState), a, oldToNew.get(feature.getTransition(oldState, a)));
            }
        }

        for (Triple<Integer, Character, Integer> t : sourceTransitions) {
            aut.removeTransition(t.getFirst(), t.getSecond(), t.getThird());
            aut.addTransition(t.getFirst(), t.getSecond(), oldToNew.get(feature.getInitialState()));
        }

        return aut;
    }

    public static void benchmark(CompactDFA<Character> base, CompactDFA<Character> target, int limit) {
        List<CompactDFA<Character>> targets = new ArrayList<>(2);
        targets.add(base);
        targets.add(target);

        runClassic(targets, limit);
        // runPDLStar(targets, limit);
        runIncremental(targets, limit);
        runContinuous(targets, limit);
    }

    public static void benchmarkMutation(int size, int limit) {
        CompactDFA<Character> base = randomAutomatonGen(size);

        CompactDFA<Character> mutateTrans = randomTransMutation(base);
        CompactDFA<Character> mutateAcceptance = randomAcceptanceMutation(mutateTrans);
        CompactDFA<Character> mutateAddState = randomAddStateMutation(mutateAcceptance);
        CompactDFA<Character> mutation = randomRemoveStateMutation(mutateAddState);

        benchmark(base, mutation, limit);
    }

    public static void benchmarkFeature(int size, int limit) {
        CompactDFA<Character> base = randomAutomatonGen(size);
        CompactDFA<Character> baseWithFeature = randomAddFeature(base, 3);
        System.out.println(DFAs.minimize(base).size() + ", " + DFAs.minimize(baseWithFeature).size());

        benchmark(base, baseWithFeature, limit);

    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            long seed = System.nanoTime();
            RAND.setSeed(seed);
            System.out.println("# SEED: " + seed);

            int baseSize = Integer.parseInt(args[0]);
            int limit = Integer.parseInt(args[2]);
            if (args.length >= 4) {
                PD.isBinary = Boolean.parseBoolean(args[3]);
            }

            switch (args[1]) {
            case "MUT":
                benchmarkMutation(baseSize, limit);
                break;
            case "FEAT":
                benchmarkFeature(baseSize, limit);
                break;
            default:
                break;
            }
        }
    }
}
