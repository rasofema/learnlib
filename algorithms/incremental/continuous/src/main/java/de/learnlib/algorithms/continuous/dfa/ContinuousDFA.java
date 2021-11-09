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
import net.automatalib.commons.util.Triple;
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
        if (!applyAnswers(Collections.singleton(new DefaultQuery<>(query, answer)))) {
            switch (activity) {
                case HYP:
                    hypothesise(answer);
                    break;
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

    private boolean applyAnswers(Set<DefaultQuery<I, Boolean>> answers) {
        Triple<Boolean, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
            treeAdjustment = adjustTree(answers, Collections.emptySet(), tree);
        boolean changed = treeAdjustment.getFirst();
        tree = treeAdjustment.getSecond();
        if (changed) {
            if (tree == null) {
                ICState<I, Boolean> startState = new ICState<>(Word.epsilon());
                this.alphabet.forEach(s -> startState.addIncoming(new ICTransition<>(startState, s)));
                tree = new BinaryDTNode<>(startState);
            } else {
                Set<ICState<I, Boolean>> origins = new HashSet<>();
                DiscriminationTreeIterators.leafIterator(tree).forEachRemaining(n -> origins.add(n.getData()));
                tree = restrictTargets(tree, origins);
            }
            activity = Activity.HYP;
            hypothesise(null);
        }
        return changed;
    }

    private Triple<Boolean, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
    adjustTree(Set<DefaultQuery<I, Boolean>> answers, Set<ICState<I, Boolean>> removed,
               AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            if (removed.contains(tree.getData())) {
                return Triple.of(true, null, new HashSet<>(tree.getData().incoming));
            }
            if (tree.getData().accepting != null) {
                for (DefaultQuery<I, Boolean> answer : answers) {
                    if (answer.getInput().equals(tree.getData().accessSequence) && answer.getOutput().equals(!tree.getData().accepting)) {
                        tree.getData().accepting = !tree.getData().accepting;
                        return Triple.of(true, tree, Collections.emptySet());
                    }
                }
            }
            return Triple.of(false, tree, Collections.emptySet());
        } else {
            AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> result = null;
            HashSet<ICTransition<I, Boolean>> extraTargets = new HashSet<>();
            Boolean changed;

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
            Triple<Boolean, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
                leftAdjustment = adjustTree(answers, adjustLeft, tree.getChild(false));
            Boolean changedLeft = leftAdjustment.getFirst();
            AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> leftPrime = leftAdjustment.getSecond();
            Set<ICTransition<I, Boolean>> incomingLeft = leftAdjustment.getThird();

            if (leftPrime == null) {
                Triple<Boolean, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
                    rightAdjustment = adjustTree(answers, removed, tree.getChild(true));
                changed = rightAdjustment.getFirst();
                AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> rightPrime = rightAdjustment.getSecond();
                Set<ICTransition<I, Boolean>> incomingRight = rightAdjustment.getThird();
                extraTargets.addAll(tree.getData().incoming);
                extraTargets.addAll(incomingLeft);
                extraTargets.addAll(incomingRight);
                if (rightPrime != null) {
                    rightPrime.getData().incoming.addAll(extraTargets);
                    result = rightPrime;
                    extraTargets.clear();
                }
            } else {
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
                Triple<Boolean, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>, Set<ICTransition<I, Boolean>>>
                    rightAdjustment = adjustTree(answers, adjustRight, tree.getChild(true));
                Boolean changedRight = rightAdjustment.getFirst();
                AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> rightPrime = rightAdjustment.getSecond();
                Set<ICTransition<I, Boolean>> incomingRight = rightAdjustment.getThird();
                changed = (changedLeft | changedRight);
                extraTargets.addAll(tree.getData().incoming);
                extraTargets.addAll(incomingLeft);
                extraTargets.addAll(incomingRight);

                if (rightPrime == null) {
                    leftPrime.getData().incoming.addAll(extraTargets);
                    result = leftPrime;
                } else {
                    result = new BinaryDTNode<>(new ICState<>());
                    for (ICTransition<I, Boolean> trans : extraTargets) {
                        result.getData().addIncoming(trans);
                    }
                    result.setDiscriminator(tree.getDiscriminator());
                    setChildren(result, leftPrime, rightPrime);
                }
                extraTargets.clear();
            }

            if (result != null) {
                result.setParent(null);
                result.setParentOutcome(null);
            }

            return Triple.of(changed, result, extraTargets);
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

    private void hypothesise(Boolean answer) {
        assert checkINIT();
        if (answer != null) {
            advanceHypothesis(query, answer, Collections.emptySet(), tree);
        }
        Word<I> nextQuery = hypothesisQuery(tree);
        if (nextQuery == null) {
            query = sampleWord();
            activity = Activity.TEST;
        } else {
            query = nextQuery;
        }
        assert checkINIT();
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
            newNode.setParent(tree.getParent());
            newNode.setParentOutcome(tree.getParentOutcome());
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
                    advanceHypothesis(query, answer, Collections.emptySet(), tree.getChild(false)),
                    advanceHypothesis(query, answer, Collections.emptySet(), tree.getChild(true)));
            } else if (answer) {
                setChildren(newNode,
                    advanceHypothesis(query, true, Collections.emptySet(), tree.getChild(false)),
                    advanceHypothesis(query, true, moved, tree.getChild(true)));
            } else {
                setChildren(newNode,
                    advanceHypothesis(query, false, moved, tree.getChild(false)),
                    advanceHypothesis(query, false, Collections.emptySet(), tree.getChild(true)));

            }
        }
        return newNode;
    }

    private Word<I> hypothesisQuery(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            if (tree.getData().accepting == null) {
                return tree.getData().accessSequence;
            } else {
                return null;
            }
        } else {
            if (!tree.getData().incoming.isEmpty()) {
                ICTransition<I, Boolean> trans = tree.getData().incoming.iterator().next();
                return trans.equals(INIT)
                    ? tree.getDiscriminator()
                    : trans.getStart().accessSequence.append(trans.getInput()).concat(tree.getDiscriminator());
            } else {
                Word<I> result = hypothesisQuery(tree.getChild(false));
                if (result != null) {
                    return result;
                }
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

    private void test(Boolean answer) {
        ICHypothesisDFA<I> hyp = extractHypothesis(Collections.emptySet(), tree);
        if (hyp.computeOutput(query) == answer) {
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

    private HashSet<DefaultQuery<I, Boolean>>
    treePath(Word<I> as, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        HashSet<DefaultQuery<I, Boolean>> decisions = new HashSet<>();

        Iterator<AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>> iter = DiscriminationTreeIterators.leafIterator(tree);
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> node = null;
        while (iter.hasNext()) {
            node = iter.next();
            if (node.getData().accessSequence.equals(as)) {
                break;
            }
        }

        assert node != null;
        while (!node.isRoot()) {
            decisions.add(new DefaultQuery<>(node.getParent().getDiscriminator(), node.getParentOutcome()));
            node = node.getParent();
        }

        return decisions;
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>
    cloneTreeWithData(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> newTree = new BinaryDTNode<>(tree);
        Set<ICState<I, Boolean>> oldStates = new HashSet<>();
        DiscriminationTreeIterators.nodeIterator(tree).forEachRemaining(n -> {
            if (n.getData() != null) {
                oldStates.add(n.getData());
                for (ICTransition<I, Boolean> trans : n.getData().incoming) {
                    if (trans.getStart() != null) {
                        oldStates.add(trans.getStart());
                    }
                }
            }
        });

        Map<ICState<I, Boolean>, ICState<I, Boolean>> stateMap = new HashMap<>();
        for (ICState<I, Boolean> state : oldStates) {
            stateMap.put(state, new ICState<>(state));
        }

        for (Map.Entry<ICState<I, Boolean>, ICState<I, Boolean>> entry : stateMap.entrySet()) {
            Set<ICTransition<I, Boolean>> oldTrans = new HashSet<>(entry.getKey().incoming);
            entry.getValue().clearIncoming();
            for (ICTransition<I, Boolean> trans : oldTrans) {
                if (trans.equals(INIT)) {
                    entry.getValue().addIncoming(INIT);
                } else {
                    assert stateMap.get(trans.getStart()) != null;
                    entry.getValue().addIncoming(new ICTransition<>(stateMap.get(trans.getStart()), trans.getInput()));
                }
            }
        }

        DiscriminationTreeIterators.nodeIterator(newTree).forEachRemaining(n -> {
            if (n.getData() != null) {
                n.setData(stateMap.get(n.getData()));
            }
        });

        return newTree;
    }

    private void finishCounterexample(Boolean swap, Word<I> shrt, Word<I> lng, Word<I> e) {
        assert checkINIT();
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> oldTree = cloneTreeWithData(tree);
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

        activity = Activity.HYP;
        Set<DefaultQuery<I, Boolean>> newImplications = treePath(shrt, tree).stream()
            .map(q -> new DefaultQuery<>(shrt.concat(q.getInput()), q.getOutput()))
            .collect(Collectors.toSet());
        newImplications.addAll(treePath(lng, tree).stream()
            .map(q -> new DefaultQuery<>(lng.concat(q.getInput()), q.getOutput()))
            .collect(Collectors.toSet()));

        if (applyAnswers(newImplications)) {
            tree = oldTree;
            if (!applyAnswers(newImplications)) {
                query = sampleWord();
                activity = Activity.TEST;
                assert checkINIT();
                return;
            }
        }
        hypothesise(null);
        assert checkINIT();
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
