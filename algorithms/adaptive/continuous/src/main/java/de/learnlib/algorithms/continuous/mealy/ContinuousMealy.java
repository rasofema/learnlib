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

import java.util.Collections;
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

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.discriminationtree.BinaryDTree;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import de.learnlib.util.mealy.MealyUtil;
import net.automatalib.automata.base.compact.CompactTransition;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.automata.visualization.MealyVisualizationHelper;
import net.automatalib.commons.util.Pair;
import net.automatalib.visualization.Visualization;
import net.automatalib.visualization.VisualizationHelper;
import net.automatalib.visualization.VisualizationProvider;
import net.automatalib.visualization.dot.GraphVizBrowserVisualizationProvider;
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
            answer = answer.suffix(cex.length());
            ICHypothesisMealy<I, O> hyp = extractHypothesis(new HashSet<>(), tree);
            if (hyp.computeOutput(cex).equals(answer)) {
                finishCounterexample(oracle.answerQuery(hyp.getState(Word.epsilon()), cex),
                        oracle.answerQuery(Word.epsilon(), cex), hyp.getState(Word.epsilon()), Word.epsilon(), cex);
            } else {
                int mismatchIdx = MealyUtil.findMismatch(hyp.toCompactMealy(), cex, answer);
                if (mismatchIdx == MealyUtil.NO_MISMATCH) {
                    return;
                }

                Word<I> effInput = cex.prefix(mismatchIdx + 1);
                splitCounterexample(Word.epsilon(), effInput, Word.epsilon());
            }
        }
    }

    public class CexActivity implements Activity<O> {
        public Word<I> pre;
        public Word<I> u;
        public Word<I> v;
        public Word<I> post;
        public int index;

        public CexActivity(Word<I> pre, Word<I> u, Word<I> v, Word<I> post, int index) {
            this.pre = pre;
            this.u = u;
            this.v = v;
            this.post = post;
            this.index = index;
        }

        @Override
        public void process(Word<O> answer) {
            answer = answer.suffix(index);
            ICHypothesisMealy<I, O> hyp = extractHypothesis(new HashSet<>(), tree);
            if (this.u.size() == 1 && this.v.equals(Word.epsilon())) {
                Word<I> shrt = Objects.requireNonNull(hyp.getState(this.pre.concat(this.u)));
                Word<I> lng = Objects.requireNonNull(hyp.getState(this.pre)).concat(this.u);
                // FIXME: Direct call to oracle.
                finishCounterexample(oracle.answerQuery(shrt, post), oracle.answerQuery(lng, post), shrt, lng, post);
            } else if (hyp.computeSuffixOutput(query.prefix(query.length() - index), query.suffix(index))
                    .equals(answer)) {
                splitCounterexample(this.pre, this.u, this.v.concat(this.post));
            } else {
                splitCounterexample(this.pre.concat(this.u), this.v, this.post);
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
        if (query.length() > 0) {
            applyAnswers(Collections.singleton(new DefaultQuery<>(query, answer)));
            advanceHypothesis(query, answer);
        }
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

    private Boolean originConflictsAnswers(Word<I> origin, Set<DefaultQuery<I, Word<O>>> answers) {
        Set<DefaultQuery<I, Word<O>>> implications = originImplications(origin);

        for (DefaultQuery<I, Word<O>> implication : implications) {
            for (DefaultQuery<I, Word<O>> answer : answers) {
                if (implication.getPrefix().equals(answer.getPrefix())) {
                    int smallestOutput = Math.min(implication.getOutput().length(), answer.getOutput().length());
                    if (implication.getSuffix().prefix(smallestOutput).equals(answer.getSuffix().prefix(smallestOutput))
                            && !implication.getOutput().prefix(smallestOutput)
                                    .equals(answer.getOutput().prefix(smallestOutput))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private MultiICNode<I, O> adjustStructure(Set<DefaultQuery<I, Word<O>>> answers, MultiICNode<I, O> tree) {
        Set<Word<I>> originsToFix = tree.getNodeOrigins().stream().filter(o -> originConflictsAnswers(o, answers))
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
                        if (query.getPrefix().equals(((MultiICNode<I, O>) l).accessSequence)
                                && query.getSuffix().equals(treeDiscriminator) && !query.getOutput().equals(o)
                                || (query.getPrefix().size() == 0 && (query.getInput()
                                        .equals(((MultiICNode<I, O>) l).accessSequence.concat(treeDiscriminator))
                                        && !query.getOutput().suffix(o.length()).equals(o)))) {
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

    private void advanceOutputs(Word<I> query, Word<O> answer) {
        DiscriminationTreeIterators.leafIterator(tree).forEachRemaining(l -> {
            MultiICNode<I, O> leaf = (MultiICNode<I, O>) l;
            if (leaf.accessSequence.equals(query.prefix(query.length() - 1))) {
                leaf.outputs.put(query.lastSymbol(), answer.lastSymbol());
            }
        });
    }

    private void advanceOrigins(Word<I> query, Word<O> answer) {
        Set<MultiICNode<I, O>> nodes = new HashSet<>();
        DiscriminationTreeIterators.nodeIterator(tree).forEachRemaining(n -> {
            nodes.add((MultiICNode<I, O>) n);
        });

        boolean advanced = true;
        while (advanced) {
            advanced = false;
            for (MultiICNode<I, O> node : nodes) {
                if (!node.isLeaf()) {
                    Set<Word<I>> originsRemoved = new HashSet<>();
                    Set<Word<I>> nodeOrigins = new HashSet<>(node.origins);
                    for (Word<I> origin : nodeOrigins) {
                        if (origin.concat(node.getDiscriminator()).equals(query)
                                && origin.concat(node.getDiscriminator()).length() == query.length()) {
                            if (node.child(answer.suffix(node.getDiscriminator().length())) == null) {
                                MultiICNode<I, O> answerChild = new MultiICNode<>();
                                answerChild.accessSequence = query
                                        .prefix(query.length() - node.getDiscriminator().length());
                                answerChild.setParent(node);
                                answerChild.setParentOutcome(answer);
                                node.setChild(answer.suffix(node.getDiscriminator().length()), answerChild);
                                this.alphabet.stream()
                                        .forEach(a -> this.tree.origins.add(answerChild.accessSequence.append(a)));

                                // We now have new origins that need to be pushed down, outputs to compute, etc.
                                activity = new HypActivity();
                            }
                            node.child(answer.suffix(node.getDiscriminator().length())).origins.add(origin);
                            originsRemoved.add(origin);
                            advanced = true;
                        }
                    }
                    node.origins.removeAll(originsRemoved);
                }
            }
        }

    }

    private void advanceHypothesis(Word<I> query, Word<O> answer) {
        advanceOutputs(query, answer);
        advanceOrigins(query, answer);
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

    private void splitCounterexample(Word<I> pre, Word<I> middle, Word<I> post) {
        Word<I> u = middle.prefix((middle.length() + 1) / 2);
        Word<I> v = middle.suffix((middle.length()) / 2);

        assert u.concat(v).equals(middle) && ((u.length() - v.length()) == 0 || (u.length() - v.length()) == 1);
        ICHypothesisMealy<I, O> hyp = extractHypothesis(new HashSet<>(), tree);
        activity = new CexActivity(pre, u, v, post, v.concat(post).length());
        query = hyp.getState(pre.concat(u)).concat(v).concat(post);
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
            alphabet.forEach(a -> this.tree.origins.add(lng.append(a)));
        }

        Set<DefaultQuery<I, Word<O>>> newImp = implications(lng, new HashSet<>(), tree);
        newImp.add(new DefaultQuery<>(shrt, e, shrtSym));
        applyAnswers(newImp);

        activity = new HypActivity();
        activity.process(null);
    }

    private void visualise(ICHypothesisMealy<I, O> hyp) {
        new GraphVizBrowserVisualizationProvider().visualize(hyp.transitionGraphView(),
                Collections.singletonList(new MealyVisualizationHelper<>(hyp)), false, new HashMap<String, String>());
    }

    private void visualiseTree(MultiICNode<I, O> tree) {
        new GraphVizBrowserVisualizationProvider().visualize(tree.asNormalGraph(),
                Collections.singletonList(tree.getVisualizationHelper()), false, new HashMap<String, String>());
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