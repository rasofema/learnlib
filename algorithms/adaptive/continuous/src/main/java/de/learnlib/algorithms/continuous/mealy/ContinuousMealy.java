/* Copyright (C) 2021-2022 University College London
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
package de.learnlib.algorithms.continuous.mealy;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.acex.impl.AbstractBaseCounterexample;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import de.learnlib.datastructure.discriminationtree.model.LCAInfo;
import de.learnlib.util.mealy.MealyUtil;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.commons.util.Pair;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * The Continuous algorithm for tree-based adaptive learning.
 *
 * @param <I> input symbol type
 *
 * @author Tiago Fereira
 */
public class ContinuousMealy<I, O> {

    public interface Activity<O> {
        void process(Word<O> answer);
    }

    public class TestActivity implements Activity<O> {
        @Override
        public void process(Word<O> answer) {
            ICHypothesisMealy<I, O> hyp = extractHypothesis(Collections.emptySet(), tree);
            if (hyp.computeOutput(query).equals(answer)) {
                query = sampleWord();
            } else {
                activity = new InitActivity(query);
                query = hyp.getState(Word.epsilon()).concat(query);
            }
        }
    }

    public class HypActivity implements Activity<O> {
        @Override
        public void process(Word<O> answer) {
            Word<I> nextQuery = hypothesisQuery(tree);
            if (nextQuery == null) {
                query = sampleWord();
                activity = new TestActivity();
            } else {
                query = nextQuery;
            }
        }
    }

    public class InitActivity implements Activity<O> {
        public Word<I> cex;

        public InitActivity(Word<I> cex) {
            this.cex = cex;
        }

        @Override
        public void process(Word<O> answer) {
            ICHypothesisMealy<I, O> hyp = extractHypothesis(new HashSet<>(), tree);
            if (hyp.computeOutput(cex).equals(answer)) {
                return;
            } else {
                int mismatchIdx = MealyUtil.findMismatch(hyp.toCompactMealy(), cex, answer);

                if (mismatchIdx == MealyUtil.NO_MISMATCH) {
                    return;
                }

                Word<I> effInput = cex.prefix(mismatchIdx + 1);
                Word<O> effOutput = answer.prefix(mismatchIdx + 1);

                finishCounterexample(hyp.computeOutput(effInput).suffix(1), effOutput.suffix(1),
                        hyp.getState(effInput), effInput.prefix(effInput.length() - 1), effInput.suffix(1));
            }
        }
    }

    private final Alphabet<I> alphabet;
    private final O defaultOutputSymbol;
    private final double alpha;
    private final MembershipOracle<I, Word<O>> oracle;
    private final Random RAND;
    private Activity<O> activity;
    private MultiICNode<I, O> tree;
    private Word<I> query;

    /**
     * Constructor.
     *
     * @param alphabet the learning alphabet
     * @param oracle   the membership oracle
     */
    public ContinuousMealy(Alphabet<I> alphabet, O defaultOutputSymbol, double alpha,
            MembershipOracle<I, Word<O>> oracle, Random random) {
        // TODO: State.
        this.alphabet = alphabet;
        this.alpha = alpha;
        this.defaultOutputSymbol = defaultOutputSymbol;
        this.oracle = oracle;
        this.activity = new HypActivity();
        this.query = Word.fromLetter(alphabet.iterator().next());
        this.tree = new MultiICNode<>(Word.epsilon());
        tree.origins.add(Word.epsilon());
        this.alphabet.forEach(s -> tree.origins.add(Word.fromSymbols(s)));
        this.RAND = random;
    }

    private Pair<ICHypothesisMealy<I, O>, Word<I>> update(Word<O> answer) {
        assert tree.getNodeOrigins().size() == (tree.getLeaves().size() * alphabet.size()) + 1;
        applyAnswers(Collections.singleton(new DefaultQuery<>(query, answer)));
        Pair<MultiICNode<I, O>, Set<Word<I>>> advanced = advanceHypothesis(query, answer, tree);
        tree = advanced.getFirst();
        tree.origins.addAll(advanced.getSecond());
        activity.process(answer);
        ICHypothesisMealy<I, O> hyp = extractHypothesis(new HashSet<>(), tree);
        return Pair.of(hyp, query);
    }

