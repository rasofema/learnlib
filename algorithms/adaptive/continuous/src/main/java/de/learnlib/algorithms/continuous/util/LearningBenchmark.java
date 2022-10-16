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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.cli.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.continuous.base.PAR;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealy;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithms.lstar.closing.ClosingStrategies;
import de.learnlib.algorithms.lstar.mealy.ExtensibleLStarMealy;
import de.learnlib.algorithms.ttt.mealy.TTTLearnerMealy;
import de.learnlib.api.algorithm.LearningAlgorithm;
import de.learnlib.api.exception.LimitException;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.cache.mealy.MealyCacheOracle;
import de.learnlib.filter.cache.mealy.MealyCaches;
import de.learnlib.filter.statistic.oracle.JointCounterOracle;
import de.learnlib.oracle.membership.LimitOracle;
import de.learnlib.oracle.membership.NoiseOracle;
import de.learnlib.oracle.membership.ProbabilisticOracle;
import de.learnlib.oracle.membership.SimulatorOracle.MealySimulatorOracle;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.commons.util.Pair;
import net.automatalib.commons.util.Triple;
import net.automatalib.serialization.InputModelDeserializer;
import net.automatalib.serialization.dot.DOTParsers;
import net.automatalib.util.automata.equivalence.DeterministicEquivalenceTest;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.ListAlphabet;

enum Framework {
    MAT, PAR
}

enum Algorithm {
    LSTAR, KV, TTT, LSHARP
}

class Config {
    public Framework framework;
    public Algorithm algorithm;
    public NoiseOracle.NoiseType noise;
    public Double noiseLevel;
    public Double revisionRatio;
    public Double lengthFactor;
    public Boolean caching;
    public Integer minRepeats;
    public Integer maxRepeats;
    public Double percentAccuracy;
    public Integer queryLimit;
    public CompactMealy<String, String> target;
    public Long randomSeed;
    public Random random;

    public Config(CommandLine cmd) throws Exception {
        this.framework = Framework.valueOf(cmd.getOptionValue("framework", "PAR"));
        this.algorithm = Algorithm.valueOf(cmd.getOptionValue("algorithm", "LSTAR"));
        this.noise = NoiseOracle.NoiseType.valueOf(cmd.getOptionValue("noise", "INPUT"));
        this.noiseLevel = Double.parseDouble(cmd.getOptionValue("noiseLevel", "0.0"));
        this.revisionRatio = Double.parseDouble(cmd.getOptionValue("revisionRatio", "0.0"));
        this.lengthFactor = Double.parseDouble(cmd.getOptionValue("lengthFactor", "0.99"));
        this.caching = Boolean.parseBoolean(cmd.getOptionValue("caching", "true"));
        this.minRepeats = Integer.parseInt(cmd.getOptionValue("minRepeats", "1"));
        this.maxRepeats = Integer.parseInt(cmd.getOptionValue("maxRepeats", "2"));
        this.percentAccuracy = Double.parseDouble(cmd.getOptionValue("percentAccuracy", "0.7"));
        this.queryLimit = Integer.parseInt(cmd.getOptionValue("queryLimit", "20000"));
        InputModelDeserializer<String, CompactMealy<String, String>> parser = DOTParsers
                .mealy(new CompactMealy.Creator<String, String>(), DOTParsers.DEFAULT_MEALY_EDGE_PARSER);
        this.target = parser.readModel(new File(cmd.getOptionValue("target"))).model;
        this.random = new Random();
        randomSeed = Long.parseLong(cmd.getOptionValue("random", System.nanoTime() + ""));
        this.random.setSeed(randomSeed);
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
        builder.append("# RANDOM: " + randomSeed.toString());

        return builder.toString();
    }
}

public class LearningBenchmark {
    private final Config config;

    public LearningBenchmark(CommandLine cmd) throws Exception {
        this.config = new Config(cmd);
    }

