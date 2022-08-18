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
import de.learnlib.filter.statistic.Counter;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.oracle.membership.MutatingSimulatorOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import de.learnlib.util.mealy.MealyUtil;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.commons.util.Pair;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

public class LearningBenchmark {
    private static final Alphabet<String> ALPHABET = Alphabets.fromArray("0", "1", "2");
    private static final PhiMetric<String> PD = new PhiMetric<>(ALPHABET, 0.999, false);
    private static final Random RAND = new Random();
    private static Integer LIMIT;
    private static Double REVISION_RATIO;
    private static Double LENGTH_FACTOR;

    private static Word<String> sampleWord() {
        double ALPHA = 0.9;
        if (RAND.nextFloat() < ALPHA) {
            List<String> alphas = new LinkedList<>(ALPHABET);
            Collections.shuffle(alphas, RAND);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    private static DefaultQuery<String, Word<String>> findCex(CompactMealy<String, String> hyp, Counter counter,
            MembershipOracle.MealyMembershipOracle<String, String> oracle) {
        Word<String> input = sampleWord();
        Word<String> output = oracle.answerQuery(input);
        while (hyp.computeOutput(input).equals(output)) {
            if (counter.getCount() > LIMIT) {
                return null;
            }
            input = sampleWord();
            output = oracle.answerQuery(input);
        }
        return new DefaultQuery<>(Word.epsilon(), input, output);
    }

    @SuppressWarnings("unchecked")
    private static Pair<KearnsVaziraniMealyState<String, String>, List<Pair<Integer, CompactMealy<String, String>>>> learnClassic(
            MembershipOracle.MealyMembershipOracle<String, String> oracle) {
        List<Pair<Integer, CompactMealy<String, String>>> results = new LinkedList<>();
        MealyCounterOracle<String, String> memOracle = new MealyCounterOracle<>(oracle, "Number of membership queries");
        KearnsVaziraniMealy<String, String> learner = new KearnsVaziraniMealy<>(ALPHABET, memOracle, false,
                AcexAnalyzers.BINARY_SEARCH_BWD);

        learner.startLearning();
        results.add(Pair.of((int) memOracle.getCount(),
                new CompactMealy<>((CompactMealy<String, String>) learner.getHypothesisModel())));
        DefaultQuery<String, Word<String>> cex = findCex(
                new CompactMealy<>((CompactMealy<String, String>) learner.getHypothesisModel()), memOracle.getCounter(),
                memOracle);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) memOracle.getCount(),
                    new CompactMealy<>((CompactMealy<String, String>) learner.getHypothesisModel())));
            cex = findCex(new CompactMealy<>((CompactMealy<String, String>) learner.getHypothesisModel()),
                    memOracle.getCounter(), memOracle);
        }

        return Pair.of(learner.suspend(), results);
    }

    public static void runClassic(List<CompactMealy<String, String>> targets) {
        System.out.println("=== CLASSIC ===");
        for (CompactMealy<String, String> target : targets) {
            Pair<KearnsVaziraniMealyState<String, String>, List<Pair<Integer, CompactMealy<String, String>>>> learnerRes = learnClassic(
                    new SimulatorOracle.MealySimulatorOracle<>(target));
            List<Pair<Integer, Double>> classic = learnerRes.getSecond().stream().parallel()
                    .map(p -> Pair.of(p.getFirst(), PD.sim(target, p.getSecond()))).collect(Collectors.toList());
            List<Double> run = new LinkedList<>();
            for (int i = 0; i < classic.size() - 1; i++) {
                while (run.size() < classic.get(i + 1).getFirst()) {
                    run.add(classic.get(i).getSecond());
                }
            }

            run = run.stream().limit(LIMIT).collect(Collectors.toList());
            while (run.size() < LIMIT) {
                run.add(classic.get(classic.size() - 1).getSecond());
            }

            for (Double metric : run) {
                System.out.println(metric.toString());
            }
        }
    }

    private static List<Pair<Integer, CompactMealy<String, String>>> learnContinuous(
            MembershipOracle.MealyMembershipOracle<String, String> oracle) {

        MealyCounterOracle<String, String> queryOracle = new MealyCounterOracle<>(oracle, "Number of total queries");

        PAS env = new PAS(
                sulOracle -> new KearnsVaziraniMealy<String, String>(ALPHABET, sulOracle, true,
                        AcexAnalyzers.BINARY_SEARCH_BWD),
                queryOracle, ALPHABET, LIMIT * 2, REVISION_RATIO, LENGTH_FACTOR, RAND);

        return env.run();
    }

    public static void runContinuous(List<CompactMealy<String, String>> targets) {
        MutatingSimulatorOracle.MealyMutatingSimulatorOracle<String, String> ORACLE = new MutatingSimulatorOracle.MealyMutatingSimulatorOracle<>(
                LIMIT, targets);
        System.out.println("=== CONTINUOUS ===");
        List<Pair<Integer, CompactMealy<String, String>>> result = learnContinuous(ORACLE);
        List<CompactMealy<String, String>> run = new LinkedList<>();
        for (int i = 0; i < result.size() - 1; i++) {
            while (run.size() < result.get(i + 1).getFirst()) {
                run.add(result.get(i).getSecond());
            }
        }
        while (run.size() < LIMIT * targets.size()) {
            run.add(result.get(result.size() - 1).getSecond());
        }
        run = run.stream().limit(LIMIT * targets.size()).collect(Collectors.toList());

        boolean check1 = (PD.sim(((CompactMealy<String, String>) targets.get(0)), run.get(LIMIT - 1)) == 1.0);
        System.out.println("# EQ CHECK 1: " + check1);

        boolean check2 = (PD.sim(((CompactMealy<String, String>) targets.get(1)), run.get(run.size() - 1)) == 1.0);
        System.out.println("# EQ CHECK 2: " + check2);

        for (int j = 0; j < run.size(); j++) {
            System.out.println(PD.sim(((CompactMealy<String, String>) ORACLE.getTarget(j)), run.get(j)));
        }

        assert check1 && check2;
    }

    public static CompactMealy<String, String> randomAutomatonGen(int size) {
        CompactMealy<String, String> aut = RandomAutomata.randomMealy(RAND, size, ALPHABET, ALPHABET);
        while (aut.size() != size) {
            aut = RandomAutomata.randomMealy(RAND, size, ALPHABET, ALPHABET);
        }
        return aut;
    }

    public static CompactMealy<String, String> randomTransMutation(CompactMealy<String, String> source) {
        CompactMealy<String, String> aut = new CompactMealy<>(source);
        Integer stateFrom = RAND.nextInt(source.size() - 1);
        Integer stateTo = RAND.nextInt(source.size() - 1);
        String symbolIn = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));
        String symbolOut = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));

        aut.removeTransition(stateFrom, symbolIn, aut.getTransition(stateFrom, symbolIn));
        aut.addTransition(stateFrom, symbolIn, stateTo, symbolOut);

        return aut;
    }

    public static CompactMealy<String, String> randomAddStateMutation(CompactMealy<String, String> source) {
        CompactMealy<String, String> aut = new CompactMealy<>(source);
        Integer state = aut.addState();

        for (String a : source.getInputAlphabet()) {
            Integer sourceState = RAND.nextInt(aut.size() - 1);
            String b = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));
            aut.removeTransition(sourceState, a, aut.getTransition(sourceState, a));
            aut.addTransition(sourceState, a, state, b);
        }

        for (String a : source.getInputAlphabet()) {
            String b = source.getInputAlphabet().getSymbol(RAND.nextInt(source.getInputAlphabet().size()));
            aut.addTransition(state, a, RAND.nextInt(aut.size() - 1), b);
        }

        return aut;
    }

    public static CompactMealy<String, String> randomRemoveStateMutation(CompactMealy<String, String> source) {
        CompactMealy<String, String> aut = new CompactMealy<>(source);

        Integer state = aut.getInitialState();
        while (Objects.equals(state, aut.getInitialState())) {
            state = RAND.nextInt(aut.size() - 1);
        }

        Set<Pair<Pair<Integer, Integer>, Pair<String, String>>> transitions = new HashSet<>();
        for (Integer sourceState : aut.getStates()) {
            for (String a : aut.getInputAlphabet()) {
                Integer targetState = aut.getSuccessor(sourceState, a);
                String b = aut.getOutput(sourceState, a);
                assert targetState != null;
                if (targetState.equals(state)) {
                    transitions.add(Pair.of(Pair.of(sourceState, targetState), Pair.of(a, b)));
                }
            }
        }

        for (Pair<Pair<Integer, Integer>, Pair<String, String>> t : transitions) {
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

    public static CompactMealy<String, String> randomAddFeature(CompactMealy<String, String> source, int featureSize) {
        CompactMealy<String, String> aut = new CompactMealy<>(source);
        CompactMealy<String, String> feature = randomAutomatonGen(featureSize);

        Set<Pair<Pair<Integer, Integer>, Pair<String, String>>> sourceTransitions = new HashSet<>();
        for (int i = 0; i < featureSize; i++) {
            for (String a : aut.getInputAlphabet()) {
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
            for (String a : feature.getInputAlphabet()) {
                aut.addTransition(oldToNew.get(oldState), a, oldToNew.get(feature.getSuccessor(oldState, a)),
                        feature.getOutput(oldState, a));
            }
        }

        for (Pair<Pair<Integer, Integer>, Pair<String, String>> t : sourceTransitions) {
            aut.removeTransition(t.getFirst().getFirst(), t.getSecond().getFirst(),
                    aut.getTransition(t.getFirst().getFirst(), t.getSecond().getFirst()));
            aut.addTransition(t.getFirst().getFirst(), t.getSecond().getFirst(),
                    oldToNew.get(feature.getInitialState()), t.getSecond().getSecond());
        }

        return aut;
    }

    public static void benchmark(CompactMealy<String, String> base, CompactMealy<String, String> target) {
        List<CompactMealy<String, String>> targets = new ArrayList<>(2);
        targets.add(base);
        targets.add(target);

        // runClassic(targets);
        runContinuous(targets);
    }

    public static void benchmarkMutation(int size) {
        CompactMealy<String, String> base = randomAutomatonGen(size);
        CompactMealy<String, String> mutateTrans = randomTransMutation(base);
        CompactMealy<String, String> mutateAddState = randomAddStateMutation(mutateTrans);
        CompactMealy<String, String> mutation = randomRemoveStateMutation(mutateAddState);

        benchmark(base, mutation);
    }

    public static void benchmarkFeature(int size) {
        CompactMealy<String, String> base = randomAutomatonGen(size);
        CompactMealy<String, String> baseWithFeature = randomAddFeature(base, 3);

        benchmark(base, baseWithFeature);
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

        LIMIT = Integer.parseInt(args[2]);
        REVISION_RATIO = Double.parseDouble(args[3]);
        LENGTH_FACTOR = Double.parseDouble(args[4]);
        PD.isBinary = Boolean.parseBoolean(args[5]);

        switch (args[1]) {
        case "MUT":
            benchmarkMutation(baseSize);
            break;
        case "FEAT":
            benchmarkFeature(baseSize);
            break;
        default:
            break;
        }
    }
}
}
