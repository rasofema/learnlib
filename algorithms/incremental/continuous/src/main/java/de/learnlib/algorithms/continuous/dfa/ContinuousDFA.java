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
package de.learnlib.algorithms.continuous.dfa;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import de.learnlib.algorithms.continuous.base.ICNode;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.query.Query;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.commons.util.Pair;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * The Kearns/Vazirani algorithm for learning DFA, as described in the book "An Introduction to Computational Learning
 * Theory" by Michael Kearns and Umesh Vazirani.
 *
 * @param <I>
 *         input symbol type
 *
 * @author Malte Isberner
 */
public class ContinuousDFA<I> {

    public enum Activity {
        HYP,
        TEST,
        INIT,
        CEX
    }

    private class BinarySearchState {
        public Word<I> pre;
        public Word<I> u;
        public Word<I> v;
        public Word<I> post;

        public BinarySearchState(Word<I> pre, Word<I> u, Word<I> v, Word<I> post) {
            this.pre = pre;
            this.u = u;
            this.v = v;
            this.post = post;
        }
    }

    private class TestState {
        public Set<Word<I>> queue;
        public Word<I> shrt;
        public Word<I> lng;
        public Word<I> post;
        public Boolean swap;


        public TestState(Set<Word<I>> queue, Word<I> shrt, Word<I> lng, Word<I> post, Boolean swap) {
            this.queue = queue;
            this.shrt = shrt;
            this.lng = lng;
            this.post = post;
            this.swap = swap;
        }
    }

    private final Alphabet<I> alphabet;
    private final double alpha;
    private final MembershipOracle<I, Boolean> oracle;
    private final Random RAND;
    private Activity activity;
    private ICNode<I> tree;
    private Word<I> query;

    private Word<I> activityINIT;
    private BinarySearchState activityCEX;
    private TestState activityTEST;

    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet
     * @param oracle
     *         the membership oracle
     */
    public ContinuousDFA(Alphabet<I> alphabet, double alpha, MembershipOracle<I, Boolean> oracle, Random random) {
        // TODO: State.
        this.alphabet = alphabet;
        this.alpha = alpha;
        this.oracle = oracle;
        this.activity = Activity.HYP;
        this.query = Word.epsilon();
        this.tree = new ICNode<>(Word.epsilon());
        tree.targets.add(Word.epsilon());
        this.alphabet.forEach(s ->  tree.targets.add(Word.fromSymbols(s)));
        this.RAND = random;
    }

    private Pair<ICHypothesisDFA<I>, Word<I>> nextState(Boolean answer) {
        applyAnswers(Collections.singleton(new DefaultQuery<>(query, answer)));
        if (activity == Activity.HYP && hypothesise(answer)) {
            testRandom();
        } else {
            switch (activity) {
                case TEST:
                    test(answer);
                    break;
                case HYP:
                    break;
                case INIT:
                    initialiseCounterexample(answer, activityINIT);
                    break;
                case CEX:
                    handleCounterexample(answer, activityCEX);
                    break;
            }
        }

        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        return Pair.of(hyp, query);
    }

    private ICHypothesisDFA<I> extractHypothesis(Set<Word<I>> extraTargets, ICNode<I> localTree) {
        if (localTree.isLeaf()) {
            Word<I> initial = null;
            if (localTree.targets.contains(Word.epsilon()) || extraTargets.contains(Word.epsilon())) {
                initial = localTree.accessSequence;
            }

            ICHypothesisDFA<I> hyp = new ICHypothesisDFA<>(alphabet);
            hyp.setInitial(initial);

            for (Word<I> target : localTree.targets) {
                if (!target.equals(Word.epsilon())) {
                    hyp.addTransition(target.prefix(target.length() - 1), target.lastSymbol(), localTree.accessSequence);
                }
            }

            for (Word<I> target : extraTargets) {
                if (!target.equals(Word.epsilon())) {
                    hyp.addTransition(target.prefix(target.length() - 1), target.lastSymbol(), localTree.accessSequence);
                }
            }

            hyp.setAccepting(localTree.accessSequence, localTree.accepting != null
                ? localTree.accepting
                : false);

            return hyp;
        } else {
            Set<Word<I>> leftTargets = new HashSet<>();
            leftTargets.addAll(extraTargets);
            leftTargets.addAll(localTree.targets);

            ICHypothesisDFA<I> leftHyp = extractHypothesis(leftTargets, localTree.getChild(false));
            ICHypothesisDFA<I> rightHyp = extractHypothesis(new HashSet<>(), localTree.getChild(true));

            ICHypothesisDFA<I> hyp = new ICHypothesisDFA<>(alphabet);
            Word<I> initial = leftHyp.getInitialState() != null
                ? leftHyp.getInitialState()
                : rightHyp.getInitialState();
            hyp.setInitial(initial);

            Set<Map.Entry<Pair<Word<I>, I>, Word<I>>> unionTrans = new HashSet<>(leftHyp.transitions.entrySet());
            unionTrans.addAll(rightHyp.transitions.entrySet());
            for (Map.Entry<Pair<Word<I>, I>, Word<I>> trans : unionTrans) {
                hyp.addTransition(trans.getKey().getFirst(), trans.getKey().getSecond(), trans.getValue());
            }

            Set<Map.Entry<Word<I>, Boolean>> unionAcc = new HashSet<>(leftHyp.acceptance.entrySet());
            unionAcc.addAll(rightHyp.acceptance.entrySet());
            for (Map.Entry<Word<I>, Boolean> acc : unionAcc) {
                hyp.setAccepting(acc.getKey(), acc.getValue());
            }

            return hyp;
        }
    }

