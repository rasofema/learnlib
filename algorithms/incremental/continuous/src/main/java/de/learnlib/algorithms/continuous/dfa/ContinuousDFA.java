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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithms.continuous.base.ICState;
import de.learnlib.algorithms.continuous.base.ICTransition;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.discriminationtree.BinaryDTNode;
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

    private final Alphabet<I> alphabet;
    private final double alpha;
    private final MembershipOracle<I, Boolean> oracle;
    private Activity activity;
    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree;
    private Word<I> query;

    private Word<I> activityINIT;
    private BinarySearchState activityCEX;


    private final ICTransition<I, Boolean> INIT = new ICTransition<>(null, null);


    private boolean checkINIT() {
        HashSet<ICState<I, Boolean>> checkStates = new HashSet<>();
        DiscriminationTreeIterators.nodeIterator(tree).forEachRemaining(t -> {
            if (t.getData() != null) {
                checkStates.add(t.getData());
            }
        });
        return checkStates.stream()
            .filter(s -> s.incoming.contains(INIT))
            .count() == 1;
    }
    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet
     * @param oracle
     *         the membership oracle
     */
    @GenerateBuilder
    public ContinuousDFA(Alphabet<I> alphabet, double alpha,
                              MembershipOracle<I, Boolean> oracle) {
        // TODO: State.
        this.alphabet = alphabet;
        this.alpha = alpha;
        this.oracle = oracle;
        this.activity = Activity.HYP;

        ICState<I, Boolean> startState = new ICState<>(Word.epsilon());
        startState.accepting = null;
        this.alphabet.forEach(s -> startState.addIncoming(new ICTransition<>(startState, s)));
        startState.addIncoming(INIT);
        this.tree = new BinaryDTNode<>(startState);

        this.query = Word.epsilon();
    }

    private Pair<ICHypothesisDFA<I>, Word<I>> nextState(Boolean answer) {
        applyAnswers(Collections.singleton(new DefaultQuery<>(query, answer)));
        if (hypothesise(answer) && activity == Activity.HYP) {
            test();
        } else {
            switch (activity) {
                case TEST:
                    test(answer);
                    break;
                case INIT:
                    initialiseCounterexample(answer, activityINIT);
                    break;
                case CEX:
                    handleCounterexample(answer, activityCEX);
                    break;
            }
        }

        if (activity != Activity.TEST) {
            // TODO: this assignment is reversed in pseudocode.
            Boolean nextAnswer = derive(query, Collections.emptySet(), tree);
            if (nextAnswer != null) {
                return nextState(nextAnswer);
            }
        }

        ICHypothesisDFA<I> hyp = extractHypothesis(Collections.emptySet(), tree);
        return Pair.of(hyp, query);
    }


    private ICHypothesisDFA<I> extractHypothesis(Set<ICTransition<I, Boolean>> extraTargets, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> localTree) {
        if (localTree.isLeaf()) {
            ICState<I, Boolean> initial = null;
            if (localTree.getData().incoming.contains(INIT) || extraTargets.contains(INIT)) {
                initial = localTree.getData();
            }

            if (localTree.getData().accepting == null) {
                localTree.getData().accepting = false;
            }

            ICHypothesisDFA<I> hyp = new ICHypothesisDFA<>(alphabet);
            hyp.setInitial(initial);

            for (ICTransition<I, Boolean> trans : localTree.getData().incoming) {
                if (!trans.equals(INIT)) {
                    hyp.addTransition(trans.getStart(), trans.getInput(), localTree.getData());
                }
            }

            for (ICTransition<I, Boolean> trans : extraTargets) {
                if (!trans.equals(INIT)) {
                    hyp.addTransition(trans.getStart(), trans.getInput(), localTree.getData());
                }
            }

            hyp.setAccepting(localTree.getData(), localTree.getData().accepting);

            return hyp;
        } else {
            Set<ICTransition<I, Boolean>> leftTargets = new HashSet<>();
            leftTargets.addAll(extraTargets);
            leftTargets.addAll(localTree.getData().incoming);

            ICHypothesisDFA<I> leftHyp = extractHypothesis(leftTargets, localTree.getChild(false));
            ICHypothesisDFA<I> rightHyp = extractHypothesis(Collections.emptySet(), localTree.getChild(true));

            ICHypothesisDFA<I> hyp = new ICHypothesisDFA<>(alphabet);
            ICState<I, Boolean> initial = leftHyp.getInitialState() != null
                ? leftHyp.getInitialState()
                : rightHyp.getInitialState();
            hyp.setInitial(initial);

            Set<Map.Entry<Pair<ICState<I, Boolean>, I>, ICState<I, Boolean>>> unionTrans = new HashSet<>(leftHyp.transitions.entrySet());
            unionTrans.addAll(rightHyp.transitions.entrySet());
            for (Map.Entry<Pair<ICState<I, Boolean>, I>, ICState<I, Boolean>> trans : unionTrans) {
                hyp.addTransition(trans.getKey().getFirst(), trans.getKey().getSecond(), trans.getValue());
            }

            Set<Map.Entry<ICState<I, Boolean>, Boolean>> unionAcc = new HashSet<>(leftHyp.acceptance.entrySet());
            unionAcc.addAll(rightHyp.acceptance.entrySet());
            for (Map.Entry<ICState<I, Boolean>, Boolean> acc : unionAcc) {
                hyp.setAccepting(acc.getKey(), acc.getValue());
            }

            return hyp;
        }
    }

    private void applyAnswers(Set<DefaultQuery<I, Boolean>> answers) {
        assert checkINIT();
        Pair<AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
            stateAdjustment = adjustStates(answers, tree);
        tree = stateAdjustment.getFirst();
        if (tree == null) {
            ICState<I, Boolean> startState = new ICState<>(Word.epsilon());
            this.alphabet.forEach(s -> startState.addIncoming(new ICTransition<>(startState, s)));
            startState.addIncoming(INIT);
            tree = new BinaryDTNode<>(startState);
        } else {
            Set<ICState<I, Boolean>> origins = new HashSet<>();
            DiscriminationTreeIterators.leafIterator(tree).forEachRemaining(n -> origins.add(n.getData()));
            tree = restrictTargets(tree, origins);
        }
        tree = adjustStructure(answers, tree);
        assert checkINIT();
    }

    private Set<ICTransition<I, Boolean>> getTargets(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        HashSet<ICTransition<I, Boolean>> targets = new HashSet<>();
        DiscriminationTreeIterators.nodeIterator(tree).forEachRemaining(n -> {
            if (n.getData() != null) {
                targets.addAll(n.getData().incoming);
            }
        });
        return targets;
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>
    adjustStructure(Set<DefaultQuery<I, Boolean>> answers, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        return adjustStructure(answers, Collections.emptySet(), tree);
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>
    adjustStructure(Set<DefaultQuery<I, Boolean>> answers, Set<ICTransition<I, Boolean>> removed,
                    AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            tree.getData().incoming.removeAll(removed);
            if (tree.getData().accepting != null) {
                for (DefaultQuery<I, Boolean> answer : answers) {
                    if (answer.getInput().equals(tree.getData().accessSequence) && answer.getOutput().equals(!tree.getData().accepting)) {
                        tree.getData().accepting = !tree.getData().accepting;
                        return tree;
                    }
                }
            }
            return tree;
        } else {
            Set<ICTransition<I, Boolean>> removeLeft = getTargets(tree.getChild(false)).stream()
                .filter(t -> {
                    Word<I> asi = t.equals(INIT)
                        ? Word.epsilon()
                        : t.getStart().accessSequence.append(t.getInput());

                    for (DefaultQuery<I, Boolean> query : answers) {
                        if (query.getInput().equals(asi.concat(tree.getDiscriminator())) &&
                            query.getOutput().equals(true)) {
                            return true;
                        }
                    }
                    return false;
                })
                .filter(t -> !removed.contains(t))
                .collect(Collectors.toSet());
            Set<ICTransition<I, Boolean>> removeRight = getTargets(tree.getChild(true)).stream()
                .filter(t -> {
                    Word<I> asi = t.equals(INIT)
                        ? Word.epsilon()
                        : t.getStart().accessSequence.append(t.getInput());

                    for (DefaultQuery<I, Boolean> query : answers) {
                        if (query.getInput().equals(asi.concat(tree.getDiscriminator())) &&
                            query.getOutput().equals(false)) {
                            return true;
                        }
                    }
                    return false;
                })
                .filter(t -> !removed.contains(t))
                .collect(Collectors.toSet());


            Set<ICTransition<I, Boolean>> adjustLeft = new HashSet<>(removed);
            adjustLeft.addAll(removeLeft);
            AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> leftPrime = adjustStructure(answers, adjustLeft, tree.getChild(false));

            Set<ICTransition<I, Boolean>> adjustRight = new HashSet<>(removed);
            adjustLeft.addAll(removeRight);
            AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> rightPrime = adjustStructure(answers, adjustRight, tree.getChild(true));

            leftPrime.getData().incoming.addAll(removeRight);
            rightPrime.getData().incoming.addAll(removeLeft);
            tree.getData().incoming.removeAll(removed);
            setChildren(tree, leftPrime, rightPrime);
            return tree;
        }
    }

    private Pair<AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
    adjustStates(Set<DefaultQuery<I, Boolean>> answers, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        return adjustStates(answers, Collections.emptySet(), tree);
    }

    private Pair<AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
    adjustStates(Set<DefaultQuery<I, Boolean>> answers, Set<ICState<I, Boolean>> removed,
                 AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            if (removed.contains(tree.getData())) {
                return Pair.of(null, new HashSet<>(tree.getData().incoming));
            }
            return Pair.of(tree, Collections.emptySet());
        } else {
            Set<ICState<I, Boolean>> removeLeft = new HashSet<>();
            DiscriminationTreeIterators.leafIterator(tree.getChild(false)).forEachRemaining(l -> {
                for (DefaultQuery<I, Boolean> query : answers) {
                    if (query.getInput().equals(l.getData().accessSequence.concat(tree.getDiscriminator())) &&
                        query.getOutput().equals(true)) {
                        removeLeft.add(l.getData());
                    }
                }
            });
            HashSet<ICState<I, Boolean>> adjustLeft = new HashSet<>();
            adjustLeft.addAll(removeLeft);
            adjustLeft.addAll(removed);
            Pair<AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
                leftAdjustment = adjustStates(answers, adjustLeft, tree.getChild(false));
            AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> leftPrime = leftAdjustment.getFirst();
            Set<ICTransition<I, Boolean>> targetsLeft = leftAdjustment.getSecond();
            if (leftPrime == null) {
                Pair<AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
                    rightAdjustment = adjustStates(answers, removed, tree.getChild(true));
                AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> rightPrime = rightAdjustment.getFirst();
                Set<ICTransition<I, Boolean>> targetsRight = rightAdjustment.getSecond();

                if (rightPrime == null) {
                    HashSet<ICTransition<I, Boolean>> extraTargets = new HashSet<>();
                    if (tree.getData() != null) {
                        extraTargets.addAll(tree.getData().incoming);
                    }
                    extraTargets.addAll(targetsLeft);
                    extraTargets.addAll(targetsRight);

                    return Pair.of(null, new HashSet<>(extraTargets));
                }

                if (tree.getData() != null) {
                    rightPrime.getData().incoming.addAll(tree.getData().incoming);
                }
                rightPrime.getData().incoming.addAll(targetsLeft);
                rightPrime.setParent(null);
                rightPrime.setParentOutcome(null);
                return Pair.of(rightPrime, Collections.emptySet());
            }
            Set<ICState<I, Boolean>> removeRight = new HashSet<>();
            DiscriminationTreeIterators.leafIterator(tree.getChild(true)).forEachRemaining(l -> {
                for (DefaultQuery<I, Boolean> query : answers) {
                    if (query.getInput().equals(l.getData().accessSequence.concat(tree.getDiscriminator())) &&
                        query.getOutput().equals(false)) {
                        removeRight.add(l.getData());
                    }
                }
            });
            HashSet<ICState<I, Boolean>> adjustRight = new HashSet<>();
            adjustRight.addAll(removeRight);
            adjustRight.addAll(removed);
            Pair<AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
                rightAdjustment = adjustStates(answers, adjustRight, tree.getChild(true));
            AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> rightPrime = rightAdjustment.getFirst();
            Set<ICTransition<I, Boolean>> targetsRight = rightAdjustment.getSecond();

            if (rightPrime == null) {
                if (tree.getData() != null) {
                    leftPrime.getData().incoming.addAll(tree.getData().incoming);
                }
                leftPrime.getData().incoming.addAll(targetsRight);
                leftPrime.setParent(null);
                leftPrime.setParentOutcome(null);
                return Pair.of(leftPrime, Collections.emptySet());
            }

            AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> node = new BinaryDTNode<>(new ICState<>());
            if (tree.getData() != null) {
                node.getData().incoming.addAll(tree.getData().incoming);
            }
            node.setDiscriminator(tree.getDiscriminator());
            setChildren(node, leftPrime, rightPrime);
            return Pair.of(node, Collections.emptySet());
        }
    }

    private void setChildren(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> parent,
                             AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> lChild,
                             AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> rChild) {
        lChild.setParentOutcome(false);
        rChild.setParentOutcome(true);

        lChild.setParent(parent);
        rChild.setParent(parent);

        HashMap<Boolean, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>>
            children = new HashMap<>();
        children.put(false, lChild);
        children.put(true, rChild);
        parent.replaceChildren(children);
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>
    restrictTargets(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree, Set<ICState<I, Boolean>> origins) {
        assert checkINIT();
        BinaryDTNode<I, ICState<I, Boolean>> result = new BinaryDTNode<>(tree.getData());
        HashSet<ICTransition<I, Boolean>> oldIncoming = new HashSet<>(tree.getData().incoming);
        result.getData().clearIncoming();
        for (ICTransition<I, Boolean> incoming : oldIncoming) {
            if (origins.contains(incoming.getStart())) {
                result.getData().addIncoming(incoming);
            }
        }
        if (oldIncoming.contains(INIT)) {
            result.getData().addIncoming(INIT);
        }

        if (!tree.isLeaf()) {
            result.setDiscriminator(tree.getDiscriminator());
            setChildren(result,
                restrictTargets(tree.getChild(false), origins),
                restrictTargets(tree.getChild(true), origins));
        }
        assert checkINIT();
        return result;
    }

    private boolean hypothesise(Boolean answer) {
        assert checkINIT();
        if (answer != null) {
            tree = advanceHypothesis(query, answer, tree);
        }
        Word<I> nextQuery = hypothesisQuery(tree);
        if (nextQuery == null) {
            return true;
        }
        query = nextQuery;
        activity = Activity.HYP;
        assert checkINIT();
        return false;
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>
    advanceHypothesis(Word<I> query, Boolean answer, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        return advanceHypothesis(query, answer, Collections.emptySet(), tree);
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>
    advanceHypothesis(Word<I> query, Boolean answer, Set<ICTransition<I, Boolean>> extraTargets,
                      AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> newNode = new BinaryDTNode<>(tree.getData());
        if (tree.isLeaf()) {
            if (tree.getData().accessSequence.equals(query)) {
                newNode.getData().accepting = answer;
            }
            extraTargets.forEach(t -> newNode.getData().addIncoming(t));
        } else {
            newNode.setDiscriminator(tree.getDiscriminator());
            newNode.setParent(null);
            newNode.setParentOutcome(null);
            newNode.setDepth(tree.getDepth());

            Set<ICTransition<I, Boolean>> allTrans = new HashSet<>(tree.getData().incoming);
            allTrans.addAll(extraTargets);
            Set<ICTransition<I, Boolean>> moved = allTrans.stream()
                .filter(t -> !t.equals(INIT))
                .filter(t -> t.getStart().accessSequence.append(t.getInput()).concat(tree.getDiscriminator()).equals(query))
                .collect(Collectors.toSet());
            if (tree.getDiscriminator().equals(query) && allTrans.contains(INIT)) {
                moved.add(INIT);
            }

            Set<ICTransition<I, Boolean>> remaining = allTrans.stream()
                .filter(t -> !moved.contains(t))
                .collect(Collectors.toSet());

            newNode.getData().clearIncoming();
            remaining.forEach(t -> newNode.getData().addIncoming(t));
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

    private Word<I> hypothesisQuery(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            if (tree.getData().accepting == null) {
                return tree.getData().accessSequence;
            }
            return null;
        } else {
            if (!tree.getData().incoming.isEmpty()) {
                ICTransition<I, Boolean> trans = tree.getData().incoming.iterator().next();
                return trans.equals(INIT)
                    ? tree.getDiscriminator()
                    : trans.getStart().accessSequence.append(trans.getInput()).concat(tree.getDiscriminator());
            }
            Word<I> result = hypothesisQuery(tree.getChild(false));
            if (result != null) {
                return result;
            }
            return hypothesisQuery(tree.getChild(true));
        }
    }

    private Word<I> sampleWord() {
        if ((new Random()).nextFloat() < alpha) {
            List<I> alphas = new LinkedList<>(alphabet);
            Collections.shuffle(alphas);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    private void test() {
        test(null);
    }

    private void test(Boolean answer) {
        ICHypothesisDFA<I> hyp = extractHypothesis(Collections.emptySet(), tree);
        if (answer == null || hyp.computeOutput(query) == answer) {
            query = sampleWord();
            activity = Activity.TEST;
        } else {
            activity = Activity.INIT;
            activityINIT = query;
            query = hyp.getInitialState().accessSequence.concat(query);
        }
    }

    private void initialiseCounterexample(Boolean answer, Word<I> cex) {
        ICHypothesisDFA<I> hyp = extractHypothesis(Collections.emptySet(), tree);
        if (hyp.computeOutput(cex) == answer) {
            finishCounterexample(answer, hyp.getInitialState().accessSequence, Word.epsilon(), cex);
        } else {
            splitCounterexample(Word.epsilon(), cex, Word.epsilon());
        }
    }

    private void handleCounterexample(Boolean answer, BinarySearchState bsState) {
        ICHypothesisDFA<I> hyp = extractHypothesis(Collections.emptySet(), tree);
        if (bsState.u.size() == 1 && bsState.v.equals(Word.epsilon())) {
            finishCounterexample(answer,
                Objects.requireNonNull(hyp.getState(bsState.pre.concat(bsState.u))).accessSequence,
                Objects.requireNonNull(hyp.getState(bsState.pre)).accessSequence.concat(bsState.u),
                bsState.post);
        } else if (hyp.computeOutput(query) == answer) {
            splitCounterexample(bsState.pre, bsState.u, bsState.v.concat(bsState.post));
        } else {
            splitCounterexample(bsState.pre.concat(bsState.u), bsState.v, bsState.post);
        }
    }

    private void splitCounterexample(Word<I> pre, Word<I> middle, Word<I> post) {
        // TODO: Check if this is correct. maybe off by 1.
        Word<I> u =  middle.prefix((middle.length() + 1) / 2);
        Word<I> v =  middle.suffix((middle.length()) / 2);

        assert u.concat(v).equals(middle) && ((u.length() - v.length()) == 0 || (u.length() - v.length()) == 1);
        ICHypothesisDFA<I> hyp = extractHypothesis(Collections.emptySet(), tree);
        activity = Activity.CEX;
        activityCEX = new BinarySearchState(pre, u, v, post);
        query = Objects.requireNonNull(hyp.getState(pre.concat(u))).accessSequence.concat(v).concat(post);
    }

    private Set<DefaultQuery<I, Boolean>> implications(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> leaf,
    AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        return implications(leaf, Collections.emptySet(), tree);
    }

    private Set<DefaultQuery<I, Boolean>> implications(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> leaf,
    Set<DefaultQuery<I, Boolean>> current, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            if (tree.equals(leaf)) {
                return current.stream()
                    .map(q -> new DefaultQuery<>(tree.getData().accessSequence.concat(q.getInput()), q.getOutput()))
                    .collect(Collectors.toSet());
            }
            return Collections.emptySet();
        }

        Set<DefaultQuery<I, Boolean>> leftCur = new HashSet<>(current);
        leftCur.add(new DefaultQuery<>(tree.getDiscriminator(), false));
        Set<DefaultQuery<I, Boolean>> impl = new HashSet<>(implications(leaf, leftCur, tree.getChild(false)));

        Set<DefaultQuery<I, Boolean>> rightCur = new HashSet<>(current);
        rightCur.add(new DefaultQuery<>(tree.getDiscriminator(), true));
        impl.addAll(implications(leaf, rightCur, tree.getChild(true)));

        return impl;
    }

    private void finishCounterexample(Boolean swap, Word<I> shrt, Word<I> lng, Word<I> e) {
        assert checkINIT();
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> longNode = new BinaryDTNode<>(new ICState<>(lng));
        AtomicReference<AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>> shortNode = new AtomicReference<>();
        DiscriminationTreeIterators.leafIterator(tree).forEachRemaining(n -> {
            if (n.getData().accessSequence.equals(shrt)) {
                shortNode.set(n);
            }
        });
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> newShort = new BinaryDTNode<>(shortNode.get());
        shortNode.get().setData(new ICState<>());
        shortNode.get().setDiscriminator(e);
        setChildren(shortNode.get(),
            swap ? longNode : newShort,
            swap ? newShort : longNode);

        shortNode.get().getData().incoming.addAll(newShort.getData().incoming);
        newShort.getData().incoming.clear();

        assert longNode.getData() != null;
        alphabet.forEach(a -> tree.getData().incoming.add(new ICTransition<>(longNode.getData(), a)));

        Set<DefaultQuery<I, Boolean>> newImplications = implications(longNode, tree);
        newImplications.add(new DefaultQuery<>(shrt.concat(e), swap));
        applyAnswers(newImplications);

        if (hypothesise(null)) {
            test();
        }
        assert checkINIT();
    }

    private Boolean derive(Word<I> word, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        return derive(word, Collections.emptySet(), tree);
    }

    private Boolean derive(Word<I> word, Set<DefaultQuery<I, Boolean>> values,
                           AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            if (word.equals(tree.getData().accessSequence) && tree.getData().accepting != null) {
                return tree.getData().accepting;
            } else {
                Set<DefaultQuery<I, Boolean>> queries = new HashSet<>();
                values.forEach(value -> tree.getData().incoming.forEach(t -> {
                    queries.add(new DefaultQuery<>(value.getInput(), value.getOutput()));
                    if (!t.equals(INIT)) {
                        queries.add(new DefaultQuery<>(t.getStart().accessSequence.append(t.getInput()).concat(value.getInput()), value.getOutput()));
                    } else {
                        queries.add(new DefaultQuery<>(value.getInput(), value.getOutput()));
                    }
                }));

                for (DefaultQuery<I, Boolean> q : queries) {
                    if (q.getInput().equals(word)) {
                        return q.getOutput();
                    }
                }
            }
        } else {
            Set<DefaultQuery<I, Boolean>> queries = new HashSet<>();
            values.forEach(value -> tree.getData().incoming.forEach(t -> {
                if (!t.equals(INIT)) {
                    queries.add(new DefaultQuery<>(t.getStart().accessSequence.append(t.getInput()).concat(value.getInput()), value.getOutput()));
                } else {
                    queries.add(new DefaultQuery<>(value.getInput(), value.getOutput()));
                }
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

    public List<CompactDFA<I>> learn(int counter) {
        List<CompactDFA<I>> hyps = new LinkedList<>();
        Boolean answer = null;
        for (int i = 0; i < counter; i++) {
            Pair<ICHypothesisDFA<I>, Word<I>> pair = nextState(answer);
            ICHypothesisDFA<I> hyp = pair.getFirst();
            hyps.add(DFAs.or(hyp, hyp, alphabet));
            Word<I> query = pair.getSecond();
            answer = oracle.answerQuery(query);

        }
        return hyps;
    }
}
