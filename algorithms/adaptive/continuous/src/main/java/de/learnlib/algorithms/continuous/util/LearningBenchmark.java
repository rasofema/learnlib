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

import java.io.File;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.cli.*;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.continuous.base.PAR;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealy;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealyState;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithms.lstar.closing.ClosingStrategies;
import de.learnlib.algorithms.lstar.mealy.ExtensibleLStarMealy;
import de.learnlib.algorithms.ttt.mealy.TTTLearnerMealy;
import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.statistic.StatisticOracle;
import de.learnlib.filter.statistic.Counter;
import de.learnlib.filter.statistic.oracle.JointCounterOracle;
import de.learnlib.filter.statistic.oracle.MealyCounterOracle;
import de.learnlib.oracle.membership.MutatingSimulatorOracle;
import de.learnlib.oracle.membership.NoiseOracle;
import de.learnlib.oracle.membership.ProbabilisticOracle;
import de.learnlib.oracle.membership.SimulatorOracle;
import de.learnlib.oracle.membership.SimulatorOracle.MealySimulatorOracle;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.commons.util.Pair;
import net.automatalib.serialization.InputModelDeserializer;
import net.automatalib.serialization.dot.DOTParsers;
import net.automatalib.util.automata.random.RandomAutomata;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

enum Framework {
    MAT, PAR
}

enum Algorithm {
    LSTAR, KV, TTT, LSHARP
}

enum Noise {
    N1, N2, N3
}

class Config {
    public Framework framework;
    public Algorithm algorithm;
    public Noise noise;
    public Double noiseLevel;
    public Double revisionRatio;
    public Double lengthFactor;
    public Boolean caching;
    public Integer minRepeats;
    public Integer maxRepeats;
    public Double percentAccuracy;
    public Integer queryLimit;
    public CompactMealy<String, String> target;
    public Random random;

    public Config(CommandLine cmd) throws Exception {
        this.framework = Framework.valueOf(cmd.getOptionValue("framework", "PAR"));
        this.algorithm = Algorithm.valueOf(cmd.getOptionValue("algorithm", "LSTAR"));
        this.noise = Noise.valueOf(cmd.getOptionValue("noise", "N1"));
        this.noiseLevel = Double.parseDouble(cmd.getOptionValue("noiseLevel", "0.0"));
        this.revisionRatio = Double.parseDouble(cmd.getOptionValue("revisionRatio", "0.99"));
        this.lengthFactor = Double.parseDouble(cmd.getOptionValue("lengthFactor", "0.99"));
        this.caching = Boolean.parseBoolean(cmd.getOptionValue("caching", "true"));
        this.minRepeats = Integer.parseInt(cmd.getOptionValue("minRepeats", "10"));
        this.maxRepeats = Integer.parseInt(cmd.getOptionValue("maxRepeats", "20"));
        this.percentAccuracy = Double.parseDouble(cmd.getOptionValue("percentAccuracy", "0.7"));
        this.queryLimit = Integer.parseInt(cmd.getOptionValue("queryLimit", "20000"));
        InputModelDeserializer<String, CompactMealy<String, String>> parser = DOTParsers
                .mealy(new CompactMealy.Creator<String, String>(), DOTParsers.DEFAULT_MEALY_EDGE_PARSER);
        this.target = parser.readModel(new File(cmd.getOptionValue("target"))).model;
        this.random = new Random();
        Long seed = Long.parseLong(cmd.getOptionValue("random", System.nanoTime() + ""));
        this.random.setSeed(seed);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("# FRAMEWORK: " + framework.toString() + '\n');
        builder.append("# ALGORITHM: " + algorithm.toString() + '\n');
        builder.append("# NOISE: " + noise.toString() + '\n');
        builder.append("# NOISE LEVEL: " + noiseLevel.toString() + '\n');
        builder.append("# REVISION RATIO: " + revisionRatio.toString() + '\n');
        builder.append("# LENGTH FACTOR: " + lengthFactor.toString() + '\n');
        builder.append("# CACHING: " + caching.toString() + '\n');
        builder.append("# MIN REPEATS: " + minRepeats.toString() + '\n');
        builder.append("# MAX REPEATS: " + maxRepeats.toString() + '\n');
        builder.append("# PERCENT ACCURACY: " + percentAccuracy.toString() + '\n');
        builder.append("# QUERY LIMIT: " + queryLimit.toString() + '\n');
        builder.append("# RANDOM: " + random.toString());

        return builder.toString();
    }
}

public class LearningBenchmark {
    private final Config config;
    private static final Random RAND = new Random();

    public LearningBenchmark(CommandLine cmd) throws Exception {
        this.config = new Config(cmd);
    }