    private void applyAnswers(Set<DefaultQuery<I, Boolean>> answers) {
        Pair<ICNode<I>, Set<Word<I>>>
            stateAdjustment = adjustStates(answers, tree);
        tree = stateAdjustment.getFirst();
        if (tree == null) {
            this.tree = new ICNode<>(Word.epsilon());
            tree.targets.add(Word.epsilon());
            this.alphabet.forEach(s ->  tree.targets.add(Word.fromSymbols(s)));
        } else {
            Set<Word<I>> origins = new HashSet<>();
            DiscriminationTreeIterators.leafIterator(tree).forEachRemaining(n -> origins.add(((ICNode<I>) n).accessSequence));
            tree = restrictTargets(tree, origins);
        }
        tree = adjustStructure(answers, tree);
    }

    private Set<Word<I>> getTargets(ICNode<I> tree) {
        HashSet<Word<I>> targets = new HashSet<>();
        DiscriminationTreeIterators.nodeIterator(tree).forEachRemaining(n -> targets.addAll(((ICNode<I>) n).targets));
        return targets;
    }

    private ICNode<I>
    adjustStructure(Set<DefaultQuery<I, Boolean>> answers, ICNode<I> tree) {
        return adjustStructure(answers, new HashSet<>(), tree);
    }

    private ICNode<I> adjustStructure(Set<DefaultQuery<I, Boolean>> answers, Set<Word<I>> removed,
    ICNode<I> tree) {
        if (tree.isLeaf()) {
            tree.targets.removeAll(removed);
            if (tree.accepting != null) {
                for (DefaultQuery<I, Boolean> answer : answers) {
                    if (answer.getInput().equals(tree.accessSequence) && answer.getOutput().equals(!tree.accepting)) {
                        tree.accepting = !tree.accepting;
                        return tree;
                    }
                }
            }
            return tree;
        } else {
            Set<Word<I>> removeLeft = getTargets(tree.getChild(false)).stream()
                .filter(t -> {
                    for (DefaultQuery<I, Boolean> query : answers) {
                        if (query.getInput().equals(t.concat(tree.getDiscriminator())) &&
                            query.getOutput().equals(true)) {
                            return true;
                        }
                    }
                    return false;
                })
                .filter(t -> !removed.contains(t))
                .collect(Collectors.toSet());
            Set<Word<I>> removeRight = getTargets(tree.getChild(true)).stream()
                .filter(t -> {
                    for (DefaultQuery<I, Boolean> query : answers) {
                        if (query.getInput().equals(t.concat(tree.getDiscriminator())) &&
                            query.getOutput().equals(false)) {
                            return true;
                        }
                    }
                    return false;
                })
                .filter(t -> !removed.contains(t))
                .collect(Collectors.toSet());


            Set<Word<I>> adjustLeft = new HashSet<>(removed);
            adjustLeft.addAll(removeLeft);
            ICNode<I> leftPrime = adjustStructure(answers, adjustLeft, tree.getChild(false));

            Set<Word<I>> adjustRight = new HashSet<>(removed);
            adjustLeft.addAll(removeRight);
            ICNode<I> rightPrime = adjustStructure(answers, adjustRight, tree.getChild(true));

            leftPrime.targets.addAll(removeRight);
            rightPrime.targets.addAll(removeLeft);
            tree.targets.removeAll(removed);
            setChildren(tree, leftPrime, rightPrime);
            return tree;
        }
    }

