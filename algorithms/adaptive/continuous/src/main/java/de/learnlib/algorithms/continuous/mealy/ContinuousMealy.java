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
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
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
            if (hyp.computeOutput(query) == answer) {
                query = sampleWord();
            } else {
                activity = new InitActivity(query);
                query = hyp.getState(Word.epsilon()).concat(query);
            }
        }
    }

    public class QueryActivity implements Activity<O> {
        public final Queue<Word<I>> queue = new LinkedList<>();
        public Word<I> suffix;

        @SafeVarargs
        public QueryActivity(Word<I> suffix, Word<I>... queries) {
            this.suffix = suffix;
            for (Word<I> query : queries) {
                this.queue.add(query);
            }
        }

        @Override
        public void process(Word<O> answer) {
            Word<I> word = queue.poll();
            if (!queue.isEmpty()) {
                if (tree.getLeaves().contains(word)) {
                    query = queue.peek();
                } else {
                    query = sampleWord();
                    activity = new TestActivity();
                }
            } else {
                Word<I> nextQuery = hypothesisQuery(tree);
                if (nextQuery == null) {
                    query = sampleWord();
                    activity = new TestActivity();
                } else {
                    query = nextQuery;
                    activity = new HypActivity();
                }
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
            if (hyp.computeOutput(cex) == answer) {
                return;
            } else {
                int mismatchIdx = MealyUtil.findMismatch(hyp.toCompactMealy(), cex, answer);

                if (mismatchIdx == MealyUtil.NO_MISMATCH) {
                    return;
                }

                Word<I> effInput = cex.prefix(mismatchIdx + 1);
                Word<O> effOutput = answer.prefix(mismatchIdx + 1);

                finishCounterexample(hyp.computeOutput(cex).lastSymbol(), effOutput.lastSymbol(),
                        hyp.getState(effInput), effInput.prefix(effInput.length() - 1), effInput.suffix(1));
            }
        }
    }

    private final Alphabet<I> alphabet;
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
    public ContinuousMealy(Alphabet<I> alphabet, double alpha, MembershipOracle<I, Word<O>> oracle, Random random) {
        // TODO: State.
        this.alphabet = alphabet;
        this.alpha = alpha;
        this.oracle = oracle;
        this.activity = new HypActivity();
        this.query = Word.epsilon();
        this.tree = new MultiICNode<>(Word.epsilon());
        tree.origins.add(Word.epsilon());
        this.alphabet.forEach(s -> tree.origins.add(Word.fromSymbols(s)));
        this.RAND = random;
    }

    private Pair<ICHypothesisMealy<I, O>, Word<I>> update(Word<O> answer) {
        applyAnswers(Collections.singleton(new DefaultQuery<>(query, answer)));
        tree = advanceHypothesis(query, answer, tree);
        activity.process(answer);
        ICHypothesisMealy<I, O> hyp = extractHypothesis(new HashSet<>(), tree);
        return Pair.of(hyp, query);
    }

    private ICHypothesisMealy<I, O> extractHypothesis(Set<Word<I>> extraOrigins, MultiICNode<I, O> localTree) {
        if (localTree.isLeaf()) {
            Word<I> initial = null;
            if (localTree.origins.contains(Word.epsilon()) || extraOrigins.contains(Word.epsilon())) {
                initial = localTree.accessSequence;
            }

            ICHypothesisMealy<I, O> hyp = new ICHypothesisMealy<>(alphabet);
            hyp.setInitial(initial);

            for (Word<I> origin : localTree.origins) {
                if (!origin.equals(Word.epsilon())) {
                    hyp.addTransition(origin.prefix(origin.length() - 1), origin.lastSymbol(),
                            localTree.accessSequence, localTree.outputs.get(origin.lastSymbol()));
                }
            }

            for (Word<I> origin : extraOrigins) {
                if (!origin.equals(Word.epsilon())) {
                    hyp.addTransition(origin.prefix(origin.length() - 1), origin.lastSymbol(),
                            localTree.accessSequence, localTree.outputs.get(origin.lastSymbol()));
                }
            }

            return hyp;
        } else {
            Set<Word<I>> leftOrigins = new HashSet<>();
            leftOrigins.addAll(extraOrigins);
            leftOrigins.addAll(localTree.origins);

            LinkedList<MultiICNode<I, O>> children = new LinkedList<>(localTree.getChildrenNative());
            Set<ICHypothesisMealy<I, O>> childHyps = new HashSet<>();
            childHyps.add(extractHypothesis(leftOrigins, children.pop()));

            for (MultiICNode<I, O> child : children) {
                childHyps.add(extractHypothesis(new HashSet<>(), child));
            }

            ICHypothesisMealy<I, O> hyp = new ICHypothesisMealy<>(alphabet);

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
            tree.restrictOrigins(tree.getLeaves());
        }
        tree = adjustStructure(answers, tree);
    }

    private MultiICNode<I, O> adjustStructure(Set<DefaultQuery<I, Word<O>>> answers, MultiICNode<I, O> tree) {
        return adjustStructure(answers, new HashSet<>(), tree);
    }

    private MultiICNode<I, O> adjustStructure(Set<DefaultQuery<I, Word<O>>> answers, Set<Word<I>> removed,
            MultiICNode<I, O> tree) {
        if (tree.isLeaf()) {
            tree.origins.removeAll(removed);
            for (DefaultQuery<I, Word<O>> answer : answers) {
                Word<I> input = answer.getInput().prefix(answer.getInput().length() - 1);
                if (input.equals(tree.accessSequence)) {
                    tree.outputs.put(answer.getInput().lastSymbol(), answer.getOutput().lastSymbol());
                    return tree;
                }
            }
        } else {
            HashMap<O, AbstractWordBasedDTNode<I, O, Object>> children = new HashMap<>(tree.getChildMap());
            HashMap<O, Set<Word<I>>> sortOrigins = new HashMap<>();

            for (Entry<O, AbstractWordBasedDTNode<I, O, Object>> child : children.entrySet()) {
                ((MultiICNode<I, O>) child.getValue()).getNodeOrigins().stream().forEach(t -> {
                    if (!removed.contains(t)) {
                        for (DefaultQuery<I, Word<O>> query : answers) {
                            if (query.getInput().equals(t.concat(tree.getDiscriminator()))) {
                                Set<Word<I>> set = sortOrigins.getOrDefault(query.getOutput().lastSymbol(),
                                        new HashSet<>());
                                set.add(t);
                                sortOrigins.put(query.getOutput().lastSymbol(), set);
                            }
                        }
                    }
                });
            }

            for (Entry<O, AbstractWordBasedDTNode<I, O, Object>> child : children.entrySet()) {
                Set<Word<I>> adjust = new HashSet<>(removed);
                sortOrigins.entrySet().stream().filter(e -> e.getKey() != child.getKey()).map(e -> e.getValue())
                        .forEach(s -> adjust.addAll(s));

                MultiICNode<I, O> prime = adjustStructure(answers, adjust, (MultiICNode<I, O>) child.getValue());
                children.put(child.getKey(), prime);
                tree.setChild(child.getKey(), prime);
            }

            for (Entry<O, AbstractWordBasedDTNode<I, O, Object>> child : children.entrySet()) {
                ((MultiICNode<I, O>) child.getValue()).origins.addAll(sortOrigins.get(child.getKey()));
            }

            tree.replaceChildren(children);
            tree.origins.removeAll(removed);
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
            Map<O, AbstractWordBasedDTNode<I, O, Object>> childPrime = new HashMap<>();// new childs
            for (O o : tree.getChildMap().keySet()) {
                remove.clear();
                DiscriminationTreeIterators.leafIterator(tree.getChild(o)).forEachRemaining(l -> {
                    for (DefaultQuery<I, Word<O>> query : answers) {
                        if (query.getInput()
                                .equals(((MultiICNode<I, O>) l).accessSequence.concat(tree.getDiscriminator()))
                                && !query.getOutput().equals(o)) {
                            remove.add(((MultiICNode<I, O>) l).accessSequence);
                        }
                    }
                });
                remove.addAll(removed);
                Pair<MultiICNode<I, O>, Set<Word<I>>> adjustment = adjustStates(answers, remove, tree.getChild(o));
                if (adjustment.getFirst() == null) {
                    tree.removeChild(o);
                    origins.addAll(adjustment.getSecond());
                } else {
                    childPrime.put(o, adjustment.getFirst());
                }
            }
            if (tree.isLeaf()) {
                origins.addAll(tree.origins);
                return Pair.of(null, origins);
            }
            tree.replaceChildren(childPrime);
            // replace node by unique child
            if (tree.getChildren().size() == 1) {
                for (O o : tree.getChildMap().keySet()) {
                    MultiICNode<I, O> node = tree.getChild(o);
                    node.origins.addAll(origins);
                    node.origins.addAll(tree.origins);
                    node.setParent(null);
                    node.setParentOutcome(null);
                    return Pair.of(node, new HashSet<>());
                }
            }
            tree.origins.addAll(origins);
            return Pair.of(tree, new HashSet<>());
        }
    }

    private MultiICNode<I, O> advanceHypothesis(Word<I> query, Word<O> answer, MultiICNode<I, O> tree) {
        return advanceHypothesis(query, answer, new HashSet<>(), tree);
    }

    private MultiICNode<I, O> advanceHypothesis(Word<I> query, Word<O> answer, Set<Word<I>> extraOrigins,
            MultiICNode<I, O> tree) {
        MultiICNode<I, O> newNode = new MultiICNode<>();
        if (tree.isLeaf()) {
            newNode.accessSequence = tree.accessSequence;
            newNode.origins.addAll(tree.origins);
            newNode.origins.addAll(extraOrigins);
            newNode.outputs.putAll(tree.outputs);

            if (tree.accessSequence.equals(query.prefix(query.length() - 1))) {
                newNode.outputs.put(query.lastSymbol(), answer.lastSymbol());
            }

        } else {
            newNode.setDiscriminator(tree.getDiscriminator());
            newNode.setParent(null);
            newNode.setParentOutcome(null);

            Set<Word<I>> allTrans = new HashSet<>(tree.origins);
            allTrans.addAll(extraOrigins);
            Set<Word<I>> moved = allTrans.stream().filter(t -> t.concat(tree.getDiscriminator()).equals(query))
                    .collect(Collectors.toSet());

            Set<Word<I>> remaining = allTrans.stream().filter(t -> !moved.contains(t)).collect(Collectors.toSet());

            newNode.origins.clear();
            newNode.origins.addAll(remaining);
            if (moved.isEmpty()) {
                HashMap<O, AbstractWordBasedDTNode<I, O, Object>> children = new HashMap<>(tree.getChildMap());
                for (Entry<O, AbstractWordBasedDTNode<I, O, Object>> child : children.entrySet()) {
                    children.put(child.getKey(),
                            advanceHypothesis(query, answer, (MultiICNode<I, O>) child.getValue()));
                }
            } else {
                HashMap<O, AbstractWordBasedDTNode<I, O, Object>> children = new HashMap<>(tree.getChildMap());
                for (Entry<O, AbstractWordBasedDTNode<I, O, Object>> child : children.entrySet()) {
                    if (child.getKey().equals(answer)) {
                        children.put(child.getKey(),
                                advanceHypothesis(query, answer, moved, (MultiICNode<I, O>) child.getValue()));
                    } else {
                        children.put(child.getKey(),
                                advanceHypothesis(query, answer, (MultiICNode<I, O>) child.getValue()));
                    }
                }
            }
        }
        return newNode;
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
            for (AbstractWordBasedDTNode<I, O, Object> child : tree.getChildren()) {
                Word<I> result = hypothesisQuery((MultiICNode<I, O>) child);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
    }

    private Word<I> sampleWord() {
        if (RAND.nextFloat() < alpha) {
            List<I> alphas = new LinkedList<>(alphabet);
            Collections.shuffle(alphas, RAND);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    private Set<DefaultQuery<I, Word<O>>> implications(Word<I> leaf, Set<DefaultQuery<I, Word<O>>> current,
            MultiICNode<I, O> currentTree) {
        if (currentTree.isLeaf()) {
            if (currentTree.accessSequence.equals(leaf)) {
                return current.stream().map(q -> new DefaultQuery<>(leaf.concat(q.getInput()), q.getOutput()))
                        .collect(Collectors.toSet());

            }
            return new HashSet<>();
        }

        Set<DefaultQuery<I, Word<O>>> result = new HashSet<>();
        for (Entry<O, AbstractWordBasedDTNode<I, O, Object>> child : currentTree.getChildEntries()) {
            Set<DefaultQuery<I, Word<O>>> childCurrent = new HashSet<>(current);
            childCurrent.add(new DefaultQuery<>(currentTree.getDiscriminator(), Word.fromLetter(child.getKey())));
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
            for (Entry<O, AbstractWordBasedDTNode<I, O, Object>> child : tree.getChildEntries()) {
                tree.setChild(child.getKey(), replaceLeaf(s, node, (MultiICNode<I, O>) child.getValue()));
            }
            return tree;
        }
        return tree;
    }

    private void finishCounterexample(O shrtSym, O lngSym, Word<I> shrt, Word<I> lng, Word<I> e) {
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

        // FIXME: Should implications be a list? Does order matter?
        Set<DefaultQuery<I, Word<O>>> newImp = implications(lng, new HashSet<>(), tree);
        newImp.add(new DefaultQuery<>(shrt.concat(e), Word.fromLetter(shrtSym)));
        applyAnswers(newImp);

        activity = new QueryActivity(e, shrt, lng);
        query = shrt.concat(e);
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