package de.learnlib.algorithms.lsharp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.HashBiMap;

import com.rits.cloning.Cloner;

import de.learnlib.api.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.util.mealy.MealyUtil;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.commons.util.Pair;
import net.automatalib.util.automata.equivalence.DeterministicEquivalenceTest;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class LSharpMealy<I, O> implements MealyLearner<I, O> {
    private LSOracle<I, O> oqOracle;
    private Alphabet<I> inputAlphabet;
    private List<Word<I>> basis;
    private HashMap<Word<I>, List<Word<I>>> frontierToBasisMap;
    private Integer consistentCECount;
    private HashSet<Word<I>> frontierTransitionsSet;
    private HashBiMap<Word<I>, LSState> basisMap;
    private Integer round;

    public LSharpMealy(LSOracle<I, O> oqOracle, Alphabet<I> inputAlphabet) {
        this.oqOracle = oqOracle;
        this.inputAlphabet = inputAlphabet;
        this.basis = new LinkedList<>();
        basis.add(Word.epsilon());
        this.frontierToBasisMap = new HashMap<>();
        this.consistentCECount = 0;
        this.basisMap = HashBiMap.create();
        this.frontierTransitionsSet = new HashSet<>();
        this.round = 1;
    }

    public void processCex(DefaultQuery<I, Word<O>> cex, LSMealyMachine<I, O> mealy) {
        this.round += 1;
        Objects.requireNonNull(cex);
        Word<I> ceInput = cex.getInput();
        Word<O> ceOutput = cex.getOutput();
        oqOracle.addObservation(ceInput, ceOutput);
        Integer prefixIndex = MealyUtil.findMismatch(mealy, ceInput, ceOutput);
        this.processBinarySearch(ceInput.prefix(prefixIndex), ceOutput.prefix(prefixIndex), mealy);
    }

    public void processBinarySearch(Word<I> ceInput, Word<O> ceOutput, LSMealyMachine<I, O> mealy) {
        LSState r = oqOracle.getTree().getSucc(oqOracle.getTree().defaultState(), ceInput);
        Objects.requireNonNull(r);
        this.updateFrontierAndBasis();
        if (this.frontierToBasisMap.containsKey(ceInput) || basis.contains(ceInput)) {
            return;
        }

        LSState q = mealy.getSuccessor(mealy.getInitialState(), ceInput);
        Word<I> accQT = basisMap.inverse().get(q);
        Objects.requireNonNull(accQT);

        NormalObservationTree<I, O> oTree = oqOracle.getTree();
        LSState qt = oTree.getSucc(oTree.defaultState(), accQT);
        Objects.requireNonNull(qt);

        Integer x = ceInput.prefixes(false).stream().filter(seq -> seq.length() != 0)
                .filter(seq -> frontierToBasisMap.containsKey(seq)).findFirst().get()
                .length();

        Integer y = ceInput.size();
        Integer h = (int) Math.floor((x.floatValue() + y.floatValue()) / 2.0);

        Word<I> sigma1 = ceInput.prefix(h);
        Word<I> sigma2 = ceInput.suffix(ceInput.size() - h);
        LSState qp = mealy.getSuccessor(mealy.getInitialState(), sigma1);
        Objects.requireNonNull(qp);
        Word<I> accQPt = basisMap.inverse().get(qp);
        Objects.requireNonNull(accQPt);

        Word<I> eta = Apartness.computeWitness(oTree, r, qt);
        Objects.requireNonNull(eta);

        Word<I> outputQuery = accQPt.concat(sigma2).concat(eta);
        Word<O> sulResponse = oqOracle.outputQuery(outputQuery);
        LSState qpt = oTree.getSucc(oTree.defaultState(), accQPt);
        Objects.requireNonNull(qpt);

        LSState rp = oTree.getSucc(oTree.defaultState(), sigma1);
        Objects.requireNonNull(rp);

        @Nullable
        Word<I> wit = Apartness.computeWitness(oTree, qpt, rp);
        if (wit != null) {
            processBinarySearch(sigma1, ceOutput.prefix(sigma1.length()), mealy);
        } else {
            Word<I> newInputs = accQPt.concat(sigma2);
            processBinarySearch(newInputs, sulResponse.prefix(newInputs.length()), mealy);
        }
    }

    public void makeObsTreeAdequate() {
        while (true) {
            List<Pair<Word<I>, List<Word<I>>>> newFrontier = oqOracle.exploreFrontier(basis);
            for (Pair<Word<I>, List<Word<I>>> pair : newFrontier) {
                frontierToBasisMap.put(pair.getFirst(), pair.getSecond());
            }

            for (Entry<Word<I>, List<Word<I>>> entry : frontierToBasisMap.entrySet()) {
                if (entry.getValue().size() <= 1) {
                    continue;
                }
                List<Word<I>> newCands = oqOracle.identifyFrontier(entry.getKey(), entry.getValue());
                frontierToBasisMap.put(entry.getKey(), newCands);
            }

            this.promoteFrontierState();
            if (this.treeIsAdequate()) {
                break;
            }
        }
    }

    public void promoteFrontierState() {
        Word<I> newBS = frontierToBasisMap.entrySet().stream().filter(e -> e.getValue().isEmpty()).findFirst()
                .map(e -> e.getKey()).orElse(null);
        if (newBS == null) {
            return;
        }

        Word<I> bs = Word.fromWords(newBS);
        basis.add(bs);
        frontierToBasisMap.remove(bs);
        NormalObservationTree<I, O> oTree = oqOracle.getTree();
        frontierToBasisMap.entrySet().parallelStream().filter(e -> !Apartness.accStatesAreApart(oTree, e.getKey(), bs))
                .forEach(e -> {
                    e.getValue().add(bs);
                });
    }

    public boolean treeIsAdequate() {
        this.checkFrontierConsistency();
        if (frontierToBasisMap.values().stream().anyMatch(x -> x.size() != 1)) {
            return false;
        }

        NormalObservationTree<I, O> oTree = oqOracle.getTree();
        LinkedList<Pair<Word<I>, I>> basisIpPairs = new LinkedList<>();
        for (Word<I> b : basis) {
            for (I i : inputAlphabet) {
                basisIpPairs.add(Pair.of(b, i));
            }
        }

        boolean check = basisIpPairs.stream().anyMatch(p -> {
            LSState q = oTree.getSucc(oTree.defaultState(), p.getFirst());
            return oTree.getOut(q, p.getSecond()) == null;
        });

        if (check) {
            return false;
        }

        return true;
    }

    public void updateFrontierAndBasis() {
        NormalObservationTree<I, O> oTree = oqOracle.getTree();
        frontierToBasisMap.entrySet().parallelStream()
                .forEach(e -> e.getValue().removeIf(bs -> Apartness.accStatesAreApart(oTree, e.getKey(), bs)));

        this.promoteFrontierState();
        this.checkFrontierConsistency();

        frontierToBasisMap.entrySet().parallelStream()
                .forEach(e -> e.getValue().removeIf(bs -> Apartness.accStatesAreApart(oTree, e.getKey(), bs)));
    }

    public LSMealyMachine<I, O> buildHypothesis() {
        while (true) {
            this.makeObsTreeAdequate();
            LSMealyMachine<I, O> hyp = this.constructHypothesis();

            DefaultQuery<I, Word<O>> ce = this.checkConsistency(hyp);
            if (ce != null) {
                consistentCECount += 1;
                this.processCex(ce, hyp);
            } else {
                return hyp;
            }
        }
    }

    public LSMealyMachine<I, O> constructHypothesis() {
        basisMap.clear();
        frontierTransitionsSet.clear();

        for (Word<I> bAcc : basis) {
            LSState s = new LSState(basisMap.size());
            basisMap.put(bAcc, s);
        }

        NormalObservationTree<I, O> oTree = oqOracle.getTree();
        LinkedList<Word<I>> basisCopy = new LinkedList<>(basis);
        HashSet<Word<I>> loopbacks = new HashSet<>();
        HashMap<Pair<LSState, I>, Pair<LSState, O>> transFunction = new HashMap<>();
        for (Word<I> q : basisCopy) {
            for (I i : inputAlphabet) {
                LSState bs = oTree.getSucc(oTree.defaultState(), q);
                Objects.requireNonNull(bs);
                O output = oTree.getOut(bs, i);
                Objects.requireNonNull(output);
                Word<I> fAcc = q.append(i);

                Pair<Word<I>, Boolean> pair = this.identifyFrontierOrBasis(fAcc);
                Word<I> dest = pair.getFirst();
                Boolean isLoopback = pair.getSecond();

                if (isLoopback) {
                    loopbacks.add(fAcc);
                }

                LSState hypBS = basisMap.get(q);
                Objects.requireNonNull(hypBS);
                LSState hypDest = basisMap.get(dest);
                Objects.requireNonNull(hypDest);
                transFunction.put(Pair.of(hypBS, i), Pair.of(hypDest, output));
            }
        }

        return new LSMealyMachine<I, O>(inputAlphabet, basisMap.values(), new LSState(0), transFunction);
    }

    public Pair<Word<I>, Boolean> identifyFrontierOrBasis(Word<I> seq) {
        if (basis.contains(seq)) {
            return Pair.of(seq, false);
        }

        Word<I> bs = frontierToBasisMap.get(seq).stream().findFirst().get();
        return Pair.of(bs, true);
    }

    public void initObsTree(@Nullable List<Pair<Word<I>, Word<O>>> logs) {
        if (logs != null) {
            for (Pair<Word<I>, Word<O>> pair : logs) {
                oqOracle.addObservation(pair.getFirst(), pair.getSecond());
            }
        }
    }

    public void checkFrontierConsistency() {
        LinkedList<Word<I>> basisSet = new LinkedList<>(basis);
        NormalObservationTree<I, O> oTree = oqOracle.getTree();

        List<Pair<Word<I>, I>> stateInputIterator = new LinkedList<>();
        for (Word<I> bs : basisSet) {
            for (I i : inputAlphabet) {
                stateInputIterator.add(Pair.of(bs, i));
            }
        }
        stateInputIterator.stream().map(p -> {
            Word<I> fsAcc = p.getFirst().append(p.getSecond());
            if (oTree.getSucc(oTree.defaultState(), fsAcc) != null) {
                return fsAcc;
            } else {
                return null;
            }
        }).filter(s -> s != null).filter(x -> !basis.contains(x)).filter(x -> !frontierToBasisMap.containsKey(x))
                .map(fs -> {
                    List<Word<I>> cands = basis.parallelStream().filter(s -> !Apartness.accStatesAreApart(oTree, fs, s))
                            .collect(Collectors.toList());
                    return Pair.of(fs, cands);
                }).forEach(p -> {
                    frontierToBasisMap.put(p.getFirst(), p.getSecond());
                });
    }

    public DefaultQuery<I, Word<O>> checkConsistency(LSMealyMachine<I, O> mealy) {
        NormalObservationTree<I, O> oTree = oqOracle.getTree();
        @Nullable
        Word<I> wit = Apartness.treeAndHypComputeWitness(oTree, oTree.defaultState(), mealy, new LSState(0));
        if (wit == null) {
            return null;
        }

        Word<O> os = oTree.getObservation(null, wit);
        Objects.requireNonNull(os);
        return new DefaultQuery<>(wit, os);
    }

    public Float getADSScore() {
        Integer zero = 0;
        return zero.floatValue();
    }

    @Override
    public void startLearning() {
        this.initObsTree(null);
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ceQuery) {
        LSMealyMachine<I, O> oldHyp = (new Cloner()).deepClone(buildHypothesis());
        processCex(ceQuery, oldHyp);
        MealyMachine<?, I, ?, O> newHyp = getHypothesisModel();
        return DeterministicEquivalenceTest.findSeparatingWord(oldHyp, newHyp, inputAlphabet) != null;
    }

    @Override
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        return buildHypothesis();
    }
}