    private Pair<ICNode<I>, Set<Word<I>>> adjustStates(Set<DefaultQuery<I, Boolean>> answers, ICNode<I> tree) {
        return adjustStates(answers, new HashSet<>(), tree);
    }

    private Pair<ICNode<I>, Set<Word<I>>> adjustStates(Set<DefaultQuery<I, Boolean>> answers, Set<Word<I>> removed,
                 ICNode<I> tree) {
        if (tree.isLeaf()) {
            if (removed.contains(tree.accessSequence)) {
                return Pair.of(null, new HashSet<>(tree.targets));
            }
            return Pair.of(tree, new HashSet<>());
        } else {
            Set<Word<I>> removeLeft = new HashSet<>();
            DiscriminationTreeIterators.leafIterator(tree.getChild(false)).forEachRemaining(l -> {
                for (DefaultQuery<I, Boolean> query : answers) {
                    if (query.getInput().equals(((ICNode<I>) l).accessSequence.concat(tree.getDiscriminator())) &&
                        query.getOutput().equals(true)) {
                        removeLeft.add(((ICNode<I>) l).accessSequence);
                    }
                }
            });
            HashSet<Word<I>> adjustLeft = new HashSet<>();
            adjustLeft.addAll(removeLeft);
            adjustLeft.addAll(removed);
            Pair<ICNode<I>, Set<Word<I>>>
                leftAdjustment = adjustStates(answers, adjustLeft, tree.getChild(false));
            ICNode<I> leftPrime = leftAdjustment.getFirst();
            Set<Word<I>> targetsLeft = leftAdjustment.getSecond();
            if (leftPrime == null) {
                Pair<ICNode<I>, Set<Word<I>>>
                    rightAdjustment = adjustStates(answers, removed, tree.getChild(true));
                ICNode<I> rightPrime = rightAdjustment.getFirst();
                Set<Word<I>> targetsRight = rightAdjustment.getSecond();

                if (rightPrime == null) {
                    HashSet<Word<I>> extraTargets = new HashSet<>();
                    extraTargets.addAll(tree.targets);
                    extraTargets.addAll(targetsLeft);
                    extraTargets.addAll(targetsRight);

                    return Pair.of(null, new HashSet<>(extraTargets));
                }

                rightPrime.targets.addAll(tree.targets);
                rightPrime.targets.addAll(targetsLeft);
                rightPrime.setParent(null);
                rightPrime.setParentOutcome(null);
                return Pair.of(rightPrime, new HashSet<>());
            }
            Set<Word<I>> removeRight = new HashSet<>();
            DiscriminationTreeIterators.leafIterator(tree.getChild(true)).forEachRemaining(l -> {
                for (DefaultQuery<I, Boolean> query : answers) {
                    if (query.getInput().equals(((ICNode<I>) l).accessSequence.concat(tree.getDiscriminator())) &&
                        query.getOutput().equals(false)) {
                        removeRight.add(((ICNode<I>) l).accessSequence);
                    }
                }
            });

            HashSet<Word<I>> adjustRight = new HashSet<>();
            adjustRight.addAll(removeRight);
            adjustRight.addAll(removed);
            Pair<ICNode<I>, Set<Word<I>>>
                rightAdjustment = adjustStates(answers, adjustRight, tree.getChild(true));
            ICNode<I> rightPrime = rightAdjustment.getFirst();
            Set<Word<I>> targetsRight = rightAdjustment.getSecond();

            if (rightPrime == null) {
                leftPrime.targets.addAll(tree.targets);
                leftPrime.targets.addAll(targetsRight);
                leftPrime.setParent(null);
                leftPrime.setParentOutcome(null);
                return Pair.of(leftPrime, new HashSet<>());
            }

            ICNode<I> node = new ICNode<>();
            node.targets.addAll(tree.targets);
            node.setDiscriminator(tree.getDiscriminator());
            setChildren(node, leftPrime, rightPrime);
            return Pair.of(node, new HashSet<>());
        }
    }

    private void setChildren(ICNode<I> parent, ICNode<I> lChild, ICNode<I> rChild) {
        lChild.setParentOutcome(false);
        rChild.setParentOutcome(true);

        lChild.setParent(parent);
        rChild.setParent(parent);

        HashMap<Boolean, AbstractWordBasedDTNode<I, Boolean, Object>>
            children = new HashMap<>();
        children.put(false, lChild);
        children.put(true, rChild);
        parent.replaceChildren(children);
    }