    private Word<String> sampleWord() {
        double ALPHA = 0.9;
        if (RAND.nextFloat() < ALPHA) {
            List<String> alphas = new LinkedList<>(config.target.getInputAlphabet());
            Collections.shuffle(alphas, RAND);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    private DefaultQuery<String, Word<String>> findCex(MealyMachine<?, String, ?, String> hyp, Counter counter,
            MembershipOracle.MealyMembershipOracle<String, String> oracle) {
        Word<String> input = sampleWord();
        Word<String> output = oracle.answerQuery(input);
        while (hyp.computeOutput(input).equals(output)) {
            if (counter.getCount() > config.queryLimit) {
                return null;
            }
            input = sampleWord();
            output = oracle.answerQuery(input);
        }
        return new DefaultQuery<>(Word.epsilon(), input, output);
    }

    private Pair<KearnsVaziraniMealyState<String, String>, List<Pair<Integer, MealyMachine<?, String, ?, String>>>> learnClassic(
            MembershipOracle.MealyMembershipOracle<String, String> oracle) {
        List<Pair<Integer, MealyMachine<?, String, ?, String>>> results = new LinkedList<>();
        MealyCounterOracle<String, String> memOracle = new MealyCounterOracle<>(oracle, "Number of membership queries");
        KearnsVaziraniMealy<String, String> learner = new KearnsVaziraniMealy<>(conf, memOracle, false,
                AcexAnalyzers.BINARY_SEARCH_BWD);

        learner.startLearning();
        results.add(Pair.of((int) memOracle.getCount(), learner.getHypothesisModel()));
        DefaultQuery<String, Word<String>> cex = findCex(learner.getHypothesisModel(), memOracle.getCounter(),
                memOracle);

        while (cex != null) {
            learner.refineHypothesis(cex);
            results.add(Pair.of((int) memOracle.getCount(), learner.getHypothesisModel()));
            cex = findCex(learner.getHypothesisModel(), memOracle.getCounter(), memOracle);
        }

        return Pair.of(learner.suspend(), results);
    }

    public static void runClassic(List<MealyMachine<?, String, ?, String>> targets) {
        System.out.println("=== CLASSIC ===");
        for (MealyMachine<?, String, ?, String> target : targets) {
            Pair<KearnsVaziraniMealyState<String, String>, List<Pair<Integer, MealyMachine<?, String, ?, String>>>> learnerRes = learnClassic(
                    new SimulatorOracle.MealySimulatorOracle<>(target));
            List<Pair<Integer, Double>> classic = learnerRes.getSecond().stream().parallel()
                    .map(p -> Pair.of(p.getFirst(), PD.sim(target, p.getSecond()))).collect(Collectors.toList());
            List<Double> run = new LinkedList<>();
            for (int i = 0; i < classic.size() - 1; i++) {
                while (run.size() < classic.get(i + 1).getFirst()) {
                    run.add(classic.get(i).getSecond());
                }
            }

            while (run.size() < LIMIT) {
                run.add(classic.get(classic.size() - 1).getSecond());
            }

            for (Double metric : run) {
                System.out.println(metric.toString());
            }
        }
    }

    private static List<Pair<Integer, MealyMachine<?, String, ?, String>>> learnContinuous(
            MembershipOracle.MealyMembershipOracle<String, String> oracle, Counter counter) {

        Function<MembershipOracle.MealyMembershipOracle<String, String>, LearningAlgorithm.MealyLearner<String, String>> constructor;
        if (ALGO.contentEquals("LSTAR")) {
            constructor = (sulOracle -> new ExtensibleLStarMealy<>(ALPHABET, sulOracle, Collections.emptyList(),
                    ObservationTableCEXHandlers.CLASSIC_LSTAR, ClosingStrategies.CLOSE_SHORTEST));
        } else if (ALGO.contentEquals("KV")) {
            constructor = (sulOracle -> new KearnsVaziraniMealy<>(ALPHABET, sulOracle, true,
                    AcexAnalyzers.BINARY_SEARCH_BWD));
        } else {
            constructor = (sulOracle -> new TTTLearnerMealy<>(ALPHABET, sulOracle, AcexAnalyzers.BINARY_SEARCH_BWD));
        }

        PAR<String, String> env = new PAR<>(constructor, oracle, ALPHABET, LIMIT * 2, REVISION_RATIO, LENGTH_FACTOR,
                CACHING, RAND,
                counter);

        return env.run();
    }

    public static void runContinuousEvol(List<MealyMachine<?, String, ?, String>> targets) {
        MutatingSimulatorOracle.MealyMutatingSimulatorOracle<String, String> sulOracle = new MutatingSimulatorOracle.MealyMutatingSimulatorOracle<>(
                LIMIT, targets);
        MealyCounterOracle<String, String> ORACLE = new MealyCounterOracle<>(sulOracle, "Membership Queries");
        System.out.println("=== CONTINUOUS EVOL ===");
        List<Pair<Integer, MealyMachine<?, String, ?, String>>> result = learnContinuous(ORACLE, ORACLE.getCounter());
        List<MealyMachine<?, String, ?, String>> run = new LinkedList<>();
        for (int i = 0; i < result.size() - 1; i++) {
            while (run.size() < result.get(i + 1).getFirst()) {
                run.add(result.get(i).getSecond());
            }
        }

        run.add(result.get(result.size() - 1).getSecond());
        while (run.size() < LIMIT * targets.size()) {
            run.add(result.get(result.size() - 1).getSecond());
        }

        boolean check1 = (PD.sim(targets.get(0), run.get(LIMIT - 1)) == 1.0);
        System.out.println("# EQ CHECK 1: " + check1);

        boolean check2 = (PD.sim(targets.get(1), run.get(run.size() - 1)) == 1.0);
        System.out.println("# EQ CHECK 2: " + check2);

        for (int j = 0; j < run.size(); j++) {
            System.out.println(PD.sim(((MealyMachine<?, String, ?, String>) sulOracle.getTarget(j)), run.get(j)));
        }
    }

    public static void runContinuousNoise(MealyMachine<?, String, ?, String> target) {
        System.out.println("=== CONTINUOUS NOISE ===");
        MealySimulatorOracle<String, String> sulOracle = new MealySimulatorOracle<>(target);
        NoiseOracle<String, String> noiseOracle = new NoiseOracle<>(ALPHABET, sulOracle, 0.15, RAND);
        MealyCounterOracle<String, String> counterOracle = new MealyCounterOracle<>(noiseOracle, "Membership Queries");
        ProbabilisticOracle<String, String> ORACLE = new ProbabilisticOracle<>(counterOracle, 3, 0.7, 10);

        List<Pair<Integer, MealyMachine<?, String, ?, String>>> result = learnContinuous(ORACLE,
                counterOracle.getCounter());
        List<MealyMachine<?, String, ?, String>> run = new LinkedList<>();
        for (int i = 0; i < result.size() - 1; i++) {
            while (run.size() < result.get(i + 1).getFirst()) {
                run.add(result.get(i).getSecond());
            }
        }
        while (run.size() < LIMIT * 2) {
            run.add(result.get(result.size() - 1).getSecond());
        }

        for (int j = 0; j < run.size(); j++) {
            System.out.println(PD.sim(((MealyMachine<?, String, ?, String>) target), run.get(j)));
        }
    }

    public void run() {
        System.out.println("# ========== CONFIG ==========");
        System.out.println(config.toString());

        MealySimulatorOracle<String, String> sulOracle = new MealySimulatorOracle<>(config.target);
        NoiseOracle<String, String> noiseOracle = new NoiseOracle<>(config.target.getInputAlphabet(), sulOracle,
                config.noiseLevel, config.random);
        JointCounterOracle<String, Word<String>> statisticOracle = new JointCounterOracle<>(noiseOracle);
        ProbabilisticOracle<String, String> probabilisticOracle = new ProbabilisticOracle<String, String>(
                (MealyMembershipOracle<String, String>) statisticOracle.asOracle(), config.minRepeats,
                config.percentAccuracy, config.maxRepeats);

    }


    public static void benchmark(MealyMachine<?, String, ?, String> base, MealyMachine<?, String, ?, String> target) {
        List<MealyMachine<?, String, ?, String>> targets = new ArrayList<>(2);
        targets.add(base);
        targets.add(target);

        // runClassic(targets);
        runContinuousEvol(targets);
        // runContinuousNoise(targets.get(0));
    }

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption("f", "framework", true, null);
        options.addOption("a", "algorithm", true, null);
        options.addOption("n", "noise", true, null);
        options.addOption("nl", "noiseLevel", true, null);
        options.addOption("rr", "revisionRatio", true, null);
        options.addOption("lf", "lengthFactor", true, null);
        options.addOption("c", "caching", true, null);
        options.addOption("min", "minRepeats", true, null);
        options.addOption("max", "maxRepeats", true, null);
        options.addOption("pa", "percentAccuracy", true, null);
        options.addOption("ql", "queryLimit", true, null);
        options.addOption("t", "target", true, null);
        options.addOption("r", "random", true, null);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Learning Benchmark", options);
            System.exit(1);
        }

        // show help menu
        if (cmd.hasOption("help")) {
            formatter.printHelp("Learning Benchmark", options);
            System.exit(1);
        }

        // check config
        LearningBenchmark benchmark = null;
        try {
            benchmark = new LearningBenchmark(cmd);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Learning Benchmark", options);
            System.exit(1);
        }

        // execute
        try {
            benchmark.run();
        } catch (Exception e) {
            throw e;
        }
}
}
