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
import de.learnlib.algorithms.continuous.base.PAS;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealy;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealyState;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.cache.mealy.MealyCacheOracle;
import de.learnlib.filter.cache.mealy.MealyCaches;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.oracle.membership.MutatingSimulatorOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.commons.util.Pair;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

public class LearningBenchmarkMealy {
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

    private static DefaultQuery<Character, Word<Character>> findCex(CompactMealy<Character, Character> hyp,
            Counter counter, MembershipOracle.MealyMembershipOracle<Character, Character> oracle, int limit) {
        Word<Character> input = sampleWord();
        Word<Character> output = oracle.answerQuery(input);
        while (hyp.computeOutput(input).equals(output)) {
            if (counter.getCount() > limit) {
                return null;
            }
            input = sampleWord();
            output = oracle.answerQuery(input);
        }
        return new DefaultQuery<>(Word.epsilon(), input, output);
    }

    @SuppressWarnings("unchecked")
    private static Pair<KearnsVaziraniMealyState<Character, Character>, List<Pair<Integer, CompactMealy<Character, Character>>>> learnClassic(
            MembershipOracle.MealyMembershipOracle<Character, Character> oracle, int limit) {
        List<Pair<Integer, CompactMealy<Character, Character>>> results = new LinkedList<>();
        MealyCounterOracle<Character, Character> memOracle = new MealyCounterOracle<>(oracle,
                "Number of membership queries");
        KearnsVaziraniMealy<Character, Character> learner = new KearnsVaziraniMealy<>(ALPHABET, memOracle, false,
                AcexAnalyzers.BINARY_SEARCH_BWD);

        learner.startLearning();
        results.add(Pair.of((int) memOracle.getCount(),
                new CompactMealy<>((CompactMealy<Character, Character>) learner.getHypothesisModel())));
        DefaultQuery<Character, Word<Character>> cex = findCex(
                new CompactMealy<>((CompactMealy<Character, Character>) learner.getHypothesisModel()),
                memOracle.getCounter(), memOracle, limit);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) memOracle.getCount(),
                    new CompactMealy<>((CompactMealy<Character, Character>) learner.getHypothesisModel())));
            cex = findCex(new CompactMealy<>((CompactMealy<Character, Character>) learner.getHypothesisModel()),
                    memOracle.getCounter(), memOracle, limit);
        }

        return Pair.of(learner.suspend(), results);
    }

    public static void runClassic(List<CompactMealy<Character, Character>> targets, int limit) {
        System.out.println("=== CLASSIC ===");
        for (CompactMealy<Character, Character> target : targets) {
            Pair<KearnsVaziraniMealyState<Character, Character>, List<Pair<Integer, CompactMealy<Character, Character>>>> learnerRes = learnClassic(
                    new SimulatorOracle.MealySimulatorOracle<>(target), limit);
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

    private static List<Pair<Integer, CompactMealy<Character, Character>>> learnContinuous(
            MembershipOracle.MealyMembershipOracle<Character, Character> oracle, int limit) {

        MealyCounterOracle<Character, Character> queryOracle = new MealyCounterOracle<>(oracle,
                "Number of total queries");

        PAS env = new PAS(sulOracle -> new KearnsVaziraniMealy<Character, Character>(ALPHABET, sulOracle, true,
                AcexAnalyzers.BINARY_SEARCH_BWD), queryOracle, ALPHABET, RAND);

        return env.run();
    }

    public static void runContinuous(List<CompactMealy<Character, Character>> targets, int limit) {
        MutatingSimulatorOracle.MealyMutatingSimulatorOracle<Character, Character> ORACLE = new MutatingSimulatorOracle.MealyMutatingSimulatorOracle<>(
                limit, targets);
        System.out.println("=== CONTINUOUS ===");
        List<Pair<Integer, CompactMealy<Character, Character>>> result = learnContinuous(ORACLE,
                limit * targets.size());
        List<CompactMealy<Character, Character>> run = new LinkedList<>();
        for (int i = 0; i < result.size() - 1; i++) {
            while (run.size() < result.get(i + 1).getFirst()) {
                run.add(result.get(i).getSecond());
            }
        }
        while (run.size() < limit * targets.size()) {
            run.add(result.get(result.size() - 1).getSecond());
        }
        run = run.stream().limit(limit * targets.size()).collect(Collectors.toList());

        List<Character> alphas = new LinkedList<>(ALPHABET);
        Word<Character> testWord = Word.epsilon();
        for (int i = 0; i < 10_000; i++) {
            Collections.shuffle(alphas, RAND);
            testWord = testWord.append(alphas.get(0));
        }

        assert targets.get(0).computeOutput(testWord)
                .equals(run.get(limit - 1).computeOutput(testWord));

        assert targets.get(1).computeOutput(testWord)
                .equals(run.get(run.size() - 1).computeOutput(testWord));

        // assert run.get(run.size() - 1).equals(1.0);
    }

    public static CompactMealy<Character, Character> randomAutomatonGen(int size) {
        CompactMealy<Character, Character> aut = RandomAutomata.randomMealy(RAND, size, ALPHABET, ALPHABET);
        while (aut.size() != size) {
            aut = RandomAutomata.randomMealy(RAND, size, ALPHABET, ALPHABET);
        }
        return aut;
    }

    public static CompactMealy<Character, Character> randomTransMutation(CompactMealy<Character, Character> source) {
        CompactMealy<Character, Character> aut = new CompactMealy<>(source);
        Integer stateFrom = RAND.nextInt(source.size() - 1);
        Integer stateTo = RAND.nextInt(source.size() - 1);
        Character symbolIn = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));
        Character symbolOut = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));

        aut.removeTransition(stateFrom, symbolIn, aut.getTransition(stateFrom, symbolIn));
        aut.addTransition(stateFrom, symbolIn, stateTo, symbolOut);

        return aut;
    }

    public static CompactMealy<Character, Character> randomAddStateMutation(CompactMealy<Character, Character> source) {
        CompactMealy<Character, Character> aut = new CompactMealy<>(source);
        Integer state = aut.addState();

        for (Character a : source.getInputAlphabet()) {
            Integer sourceState = RAND.nextInt(aut.size() - 1);
            Character b = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));
            aut.removeTransition(sourceState, a, aut.getTransition(sourceState, a));
            aut.addTransition(sourceState, a, state, b);
        }

        for (Character a : source.getInputAlphabet()) {
            Character b = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));
            aut.addTransition(state, a, RAND.nextInt(aut.size() - 1), b);
        }

        return aut;
    }

    public static CompactMealy<Character, Character> randomRemoveStateMutation(
            CompactMealy<Character, Character> source) {
        CompactMealy<Character, Character> aut = new CompactMealy<>(source);

        Integer state = aut.getInitialState();
        while (Objects.equals(state, aut.getInitialState())) {
            state = RAND.nextInt(aut.size() - 1);
        }

        Set<Pair<Pair<Integer, Integer>, Pair<Character, Character>>> transitions = new HashSet<>();
        for (Integer sourceState : aut.getStates()) {
            for (Character a : aut.getInputAlphabet()) {
                Integer targetState = aut.getSuccessor(sourceState, a);
                Character b = aut.getOutput(sourceState, a);
                assert targetState != null;
                if (targetState.equals(state)) {
                    transitions.add(Pair.of(Pair.of(sourceState, targetState), Pair.of(a, b)));
                }
            }
        }

        for (Pair<Pair<Integer, Integer>, Pair<Character, Character>> t : transitions) {
            Integer newTarget = state;
            while (newTarget.equals(state)) {
                newTarget = RAND.nextInt(aut.size() - 1);
            }

            aut.removeTransition(t.getFirst().getFirst(), t.getSecond().getFirst(),
                    aut.getTransition(t.getFirst().getFirst(), t.getSecond().getFirst()));
            aut.addTransition(t.getFirst().getFirst(), t.getSecond().getFirst(), newTarget, t.getSecond().getSecond());
        }

        return aut;
    }

    public static CompactMealy<Character, Character> randomAddFeature(CompactMealy<Character, Character> source,
            int featureSize) {
        CompactMealy<Character, Character> aut = new CompactMealy<>(source);
        CompactMealy<Character, Character> feature = randomAutomatonGen(featureSize);

        Set<Pair<Pair<Integer, Integer>, Pair<Character, Character>>> sourceTransitions = new HashSet<>();
        for (int i = 0; i < featureSize; i++) {
            for (Character a : aut.getInputAlphabet()) {
                Integer sourceState = RAND.nextInt(aut.size() - 1);
                Integer targetState = aut.getSuccessor(sourceState, a);
                sourceTransitions
                        .add(Pair.of(Pair.of(sourceState, targetState), Pair.of(a, aut.getOutput(sourceState, a))));
            }
        }

        Map<Integer, Integer> oldToNew = new HashMap<>();
        for (Integer oldState : feature.getStates()) {
            Integer newState = aut.addState();
            oldToNew.put(oldState, newState);
        }

        for (Integer oldState : feature.getStates()) {
            for (Character a : feature.getInputAlphabet()) {
                aut.addTransition(oldToNew.get(oldState), a, oldToNew.get(feature.getSuccessor(oldState, a)),
                        feature.getOutput(oldState, a));
            }
        }

        for (Pair<Pair<Integer, Integer>, Pair<Character, Character>> t : sourceTransitions) {
            aut.removeTransition(t.getFirst().getFirst(), t.getSecond().getFirst(),
                    aut.getTransition(t.getFirst().getFirst(), t.getSecond().getFirst()));
            aut.addTransition(t.getFirst().getFirst(), t.getSecond().getFirst(),
                    oldToNew.get(feature.getInitialState()), t.getSecond().getSecond());
        }

        return aut;
    }

    public static void benchmark(CompactMealy<Character, Character> base, CompactMealy<Character, Character> target,
            int limit) {
        List<CompactMealy<Character, Character>> targets = new ArrayList<>(2);
        targets.add(base);
        targets.add(target);

        runClassic(targets, limit);
        runContinuous(targets, limit);
    }

    public static void benchmarkMutation(int size, int limit) {
        CompactMealy<Character, Character> base = randomAutomatonGen(size);
        CompactMealy<Character, Character> mutateTrans = randomTransMutation(base);
        CompactMealy<Character, Character> mutateAddState = randomAddStateMutation(mutateTrans);
        CompactMealy<Character, Character> mutation = randomRemoveStateMutation(mutateAddState);

        benchmark(base, mutation, limit);
    }

    public static void benchmarkFeature(int size, int limit) {
        CompactMealy<Character, Character> base = randomAutomatonGen(size);
        CompactMealy<Character, Character> baseWithFeature = randomAddFeature(base, 3);

        benchmark(base, baseWithFeature, limit);
    }

    // Interesting seeds:
    // 90800239093333L -> Shows an example where "trusing the cache" leads to an
    // intermediate hypothesis bigger than the new target. Only to later be
    // destroyed.
    // 160939488765291L -> An example of why the initial state is no longer
    // guaranteed as correct.
    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            System.out.println("# RUN: " + i);
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