    private ICHypothesisMealy<I, O> extractHypothesis(Set<Word<I>> extraOrigins, MultiICNode<I, O> localTree) {
        Set<Word<I>> origins = new HashSet<>(localTree.origins);
        origins.addAll(extraOrigins);

        if (localTree.isLeaf()) {
            Word<I> initial = null;
            if (localTree.origins.contains(Word.epsilon()) || extraOrigins.contains(Word.epsilon())) {
                initial = localTree.accessSequence;
            }

            ICHypothesisMealy<I, O> hyp = new ICHypothesisMealy<>(alphabet, defaultOutputSymbol);
            hyp.setInitial(initial);

            HashMap<Word<I>, MultiICNode<I, O>> accessSeqToLeaf = new HashMap<>();
            DiscriminationTreeIterators.leafIterator(this.tree).forEachRemaining(l -> {
                MultiICNode<I, O> originLeaf = (MultiICNode<I, O>) l;
                accessSeqToLeaf.put(originLeaf.accessSequence, originLeaf);
            });

            for (Word<I> origin : origins) {
                if (!origin.equals(Word.epsilon())) {
                    Word<I> originString = origin.prefix(origin.length() - 1);
                    hyp.addTransition(originString, origin.lastSymbol(), localTree.accessSequence,
                            accessSeqToLeaf.get(originString).outputs.getOrDefault(origin.lastSymbol(),
                                    defaultOutputSymbol));
                }
            }

            return hyp;
        } else {
            LinkedList<MultiICNode<I, O>> children = new LinkedList<>(localTree.getChildrenNative());
            Set<ICHypothesisMealy<I, O>> childHyps = new HashSet<>();
            childHyps.add(extractHypothesis(origins, children.pop()));

            for (MultiICNode<I, O> child : children) {
                childHyps.add(extractHypothesis(new HashSet<>(), child));
            }

            ICHypothesisMealy<I, O> hyp = new ICHypothesisMealy<>(alphabet, defaultOutputSymbol);

            for (ICHypothesisMealy<I, O> childHyp : childHyps) {
                if (childHyp.getInitialState() != null) {
                    hyp.setInitial(childHyp.getInitialState());
                    break;
                }
            }

            Set<Map.Entry<Pair<Word<I>, I>, Word<I>>> unionTrans = new HashSet<>();
            for (ICHypothesisMealy<I, O> childHyp : childHyps) {
                unionTrans.addAll(childHyp.transitions.entrySet());
            }

            Map<Pair<Word<I>, I>, O> unionOutputs = new HashMap<>();
            for (ICHypothesisMealy<I, O> childHyp : childHyps) {
                unionOutputs.putAll(childHyp.outputs);
            }

            for (Map.Entry<Pair<Word<I>, I>, Word<I>> trans : unionTrans) {
                hyp.addTransition(trans.getKey().getFirst(), trans.getKey().getSecond(), trans.getValue(),
                        unionOutputs.get(trans.getKey()));
            }

            return hyp;
        }
    }

    private void applyAnswers(Set<DefaultQuery<I, Word<O>>> answers) {
        Pair<MultiICNode<I, O>, Set<Word<I>>> stateAdjustment = adjustStates(answers, tree);
        tree = stateAdjustment.getFirst();

        if (tree == null) {
            this.tree = new MultiICNode<>(Word.epsilon());
            tree.origins.add(Word.epsilon());
            this.alphabet.forEach(s -> tree.origins.add(Word.fromSymbols(s)));
        } else {
            Set<Word<I>> origins = new HashSet<>();
            origins.add(Word.epsilon());
            for (Word<I> leaf : tree.getLeaves()) {
                for (I a : alphabet) {
                    origins.add(leaf.append(a));
                }
            }
            tree.restrictOrigins(origins);
        }
        tree = adjustStructure(answers, tree);
    }

    private Set<DefaultQuery<I, Word<O>>> originImplications(Word<I> origin) {
        AtomicReference<MultiICNode<I, O>> targetNodeRef = new AtomicReference<>();
        DiscriminationTreeIterators.nodeIterator(tree).forEachRemaining(n -> {
            MultiICNode<I, O> node = (MultiICNode<I, O>) n;
            if (node.origins.contains(origin)) {
                targetNodeRef.set(node);
            }
        });

        MultiICNode<I, O> targetNode = targetNodeRef.get();
        assert targetNode != null;

        Set<DefaultQuery<I, Word<O>>> implications = new HashSet<>();

        while (!targetNode.isRoot()) {
            implications.add(new DefaultQuery<>(origin, targetNode.getParent().getDiscriminator(),
                    targetNode.getParentOutcome()));
            targetNode = (MultiICNode<I, O>) targetNode.getParent();
        }

        return implications;
    }