    private ICNode<I> restrictTargets(ICNode<I> tree, Set<Word<I>> origins) {
        ICNode<I> result = new ICNode<>();
        result.accessSequence = tree.accessSequence;
        result.accepting = tree.accepting;
        HashSet<Word<I>> oldTargets = new HashSet<>(tree.targets);
        result.targets.clear();
        for (Word<I> target : oldTargets) {
            if (!target.equals(Word.epsilon())) {
                if (origins.contains(target.prefix(target.length() - 1))) {
                    result.targets.add(target);
                }
            }
        }
        if (oldTargets.contains(Word.epsilon())) {
            result.targets.add(Word.epsilon());
        }

        if (!tree.isLeaf()) {
            result.setDiscriminator(tree.getDiscriminator());
            setChildren(result,
                restrictTargets(tree.getChild(false), origins),
                restrictTargets(tree.getChild(true), origins));
        }

        return result;
    }

    private boolean hypothesise(Boolean answer) {
        if (answer != null) {
            tree = advanceHypothesis(query, answer, tree);
        }
        Word<I> nextQuery = hypothesisQuery(tree);
        if (nextQuery == null) {
            return true;
        }
        query = nextQuery;
        activity = Activity.HYP;
        return false;
    }

    private ICNode<I>
    advanceHypothesis(Word<I> query, Boolean answer, ICNode<I> tree) {
        return advanceHypothesis(query, answer, new HashSet<>(), tree);
    }

    private ICNode<I> advanceHypothesis(Word<I> query, Boolean answer, Set<Word<I>> extraTargets,
    ICNode<I> tree) {
        ICNode<I> newNode = new ICNode<>();
        if (tree.isLeaf()) {
            newNode.accessSequence = tree.accessSequence;
            newNode.targets.addAll(tree.targets);
            newNode.targets.addAll(extraTargets);
            newNode.accepting = tree.accepting;
            if (tree.accessSequence.equals(query)) {
                newNode.accepting = answer;
            }
        } else {
            newNode.setDiscriminator(tree.getDiscriminator());
            newNode.setParent(null);
            newNode.setParentOutcome(null);

            Set<Word<I>> allTrans = new HashSet<>(tree.targets);
            allTrans.addAll(extraTargets);
            Set<Word<I>> moved = allTrans.stream()
                .filter(t -> t.concat(tree.getDiscriminator()).equals(query))
                .collect(Collectors.toSet());

            Set<Word<I>> remaining = allTrans.stream()
                .filter(t -> !moved.contains(t))
                .collect(Collectors.toSet());

            newNode.targets.clear();
            newNode.targets.addAll(remaining);
            if (moved.isEmpty()) {
                setChildren(newNode,
                    advanceHypothesis(query, answer, tree.getChild(false)),
                    advanceHypothesis(query, answer, tree.getChild(true)));
            } else if (answer) {
                setChildren(newNode,
                    advanceHypothesis(query, true, tree.getChild(false)),
                    advanceHypothesis(query, true, moved, tree.getChild(true)));
            } else {
                setChildren(newNode,
                    advanceHypothesis(query, false, moved, tree.getChild(false)),
                    advanceHypothesis(query, false, tree.getChild(true)));

            }
        }
        return newNode;
    }

