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

import java.util.Arrays;
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
import java.util.stream.Collectors;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.continuous.base.ICState;
import de.learnlib.algorithms.continuous.base.ICTransition;
import de.learnlib.algorithms.kv.StateInfo;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.discriminationtree.BinaryDTNode;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.commons.util.Triple;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

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

    private Alphabet<I> alphabet;
    private final float alpha;
    private MembershipOracle<I, Boolean> oracle;
    private Activity activity;
    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree;
    private Word<I> query;

    private Word<I> activityINIT;
    private BinarySearchState activityCEX;


    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet
     * @param oracle
     *         the membership oracle
     */
    @GenerateBuilder
    public ContinuousDFA(Alphabet<I> alphabet, float alpha,
                              MembershipOracle<I, Boolean> oracle) {
        // TODO: State.
        this.alphabet = alphabet;
        this.alpha = alpha;
        this.oracle = oracle;
        this.activity = Activity.HYP;

        ICState<I, Boolean> startState = new ICState<>(Word.epsilon());
        startState.accepting = null;
        this.alphabet.forEach(s -> startState.addIncoming(new ICTransition<>(startState, s)));
        this.tree = new BinaryDTNode<>(startState);


        this.query = Word.epsilon();
    }

    private DFA<?, I> nextResult(Boolean answer) {
        if (!applyAnswers(Collections.singleton(new DefaultQuery<I, Boolean>(query, answer)))) {
            switch (activity) {
                case HYP:
                    hypothesise(answer);
                case TEST:
                    test(answer);
                case INIT:
                    initialiseCounterexample(answer, activityINIT);
                case CEX:
                    handleCounterexample(answer, activityCEX);
            }
        }

        if (activity != Activity.TEST) {
            // TODO: this assignment is reversed in pseudocode.
            Boolean nextAnswer = derive(query, Collections.emptySet(), tree);
            if (nextAnswer != null) {
                return nextResult(nextAnswer);
            }
        }

        // TOOD: Do we want to return query here too?
        return extractHypothesis(Collections.emptySet(), tree);
    }


    private ICHypothesisDFA<I> extractHypothesis(Set<ICState<I, Boolean>> extraTargets, AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> localTree) {
        if (localTree.isLeaf()) {
            ICState<I, Boolean> initial = null;
            if (localTree.getData().accessSequence.getClass().equals(Word.epsilon().getClass())) {
                initial = localTree.getData();
            }

            if (localTree.getData().accepting == null) {
                localTree.getData().accepting = false;
            }

            ICHypothesisDFA<I> hyp = new ICHypothesisDFA<I>(alphabet);
            // TODO: Check if this is equiv to (s, out) \in F
            hyp.setInitial(initial, localTree.getData().accepting);

            for (ICTransition<I, Boolean> trans : localTree.getData().incoming) {
                hyp.addTransition(trans.getStart(), trans.getInput(), localTree.getData());
            }

            return hyp;
        } else {
            Set<ICState<I, Boolean>> leftTargets = new HashSet<>();
            leftTargets.addAll(extraTargets);
            leftTargets.addAll(localTree.getData().incoming.stream()
                .map(ICTransition::getStart).collect(Collectors.toSet()));

            ICHypothesisDFA<I> leftHyp = extractHypothesis(leftTargets, localTree.getChild(false));
            ICHypothesisDFA<I> rightHyp = extractHypothesis(Collections.emptySet(), localTree.getChild(true));

            ICHypothesisDFA<I> hyp = new ICHypothesisDFA<>(alphabet);
            ICState<I, Boolean> initial = leftHyp.getInitialState() != null
                ? leftHyp.getInitialState()
                : rightHyp.getInitialState();
            hyp.setInitial(initial, false);

            HashSet<ICState<I, Boolean>> states = new HashSet<>(leftHyp.getStates());
            states.addAll(rightHyp.getStates());
            for (ICState<I, Boolean> state : states) {
                if (state.accepting) {
                    hyp.setAccepting(state, true);
                }

                for (ICTransition<I, Boolean> trans : state.incoming) {
                    hyp.addTransition(trans.getStart(), trans.getInput(), state);
                }
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
                activity = Activity.HYP;
                hypothesise(null);
            }
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
            return Triple.of(false, tree, Collections.emptySet());
        } else {
            AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> result = null;
            HashSet<ICTransition<I, Boolean>> extraTargets = new HashSet<>();
            Boolean changed = null;

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
                    result = addTargets(rightPrime, extraTargets);
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
                    result = addTargets(leftPrime, extraTargets);
                } else {
                    result = new BinaryDTNode<>(tree.getData());
                    result.setDiscriminator(tree.getDiscriminator());
                    setChildren(result, leftPrime, rightPrime);
                }
                extraTargets.clear();
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
        BinaryDTNode<I, ICState<I, Boolean>> result = new BinaryDTNode<>(tree.getData());
        HashSet<ICTransition<I, Boolean>> oldIncoming = new HashSet<>(tree.getData().incoming);
        result.getData().clearIncoming();
        for (ICTransition<I, Boolean> incoming : oldIncoming) {
            if (origins.contains(incoming.getStart())) {
                result.getData().addIncoming(incoming);
            }
        }
        if (!tree.isLeaf()) {
            result.setDiscriminator(tree.getDiscriminator());
            setChildren(result, restrictTargets(tree.getChild(false), origins), restrictTargets(tree.getChild(true), origins));
        }
        return result;
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>
    addTargets(AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree, Set<ICTransition<I, Boolean>> extraTargets) {
        tree.getData().incoming.addAll(extraTargets);
        return tree;
    }

    private void hypothesise(Boolean answer) {
        if (answer != null) {
            tree = advanceHypothesis(query, answer, Collections.emptySet(), tree);
        }
        Word<I> nextQuery = hypothesisQuery(tree);
        if (nextQuery == null) {
            query = sampleWord();
            activity = Activity.TEST;
        } else {
            query = nextQuery;
        }
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>>
    advanceHypothesis(Word<I> query, Boolean answer, Set<ICTransition<I, Boolean>> extraTargets,
                      AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            if (tree.getData().accessSequence.equals(query)) {
                extraTargets.forEach(t -> tree.getData().addIncoming(t));
            }
            tree.getData().accepting = answer;
        } else {
            Set<ICTransition<I, Boolean>> allTrans = new HashSet<>(tree.getData().incoming);
            allTrans.addAll(extraTargets);
            Set<ICTransition<I, Boolean>> moved = allTrans.stream()
                .filter(t -> t.getStart().accessSequence.concat(tree.getDiscriminator()).equals(query))
                .collect(Collectors.toSet());
            Set<ICTransition<I, Boolean>> remaining = allTrans.stream()
                .filter(t -> !moved.contains(t))
                .collect(Collectors.toSet());

            tree.getData().incoming.clear();
            remaining.forEach(t -> tree.getData().addIncoming(t));
            if (moved.isEmpty()) {
                setChildren(tree,
                    advanceHypothesis(query, answer, Collections.emptySet(), tree.getChild(false)),
                    advanceHypothesis(query, answer, Collections.emptySet(), tree.getChild(true)));
            } else if (answer) {
                setChildren(tree,
                    advanceHypothesis(query, answer, Collections.emptySet(), tree.getChild(false)),
                    advanceHypothesis(query, answer, moved, tree.getChild(true)));
            } else {
                setChildren(tree,
                    advanceHypothesis(query, answer, moved, tree.getChild(false)),
                    advanceHypothesis(query, answer, Collections.emptySet(), tree.getChild(true)));

            }
        }
        return tree;
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
                return trans.getStart().accessSequence.concat(tree.getDiscriminator());
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
        Word<I> v =  middle.suffix(((middle.length() + 1) / 2) + 1);

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

    private void finishCounterexample(Boolean swap, Word<I> shrt, Word<I> lng, Word<I> e) {
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> shortNode = new BinaryDTNode<>(new ICState<>(shrt));
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> longNode = new BinaryDTNode<>(new ICState<>(lng));
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> node = new BinaryDTNode<>(null);
        node.setDiscriminator(e);
        setChildren(node,
            swap ? longNode : shortNode,
            swap ? shortNode : longNode);

        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> oldTree = new BinaryDTNode<>(tree.getData());
        oldTree.setDiscriminator(tree.getDiscriminator());
        oldTree.setParent(tree.getParent());
        oldTree.setParentOutcome(tree.getParentOutcome());
        oldTree.replaceChildren(tree.getChildMap());

        tree = replaceLeaf(shrt, node, tree);
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
            }
        } else {
            hypothesise(null);
        }
    }

    private AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> replaceLeaf(Word<I> as,
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> node,
        AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tree) {
        if (tree.isLeaf()) {
            return addTargets(node, tree.getData().incoming);
        } else {
            setChildren(tree,
                replaceLeaf(as, node, tree.getChild(false)),
                replaceLeaf(as, node, tree.getChild(true)));
            return tree;
        }
    }

    private Boolean derive(Word<I> word, Set<DefaultQuery<I, Boolean>> values,
                           AbstractWordBasedDTNode<I, Boolean, ICState<I, Boolean>> tre) {
        if (tree.isLeaf()) {
            if (word.equals(tree.getData().accessSequence) && tree.getData().accepting != null) {
                return tree.getData().accepting;
            } else {
                Set<DefaultQuery<I, Boolean>> queries = new HashSet<>();
                values.forEach(value -> {
                    tree.getData().incoming.forEach(t -> {
                        queries.add(new DefaultQuery<>(value.getInput(), value.getOutput()));
                        queries.add(new DefaultQuery<>(t.getStart().accessSequence.append(t.getInput()).concat(value.getInput()), value.getOutput()));
                    });
                });

                for (DefaultQuery<I, Boolean> q : queries) {
                    if (q.getInput().equals(word)) {
                        return q.getOutput();
                    }
                }
            }
        } else {
            Set<DefaultQuery<I, Boolean>> queries = new HashSet<>();
            values.forEach(value -> {
                tree.getData().incoming.forEach(t -> {
                    queries.add(new DefaultQuery<>(t.getStart().accessSequence.append(t.getInput()).concat(value.getInput()), value.getOutput()));
                });
            });

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

}