    private Boolean conflictsTreeImplications(Word<I> origin, Set<DefaultQuery<I, Word<O>>> answers) {
        Set<DefaultQuery<I, Word<O>>> implications = originImplications(origin);

        for (DefaultQuery<I, Word<O>> implication : implications) {
            for (DefaultQuery<I, Word<O>> answer : answers) {
                int smallestOutput = Math.min(implication.getOutput().length(), answer.getOutput().length());
                if (implication.getInput().equals(answer.getInput())) {
                    if (implication.getOutput().suffix(smallestOutput)
                            .equals(answer.getOutput().suffix(smallestOutput))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private MultiICNode<I, O> adjustStructure(Set<DefaultQuery<I, Word<O>>> answers, MultiICNode<I, O> tree) {
        Set<Word<I>> originsToFix = tree.getNodeOrigins().stream().filter(o -> conflictsTreeImplications(o, answers))
                .collect(Collectors.toSet());

        for (Word<I> originToFix : originsToFix) {
            MultiICNode<I, O> currentNode = tree;
            while (currentNode != null) {
                if (currentNode.isLeaf()) {
                    currentNode.origins.add(originToFix);
                    for (DefaultQuery<I, Word<O>> answer : answers) {
                        Word<I> input = answer.getInput().prefix(answer.getInput().length() - 1);
                        if (input.equals(tree.accessSequence)) {
                            tree.outputs.put(answer.getInput().lastSymbol(), answer.getOutput().lastSymbol());
                        }
                    }
                    break;
                } else {
                    Word<I> disc = currentNode.getDiscriminator();
                    DefaultQuery<I, Word<O>> answer = answers.stream()
                            .filter(q -> q.getInput().equals(originToFix.concat(disc))).findFirst().orElse(null);
                    assert answer != null;
                    Word<O> answerSuffix = answer.getOutput().suffix(currentNode.getDiscriminator().length());
                    if (currentNode.getChildMap().keySet().contains(answerSuffix)) {
                        currentNode = (MultiICNode<I, O>) currentNode.getChild(answerSuffix);
                    } else {
                        currentNode.origins.add(originToFix);
                    }
                }
            }
        }
        return tree;

    }

    private Pair<MultiICNode<I, O>, Set<Word<I>>> adjustStates(Set<DefaultQuery<I, Word<O>>> answers,
            MultiICNode<I, O> tree) {
        return adjustStates(answers, new HashSet<>(), tree);
    }

    private Pair<MultiICNode<I, O>, Set<Word<I>>> adjustStates(Set<DefaultQuery<I, Word<O>>> answers,
            Set<Word<I>> removed, MultiICNode<I, O> tree) {
        if (tree.isLeaf()) {
            if (removed.contains(tree.accessSequence)) {
                return Pair.of(null, new HashSet<>(tree.origins));
            }
            return Pair.of(tree, new HashSet<>());
        } else {
            Set<Word<I>> remove = new HashSet<>(); // used for new words to remove for each child
            Set<Word<I>> origins = new HashSet<>(); // gathers all homeless origins
            Map<Word<O>, AbstractWordBasedDTNode<I, Word<O>, Object>> childPrime = new HashMap<>();// new childs
            for (Word<O> o : tree.getChildMap().keySet()) {
                remove.clear();
                Word<I> treeDiscriminator = tree.getDiscriminator();
                DiscriminationTreeIterators.leafIterator(tree.child(o)).forEachRemaining(l -> {
                    for (DefaultQuery<I, Word<O>> query : answers) {
                        if (query.getInput()
                                .equals(((MultiICNode<I, O>) l).accessSequence.concat(treeDiscriminator))
                                && !query.getOutput().suffix(o.length()).equals(o)) {
                            remove.add(((MultiICNode<I, O>) l).accessSequence);
                        }
                    }
                });
                remove.addAll(removed);
                Pair<MultiICNode<I, O>, Set<Word<I>>> adjustment = adjustStates(answers, remove,
                        (MultiICNode<I, O>) tree.child(o));
                if (adjustment.getFirst() == null) {
                    origins.addAll(adjustment.getSecond());
                } else {
                    childPrime.put(o, adjustment.getFirst());
                }
            }

            tree.replaceChildren(childPrime);

            if (tree.isLeaf()) {
                origins.addAll(tree.origins);
                return Pair.of(null, origins);
            }

            // replace node by unique child
            if (tree.getChildren().size() == 1) {
                MultiICNode<I, O> newNode = new MultiICNode<>(tree.getChildrenNative().iterator().next());
                newNode.origins.addAll(origins);
                newNode.origins.addAll(tree.origins);
                newNode.setParent(null);
                newNode.setParentOutcome(null);
                return Pair.of(newNode, new HashSet<>());
            }
            tree.origins.addAll(origins);
            return Pair.of(tree, new HashSet<>());
        }
    }

    private Pair<MultiICNode<I, O>, Set<Word<I>>> advanceHypothesis(Word<I> query, Word<O> answer,
            MultiICNode<I, O> tree) {
        return advanceHypothesis(query, answer, new HashSet<>(), tree);
    }

    private Pair<MultiICNode<I, O>, Set<Word<I>>> advanceHypothesis(Word<I> query, Word<O> answer,
            Set<Word<I>> extraOrigins,
            MultiICNode<I, O> tree) {
        MultiICNode<I, O> newNode = new MultiICNode<>();
        Set<Word<I>> newOrigins = new HashSet<>();
        if (tree.isLeaf()) {
            newNode.accessSequence = tree.accessSequence;
            newNode.origins.addAll(tree.origins);
            newNode.origins.addAll(extraOrigins);
            newNode.outputs.putAll(tree.outputs);

            assert tree.accessSequence != null;
            assert newNode.accessSequence != null;
            if (tree.accessSequence.equals(query.prefix(query.length() - 1))) {
                newNode.outputs.put(query.lastSymbol(), answer.lastSymbol());
            }

        } else {
            newNode.setDiscriminator(tree.getDiscriminator());
            newNode.setParent(null);
            newNode.setParentOutcome(null);

            // Make sure we have the appropriate branch child.
            if (tree.getDiscriminator().equals(query.suffix(answer.length())) && tree.child(answer) == null) {
                MultiICNode<I, O> answerChild = new MultiICNode<>();
                answerChild.accessSequence = query.prefix(query.length() - 1);
                answerChild.setParent(tree);
                answerChild.setParentOutcome(answer);
                tree.setChild(answer, answerChild);
                this.alphabet.stream().forEach(a -> newOrigins.add(answerChild.accessSequence.append(a)));
            }

            Set<Word<I>> allTrans = new HashSet<>(tree.origins);
            allTrans.addAll(extraOrigins);
            Set<Word<I>> moved = allTrans.stream().filter(t -> t.concat(tree.getDiscriminator()).equals(query))
                    .collect(Collectors.toSet());

            Set<Word<I>> remaining = allTrans.stream().filter(t -> !moved.contains(t)).collect(Collectors.toSet());

            newNode.origins.clear();
            newNode.origins.addAll(remaining);

            HashMap<Word<O>, AbstractWordBasedDTNode<I, Word<O>, Object>> children = new HashMap<>(tree.getChildMap());
            for (Entry<Word<O>, AbstractWordBasedDTNode<I, Word<O>, Object>> child : children.entrySet()) {
                if (moved.isEmpty()) {
                    Pair<MultiICNode<I, O>, Set<Word<I>>> advanced = advanceHypothesis(query, answer,
                            (MultiICNode<I, O>) child.getValue());
                    children.put(child.getKey(), advanced.getFirst());
                    newOrigins.addAll(advanced.getSecond());
                } else {
                    if (child.getKey().equals(answer.suffix(child.getKey().length()))) {
                        Pair<MultiICNode<I, O>, Set<Word<I>>> advanced = advanceHypothesis(query, answer, moved,
                                (MultiICNode<I, O>) child.getValue());
                        children.put(child.getKey(), advanced.getFirst());
                        newOrigins.addAll(advanced.getSecond());
                    } else {
                        Pair<MultiICNode<I, O>, Set<Word<I>>> advanced = advanceHypothesis(query, answer,
                                (MultiICNode<I, O>) child.getValue());
                        children.put(child.getKey(), advanced.getFirst());
                        newOrigins.addAll(advanced.getSecond());
                    }
                }
            }

            newNode.replaceChildren(children);
        }
        return Pair.of(newNode, newOrigins);
    }

    private Word<I> hypothesisQuery(MultiICNode<I, O> tree) {
        if (tree.isLeaf()) {
            Set<I> inputs = alphabet.stream().collect(Collectors.toSet());
            inputs.removeAll(tree.outputs.keySet());
            if (!inputs.isEmpty()) {
                return tree.accessSequence.append(inputs.iterator().next());
            }
            return null;
        } else {
            if (!tree.origins.isEmpty()) {
                Word<I> trans = tree.origins.iterator().next();
                return trans.concat(tree.getDiscriminator());
            }
            for (AbstractWordBasedDTNode<I, Word<O>, Object> child : tree.getChildren()) {
                Word<I> result = hypothesisQuery((MultiICNode<I, O>) child);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
    }

    private Word<I> sampleWord() {
        List<I> alphas = new LinkedList<>(alphabet);
        Collections.shuffle(alphas, RAND);
        return Word.fromLetter(alphas.get(0)).concat(RAND.nextFloat() < alpha ? sampleWord() : Word.epsilon());
    }

    private Set<DefaultQuery<I, Word<O>>> implications(Word<I> leaf, Set<DefaultQuery<I, Word<O>>> current,
            MultiICNode<I, O> currentTree) {
        if (currentTree.isLeaf()) {
            if (currentTree.accessSequence.equals(leaf)) {
                return current.stream()
                        .map(q -> new DefaultQuery<>(leaf, q.getInput(), q.getOutput()))
                        .collect(Collectors.toSet());

            }
            return new HashSet<>();
        }

        Set<DefaultQuery<I, Word<O>>> result = new HashSet<>();
        for (Entry<Word<O>, AbstractWordBasedDTNode<I, Word<O>, Object>> child : currentTree.getChildEntries()) {
            Set<DefaultQuery<I, Word<O>>> childCurrent = new HashSet<>(current);
            childCurrent.add(new DefaultQuery<>(Word.epsilon(), currentTree.getDiscriminator(), child.getKey()));
            result.addAll(implications(leaf, childCurrent, (MultiICNode<I, O>) child.getValue()));
        }

        return result;
    }

    private MultiICNode<I, O> replaceLeaf(Word<I> s, MultiICNode<I, O> node, MultiICNode<I, O> tree) {
        if (tree.isLeaf() && tree.accessSequence.equals(s)) {
            node.origins.addAll(tree.origins);
            return node;
        }
        if (!tree.isLeaf()) {
            for (Entry<Word<O>, AbstractWordBasedDTNode<I, Word<O>, Object>> child : tree.getChildEntries()) {
                tree.setChild(child.getKey(), replaceLeaf(s, node, (MultiICNode<I, O>) child.getValue()));
            }
            return tree;
        }
        return tree;
    }

    private void finishCounterexample(Word<O> shrtSym, Word<O> lngSym, Word<I> shrt, Word<I> lng, Word<I> e) {
        // FIXME: This is for debug purposes. In a correct implementation should be
        // guaranteed.
        assert tree.getLeaves().contains(shrt);

        Boolean duplicate = tree.getLeaves().contains(lng);
        MultiICNode<I, O> node = new MultiICNode<>();
        node.setDiscriminator(e);
        MultiICNode<I, O> longNode = new MultiICNode<>(lng);
        MultiICNode<I, O> shortNode = new MultiICNode<>(shrt);
        node.setChild(shrtSym, shortNode);
        node.setChild(lngSym, longNode);

        tree = replaceLeaf(shrt, node, tree);

        if (!duplicate) {
            alphabet.forEach(a -> tree.origins.add(lng.append(a)));
        }

        Set<DefaultQuery<I, Word<O>>> newImp = implications(lng, new HashSet<>(), tree);
        newImp.add(new DefaultQuery<>(shrt, e, shrtSym));
        applyAnswers(newImp);

        activity = new HypActivity();
        activity.process(null);
    }

    public List<Pair<Integer, CompactMealy<I, O>>> learn(int limit, int sample) {
        List<Pair<Integer, CompactMealy<I, O>>> hyps = new LinkedList<>();
        Word<O> answer = oracle.answerQuery(query);
        for (int i = 0; i < limit; i++) {
            Pair<ICHypothesisMealy<I, O>, Word<I>> pair = update(answer);
            ICHypothesisMealy<I, O> hyp = pair.getFirst();
            if (i % sample == 0) {
                hyps.add(Pair.of(i, hyp.toCompactMealy()));
            }
            answer = oracle.answerQuery(pair.getSecond());
        }
        return hyps;
    }
}