    private Word<String> sampleWord() {
        if (config.random.nextFloat() < config.lengthFactor) {
            List<String> alphas = new LinkedList<>(config.target.getInputAlphabet());
            Collections.shuffle(alphas, config.random);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    private DefaultQuery<String, Word<String>> findCex(MealyMachine<?, String, ?, String> hyp,
            MembershipOracle<String, Word<String>> oracle, MealyEquivalenceOracle<String, String> cacheTest) {

        if (DeterministicEquivalenceTest.findSeparatingWord(config.target, hyp,
                config.target.getInputAlphabet()) == null) {
            return null;
        }

        if (cacheTest != null) {
            @Nullable
            DefaultQuery<String, Word<String>> cacheCex = cacheTest.findCounterExample(hyp,
                    config.target.getInputAlphabet());
            if (cacheCex != null) {
                return cacheCex;
            }
        }

        Word<String> input = sampleWord();
        Word<String> output = oracle.answerQuery(input);
        while (hyp.computeOutput(input).equals(output)) {
            input = sampleWord();
            output = oracle.answerQuery(input);
        }
        return new DefaultQuery<>(Word.epsilon(), input, output);
    }

    private Triple<Long, Long, CompactMealy<String, String>> runMAT(MembershipOracle<String, Word<String>> oracle,
            JointCounterOracle<String, Word<String>> statisticOracle,
            MealyEquivalenceOracle<String, String> cacheTest) {
        LearningAlgorithm.MealyLearner<String, String> learner = null;
        switch (config.algorithm) {
        case LSTAR:
            learner = new ExtensibleLStarMealy<>(config.target.getInputAlphabet(), oracle, Collections.emptyList(),
                    ObservationTableCEXHandlers.RIVEST_SCHAPIRE, ClosingStrategies.CLOSE_SHORTEST);
            break;
        case KV:
            learner = new KearnsVaziraniMealy<>(config.target.getInputAlphabet(), oracle, true,
                    AcexAnalyzers.BINARY_SEARCH_BWD);
            break;
        case TTT:
            learner = new TTTLearnerMealy<>(config.target.getInputAlphabet(), oracle, AcexAnalyzers.BINARY_SEARCH_BWD);
            break;
        case LSHARP:
        default:
            learner = new ExtensibleLStarMealy<>(config.target.getInputAlphabet(), oracle, Collections.emptyList(),
                    ObservationTableCEXHandlers.RIVEST_SCHAPIRE, ClosingStrategies.CLOSE_SHORTEST);
            break;
        }

        try {
            learner.startLearning();

            DefaultQuery<String, Word<String>> cex = findCex(learner.getHypothesisModel(), oracle, cacheTest);

            while (cex != null) {
                learner.refineHypothesis(cex);
                cex = findCex(learner.getHypothesisModel(), oracle, cacheTest);
            }

        } catch (LimitException e) {
        }

        return Triple.of(statisticOracle.getQueryCount(),
                statisticOracle.getSymbolsByQuery().get((int) statisticOracle.getQueryCount() - 1),
                (CompactMealy<String, String>) learner.getHypothesisModel());
    }

    public Pair<Integer, MealyMachine<?, String, ?, String>> mostFrequent(
            List<Pair<Integer, MealyMachine<?, String, ?, String>>> hypotheses, Integer queryCount) {

        // For counting purposes.
        List<Pair<Integer, MealyMachine<?, String, ?, String>>> newHyps = new LinkedList<>(hypotheses);
        newHyps.add(Pair.of(queryCount, newHyps.get(newHyps.size() - 1).getSecond()));

        List<Pair<Integer, MealyMachine<?, String, ?, String>>> lifetimes = new LinkedList<>();

        Integer previousQuery = 0;
        for (Pair<Integer, MealyMachine<?, String, ?, String>> pair : newHyps) {
            lifetimes.add(
                    Pair.of(pair.getFirst() - previousQuery, pair.getSecond()));
            previousQuery = pair.getFirst();
        }

        List<Pair<Integer, MealyMachine<?, String, ?, String>>> bags = new LinkedList<>();

        for (Pair<Integer, MealyMachine<?, String, ?, String>> pair : lifetimes) {
            MealyMachine<?, String, ?, String> hyp = pair.getSecond();
            Boolean addedToBag = false;
            for (int bagIndex = 0; bagIndex < bags.size(); bagIndex++) {
                Boolean equivalent = DeterministicEquivalenceTest.findSeparatingWord(hyp,
                        bags.get(bagIndex).getSecond(),
                        config.target.getInputAlphabet()) == null;

                if (equivalent) {
                    MealyMachine<?, String, ?, String> newMealy = bags.get(bagIndex).getSecond();
                    Integer newQueryCount = bags.get(bagIndex).getFirst() + pair.getFirst();
                    if (hyp.size() < newMealy.size()) {
                        newMealy = hyp;
                    }
                    bags.set(bagIndex, Pair.of(newQueryCount, newMealy));
                    addedToBag = true;
                    break;
                }
            }

            if (!addedToBag) {
                bags.add(Pair.of(pair.getFirst(), hyp));
            }
        }

        Pair<Integer, MealyMachine<?, String, ?, String>> mostFrequent = bags.get(0);
        for (Pair<Integer, MealyMachine<?, String, ?, String>> bag : bags) {
            if (bag.getFirst() > mostFrequent.getFirst()) {
                mostFrequent = bag;
            }
        }

        return mostFrequent;
    }

    private List<Pair<Integer, MealyMachine<?, String, ?, String>>> sublistQ(
            List<Pair<Integer, MealyMachine<?, String, ?, String>>> hyps, Integer queryLimit) {
        Integer highest = -1;
        for (int i = 0; i < hyps.size(); i++) {
            if (hyps.get(i).getFirst() > queryLimit) {
                break;
            }
            highest = i;
        }
        return hyps.subList(0, highest + 1);
    }

    private Pair<Integer, List<Pair<Integer, MealyMachine<?, String, ?, String>>>> searchFirstCorrect(
            List<Pair<Integer, MealyMachine<?, String, ?, String>>> hyps, Integer queryCount) {
        for (int i = 0; i <= queryCount; i++) {
            List<Pair<Integer, MealyMachine<?, String, ?, String>>> sublist = sublistQ(hyps, i);
            if (!sublist.isEmpty()) {
                Pair<Integer, MealyMachine<?, String, ?, String>> mf = sublist.get(sublist.size() - 1);

                if (DeterministicEquivalenceTest.findSeparatingWord(config.target, mf.getSecond(),
                        config.target.getInputAlphabet()) == null) {
                    return Pair.of(i, sublist);
                }
            }
        }

        return Pair.of(queryCount, hyps);
    }

    private Pair<Integer, List<Pair<Integer, MealyMachine<?, String, ?, String>>>> searchMin(
            List<Pair<Integer, MealyMachine<?, String, ?, String>>> hyps, Integer queryCount) {
        for (int i = 0; i <= queryCount; i++) {
            List<Pair<Integer, MealyMachine<?, String, ?, String>>> sublist = sublistQ(hyps, i);
            if (!sublist.isEmpty()) {
                Pair<Integer, MealyMachine<?, String, ?, String>> mf = mostFrequent(sublist, i);

                if (DeterministicEquivalenceTest.findSeparatingWord(config.target, mf.getSecond(),
                        config.target.getInputAlphabet()) == null) {
                    return Pair.of(i, sublist);
                }
            }
        }

        return Pair.of(queryCount, hyps);
    }

    private Triple<Long, Long, CompactMealy<String, String>> runPAR(MembershipOracle<String, Word<String>> oracle,
            JointCounterOracle<String, Word<String>> statisticOracle) {
        Function<MembershipOracle<String, Word<String>>, LearningAlgorithm.MealyLearner<String, String>> constructor;
        if (config.algorithm == Algorithm.LSTAR) {
            constructor = (sulOracle -> new ExtensibleLStarMealy<>(config.target.getInputAlphabet(), sulOracle,
                    Collections.emptyList(), ObservationTableCEXHandlers.CLASSIC_LSTAR,
                    ClosingStrategies.CLOSE_SHORTEST));
        } else if (config.algorithm == Algorithm.KV) {
            constructor = (sulOracle -> new KearnsVaziraniMealy<>(config.target.getInputAlphabet(), sulOracle, true,
                    AcexAnalyzers.BINARY_SEARCH_BWD));
        } else if (config.algorithm == Algorithm.TTT) {
            constructor = (sulOracle -> new TTTLearnerMealy<>(config.target.getInputAlphabet(), sulOracle,
                    AcexAnalyzers.BINARY_SEARCH_BWD));
        } else {
            constructor = (sulOracle -> new ExtensibleLStarMealy<>(config.target.getInputAlphabet(), sulOracle,
                    Collections.emptyList(), ObservationTableCEXHandlers.CLASSIC_LSTAR,
                    ClosingStrategies.CLOSE_SHORTEST));
        }

        PAR<String, String> env = new PAR<String, String>(constructor, oracle, config.target.getInputAlphabet(),
                config.queryLimit, config.revisionRatio, config.lengthFactor, config.caching, config.random,
                statisticOracle.getQueryCounter());

        List<Pair<Integer, MealyMachine<?, String, ?, String>>> hypotheses = env.run();

        Pair<Integer, List<Pair<Integer, MealyMachine<?, String, ?, String>>>> min = null;
        Pair<Integer, MealyMachine<?, String, ?, String>> mf = null;

        if (config.noiseLevel == 0.0) {
            min = searchFirstCorrect(hypotheses, (int) statisticOracle.getQueryCount());
            mf = min.getSecond().get(min.getSecond().size() - 1);
        } else {
            min = searchMin(hypotheses, (int) statisticOracle.getQueryCount());
            mf = mostFrequent(min.getSecond(), min.getFirst());
        }

        return Triple.of(Long.valueOf(min.getFirst()), statisticOracle.getSymbolsByQuery().get(min.getFirst() - 1),
                (CompactMealy<String, String>) mf.getSecond());
    }

    private Alphabet<String> getOutputAlphabet(CompactMealy<String, String> mealy) {
        Set<String> symbols = new HashSet<>();
        for (Integer state : mealy.getStates()) {
            for (String alpha : mealy.getInputAlphabet()) {
                symbols.add(mealy.getOutput(state, alpha));
            }
        }

        return new ListAlphabet<>(new LinkedList<>(symbols));
    }

    public void run() {
        System.out.println("# ========== CONFIG ==========");
        System.out.println(config.toString());

        MealySimulatorOracle<String, String> sulOracle = new MealySimulatorOracle<>(config.target);
        MembershipOracle<String, Word<String>> noiseOracle = new NoiseOracle<String, String>(
                config.target.getInputAlphabet(),
                getOutputAlphabet(config.target), sulOracle, config.noiseLevel, config.noise, config.random);
        JointCounterOracle<String, Word<String>> statisticOracle = new JointCounterOracle<>(noiseOracle);
        LimitOracle<String, Word<String>> limitOracle = new LimitOracle<>(Long.valueOf(config.queryLimit),
                statisticOracle);
        MembershipOracle<String, Word<String>> oracle = new ProbabilisticOracle<String, String>(
                limitOracle.asOracle(), config.minRepeats,
                config.percentAccuracy, config.maxRepeats);

        // Give MAT all the benefits, including caching whenver possible.
        MealyEquivalenceOracle<String, String> cacheTest = null;
        if (config.framework == Framework.MAT && config.noiseLevel == 0.0) {
            MealyCacheOracle<String, String> cacheOracle = MealyCaches.createCache(config.target.getInputAlphabet(),
                    oracle);
            cacheTest = cacheOracle.createCacheConsistencyTest();
            oracle = cacheOracle;
        }

        Triple<Long, Long, CompactMealy<String, String>> result = config.framework == Framework.MAT
                ? runMAT(oracle, statisticOracle, cacheTest)
                : runPAR(oracle, statisticOracle);

        Boolean isCorrect = DeterministicEquivalenceTest.findSeparatingWord(config.target, result.getThird(),
                config.target.getInputAlphabet()) == null;

        System.out.println("# ========== RESULTS ==========");
        System.out.println("# QUERY COUNT: " + result.getFirst());
        System.out.println("# SYMBOL COUNT: " + result.getSecond());
        System.out.println("# SUCCESS: " + isCorrect.toString());
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
        benchmark.run();
    }
}