    private Word<I> hypothesisQuery(ICNode<I> tree) {
        if (tree.isLeaf()) {
            if (tree.accepting == null) {
                return tree.accessSequence;
            }
            return null;
        } else {
            if (!tree.targets.isEmpty()) {
                Word<I> trans = tree.targets.iterator().next();
                return trans.concat(tree.getDiscriminator());
            }
            Word<I> result = hypothesisQuery(tree.getChild(false));
            if (result != null) {
                return result;
            }
            return hypothesisQuery(tree.getChild(true));
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

    private void testRandom() {
        query = sampleWord();
        activity = Activity.TEST;
        activityTEST = null;
    }

    private void test(Boolean answer) {
        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        if (activityTEST != null) {
            if (activityTEST.queue.isEmpty()) {
                finishCounterexample(activityTEST.swap, activityTEST.shrt, activityTEST.lng, activityTEST.post);
            } else {
                activity = Activity.TEST;
                query = activityTEST.queue.iterator().next();
                activityTEST.queue.remove(query);
            }
        } else {
            if (answer == null || hyp.computeOutput(query) == answer) {
                testRandom();
            } else {
                activity = Activity.INIT;
                activityINIT = query;
                query = hyp.getInitialState().concat(query);
            }
        }
    }
    private List<DefaultQuery<I, Boolean>> treePath(Word<I> find, ICNode<I> currentTree) {
        return treePath(find, currentTree, new LinkedList<>());
    }

    private List<DefaultQuery<I, Boolean>> treePath(Word<I> find, ICNode<I> currentTree, List<DefaultQuery<I, Boolean>> current) {
        if (currentTree.isLeaf()) {
            if (currentTree.accessSequence.equals(find)) {
                return current;
            }
            return new LinkedList<>();
        }
        List<DefaultQuery<I, Boolean>> leftCurrent = new LinkedList<>(current);
        leftCurrent.add(0, new DefaultQuery<>(currentTree.getDiscriminator(), false));
        List<DefaultQuery<I, Boolean>> left = treePath(find, currentTree.getChild(false), leftCurrent);

        List<DefaultQuery<I, Boolean>> rightCurrent = new LinkedList<>(current);
        rightCurrent.add(0, new DefaultQuery<>(currentTree.getDiscriminator(), true));
        List<DefaultQuery<I, Boolean>> right = treePath(find, currentTree.getChild(true), rightCurrent);

        List<DefaultQuery<I, Boolean>> result = new LinkedList<>(left);
        result.addAll(right);
        return result;
    }

    private void checkSplit(Word<I> shrt, Word<I> lng, Word<I> post, Boolean answer) {
        Set<Word<I>> queue = treePath(shrt, tree).stream()
            .map(Query::getInput)
            .map(shrt::concat)
            .collect(Collectors.toSet());
        activityTEST = new TestState(queue, shrt, lng, post, answer);
        test(answer);
    }

    private void initialiseCounterexample(Boolean answer, Word<I> cex) {
        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        if (hyp.computeOutput(cex) == answer) {
            checkSplit(hyp.getInitialState(), Word.epsilon(), cex, answer);
        } else {
            splitCounterexample(Word.epsilon(), cex, Word.epsilon());
        }
    }

    private void handleCounterexample(Boolean answer, BinarySearchState bsState) {
        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        if (bsState.u.size() == 1 && bsState.v.equals(Word.epsilon())) {
            checkSplit(Objects.requireNonNull(hyp.getState(bsState.pre.concat(bsState.u))),
                       Objects.requireNonNull(hyp.getState(bsState.pre)).concat(bsState.u),
                       bsState.post, answer);
        } else if (hyp.computeOutput(query) == answer) {
            splitCounterexample(bsState.pre, bsState.u, bsState.v.concat(bsState.post));
        } else {
            splitCounterexample(bsState.pre.concat(bsState.u), bsState.v, bsState.post);
        }
    }

    private void splitCounterexample(Word<I> pre, Word<I> middle, Word<I> post) {
        Word<I> u =  middle.prefix((middle.length() + 1) / 2);
        Word<I> v =  middle.suffix((middle.length()) / 2);

        assert u.concat(v).equals(middle) && ((u.length() - v.length()) == 0 || (u.length() - v.length()) == 1);
        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        activity = Activity.CEX;
        activityCEX = new BinarySearchState(pre, u, v, post);
        query = Objects.requireNonNull(hyp.getState(pre.concat(u))).concat(v).concat(post);
    }

    private Set<DefaultQuery<I, Boolean>> implications(ICNode<I> leaf, ICNode<I> tree) {
        return implications(leaf, new HashSet<>(), tree);
    }

    private Set<DefaultQuery<I, Boolean>> implications(ICNode<I> leaf,
    Set<DefaultQuery<I, Boolean>> current, ICNode<I> tree) {
        if (tree.isLeaf()) {
            if (tree.equals(leaf)) {
                return current.stream()
                    .map(q -> new DefaultQuery<>(tree.accessSequence.concat(q.getInput()), q.getOutput()))
                    .collect(Collectors.toSet());
            }
            return new HashSet<>();
        }

        Set<DefaultQuery<I, Boolean>> leftCur = new HashSet<>(current);
        leftCur.add(new DefaultQuery<>(tree.getDiscriminator(), false));
        Set<DefaultQuery<I, Boolean>> impl = new HashSet<>(implications(leaf, leftCur, tree.getChild(false)));

        Set<DefaultQuery<I, Boolean>> rightCur = new HashSet<>(current);
        rightCur.add(new DefaultQuery<>(tree.getDiscriminator(), true));
        impl.addAll(implications(leaf, rightCur, tree.getChild(true)));

        return impl;
    }

    private ICNode<I> replaceLeaf(Word<I> s, ICNode<I> node, ICNode<I> tree) {
        if (tree.isLeaf() && tree.accessSequence.equals(s)) {
            node.targets.addAll(tree.targets);
            return node;
        }
        if (!tree.isLeaf()) {
            setChildren(tree,
                replaceLeaf(s, node, tree.getChild(false)),
                replaceLeaf(s, node, tree.getChild(true)));
            return tree;
        }
        return tree;
    }

    private void finishCounterexample(Boolean swap, Word<I> shrt, Word<I> lng, Word<I> e) {
        ICNode<I> node = new ICNode<>();
        node.setDiscriminator(e);

        ICNode<I> longNode = new ICNode<>(lng);
        ICNode<I> shortNode = new ICNode<>(shrt);
        setChildren(node,
            swap ? longNode : shortNode,
            swap ? shortNode : longNode);

        AtomicBoolean duplicate = new AtomicBoolean(false);
        DiscriminationTreeIterators.leafIterator(tree).forEachRemaining(n -> {
            if (((ICNode<I>) n).accessSequence.equals(lng)) {
                duplicate.set(true);
            }
        });

        tree = replaceLeaf(shrt, node, tree);

        if (!duplicate.get()) {
            alphabet.forEach(a -> tree.targets.add(lng.append(a)));
        }

        Set<DefaultQuery<I, Boolean>> newImplications = implications(longNode, tree);
        newImplications.add(new DefaultQuery<>(shrt.concat(e), swap));
        applyAnswers(newImplications);
        if (hypothesise(null)) {
            testRandom();
        }
    }

    private Boolean derive(Word<I> word, ICNode<I> tree) {
        return derive(word, new HashSet<>(), tree);
    }

    private Boolean derive(Word<I> word, Set<DefaultQuery<I, Boolean>> values,
                           ICNode<I> tree) {
        if (tree.isLeaf()) {
            if (word.equals(tree.accessSequence) && tree.accepting != null) {
                return tree.accepting;
            } else {
                Set<DefaultQuery<I, Boolean>> queries = new HashSet<>();
                values.forEach(value -> tree.targets.forEach(t -> {
                    queries.add(new DefaultQuery<>(tree.accessSequence.concat(value.getInput()), value.getOutput()));
                    queries.add(new DefaultQuery<>(t.concat(value.getInput()), value.getOutput()));
                }));

                for (DefaultQuery<I, Boolean> q : queries) {
                    if (q.getInput().equals(word)) {
                        return q.getOutput();
                    }
                }
            }
        } else {
            Set<DefaultQuery<I, Boolean>> queries = new HashSet<>();
            values.forEach(value -> tree.targets.forEach(t -> {
                queries.add(new DefaultQuery<>(t.concat(value.getInput()), value.getOutput()));
            }));

            for (DefaultQuery<I, Boolean> q : queries) {
                if (q.getInput().equals(word)) {
                    return q.getOutput();
                }
            }

            HashSet<DefaultQuery<I, Boolean>> leftValues = new HashSet<>(values);
            leftValues.add(new DefaultQuery<>(tree.getDiscriminator(), false));
            Boolean answer = derive(word, leftValues, tree.getChild(false));
            if (answer != null) {
                return answer;
            }

            HashSet<DefaultQuery<I, Boolean>> rightValues = new HashSet<>(values);
            rightValues.add(new DefaultQuery<>(tree.getDiscriminator(), true));
            answer = derive(word, rightValues, tree.getChild(true));
            return answer;
        }

        return null;
    }

    public List<Pair<Integer, CompactDFA<I>>> learn(int limit, int sample) {
        List<Pair<Integer, CompactDFA<I>>> hyps = new LinkedList<>();
        Boolean answer = null;
        for (int i = 0; i < limit; i++) {
            Pair<ICHypothesisDFA<I>, Word<I>> pair = nextState(answer);
            ICHypothesisDFA<I> hyp = pair.getFirst();
            if (i % sample == 0) {
                hyps.add(Pair.of(i, DFAs.or(hyp, hyp, alphabet)));
            }
            Word<I> query = pair.getSecond();
            if (activity != Activity.TEST) {
                Boolean nextAnswer = derive(query, tree);
                if (nextAnswer != null) {
                    answer = nextAnswer;
                    i--;
                    continue;
                }
            }

            answer = oracle.answerQuery(query);
        }
        return hyps;
    }
}
