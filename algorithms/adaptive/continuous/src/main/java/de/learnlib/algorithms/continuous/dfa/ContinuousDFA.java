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
package de.learnlib.algorithms.continuous.dfa;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import de.learnlib.algorithms.continuous.base.ICNode;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.commons.util.Pair;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

/**
 * The Continuous algorithm for tree-based adaptive learning.
 *
 * @param <I> input symbol type
 *
 * @author Tiago Fereira
 */
public class ContinuousDFA<I> {

    public interface Activity {
        void process(Boolean answer);
    }

    public class TestActivity implements Activity {
        @Override
        public void process(Boolean answer) {
            ICHypothesisDFA<I> hyp = extractHypothesis(Collections.emptySet(), tree);
            if (hyp.accepts(query) == answer) {
                query = sampleWord();
            } else {
                activity = new InitActivity(query);
                query = hyp.getState(Word.epsilon()).concat(query);
            }
        }
    }

    public class QueryActivity implements Activity {
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
        public void process(Boolean answer) {
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

    public class HypActivity implements Activity {
        @Override
        public void process(Boolean answer) {
            Word<I> nextQuery = hypothesisQuery(tree);
            if (nextQuery == null) {
                query = sampleWord();
                activity = new TestActivity();
            } else {
                query = nextQuery;
            }
        }
    }

    public class InitActivity implements Activity {
        public Word<I> cex;

        public InitActivity(Word<I> cex) {
            this.cex = cex;
        }

        @Override
        public void process(Boolean answer) {
            initialiseCounterexample(answer, cex);
        }
    }

    public class CexActivity implements Activity {
        public Word<I> pre;
        public Word<I> u;
        public Word<I> v;
        public Word<I> post;

        public CexActivity(Word<I> pre, Word<I> u, Word<I> v, Word<I> post) {
            this.pre = pre;
            this.u = u;
            this.v = v;
            this.post = post;
        }

        @Override
        public void process(Boolean answer) {
            handleCounterexample(answer, this);
        }
    }

    private final Alphabet<I> alphabet;
    private final double alpha;
    private final MembershipOracle<I, Boolean> oracle;
    private final Random RAND;
    private Activity activity;
    private ICNode<I> tree;
    private Word<I> query;

    /**
     * Constructor.
     *
     * @param alphabet the learning alphabet
     * @param oracle   the membership oracle
     */
    public ContinuousDFA(Alphabet<I> alphabet, double alpha, MembershipOracle<I, Boolean> oracle, Random random) {
        // TODO: State.
        this.alphabet = alphabet;
        this.alpha = alpha;
        this.oracle = oracle;
        this.activity = new HypActivity();
        this.query = Word.epsilon();
        this.tree = new ICNode<>(Word.epsilon());
        tree.origins.add(Word.epsilon());
        this.alphabet.forEach(s -> tree.origins.add(Word.fromSymbols(s)));
        this.RAND = random;
    }

    private Pair<ICHypothesisDFA<I>, Word<I>> update(boolean answer) {
        applyAnswers(Collections.singleton(new DefaultQuery<>(query, answer)));
        tree = advanceHypothesis(query, answer, tree);
        activity.process(answer);
        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        return Pair.of(hyp, query);
    }

    private ICHypothesisDFA<I> extractHypothesis(Set<Word<I>> extraOrigins, ICNode<I> localTree) {
        if (localTree.isLeaf()) {
            Word<I> initial = null;
            if (localTree.origins.contains(Word.epsilon()) || extraOrigins.contains(Word.epsilon())) {
                initial = localTree.accessSequence;
            }

            ICHypothesisDFA<I> hyp = new ICHypothesisDFA<>(alphabet);
            hyp.setInitial(initial);

            for (Word<I> origin : localTree.origins) {
                if (!origin.equals(Word.epsilon())) {
                    hyp.addTransition(origin.prefix(origin.length() - 1), origin.lastSymbol(),
                            localTree.accessSequence);
                }
            }

            for (Word<I> origin : extraOrigins) {
                if (!origin.equals(Word.epsilon())) {
                    hyp.addTransition(origin.prefix(origin.length() - 1), origin.lastSymbol(),
                            localTree.accessSequence);
                }
            }

            hyp.setAccepting(localTree.accessSequence, localTree.accepting != null ? localTree.accepting : false);

            return hyp;
        } else {
            Set<Word<I>> leftOrigins = new HashSet<>();
            leftOrigins.addAll(extraOrigins);
            leftOrigins.addAll(localTree.origins);

            ICHypothesisDFA<I> leftHyp = extractHypothesis(leftOrigins, localTree.getChild(false));
            ICHypothesisDFA<I> rightHyp = extractHypothesis(new HashSet<>(), localTree.getChild(true));

            ICHypothesisDFA<I> hyp = new ICHypothesisDFA<>(alphabet);
            Word<I> initial = leftHyp.getInitialState() != null ? leftHyp.getInitialState()
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
        Pair<ICNode<I>, Set<Word<I>>> stateAdjustment = adjustStates(answers, tree);
        tree = stateAdjustment.getFirst();

        if (tree == null) {
            this.tree = new ICNode<>(Word.epsilon());
            tree.origins.add(Word.epsilon());
            this.alphabet.forEach(s -> tree.origins.add(Word.fromSymbols(s)));
        } else {
            tree.restrictOrigins(tree.getLeaves());
        }
        tree = adjustStructure(answers, tree);
    }

    private ICNode<I> adjustStructure(Set<DefaultQuery<I, Boolean>> answers, ICNode<I> tree) {
        return adjustStructure(answers, new HashSet<>(), tree);
    }

    private ICNode<I> adjustStructure(Set<DefaultQuery<I, Boolean>> answers, Set<Word<I>> removed, ICNode<I> tree) {
        if (tree.isLeaf()) {
            tree.origins.removeAll(removed);
            if (tree.accepting != null) {
                for (DefaultQuery<I, Boolean> answer : answers) {
                    if (answer.getInput().equals(tree.accessSequence) && answer.getOutput().equals(!tree.accepting)) {
                        tree.accepting = !tree.accepting;
                        return tree;
                    }
                }
            }
        } else {
            Set<Word<I>> removeLeft = tree.getChild(false).getNodeOrigins().stream().filter(t -> {
                for (DefaultQuery<I, Boolean> query : answers) {
                    if (query.getInput().equals(t.concat(tree.getDiscriminator())) && query.getOutput().equals(true)) {
                        return true;
                    }
                }
                return false;
            }).filter(t -> !removed.contains(t)).collect(Collectors.toSet());
            Set<Word<I>> removeRight = tree.getChild(true).getNodeOrigins().stream().filter(t -> {
                for (DefaultQuery<I, Boolean> query : answers) {
                    if (query.getInput().equals(t.concat(tree.getDiscriminator())) && query.getOutput().equals(false)) {
                        return true;
                    }
                }
                return false;
            }).filter(t -> !removed.contains(t)).collect(Collectors.toSet());

            Set<Word<I>> adjustLeft = new HashSet<>(removed);
            adjustLeft.addAll(removeLeft);
            ICNode<I> leftPrime = adjustStructure(answers, adjustLeft, tree.getChild(false));

            Set<Word<I>> adjustRight = new HashSet<>(removed);
            adjustRight.addAll(removeRight);
            ICNode<I> rightPrime = adjustStructure(answers, adjustRight, tree.getChild(true));

            leftPrime.origins.addAll(removeRight);
            rightPrime.origins.addAll(removeLeft);
            tree.origins.removeAll(removed);
            tree.setChildren(leftPrime, rightPrime);
        }
        return tree;
    }

    private Pair<ICNode<I>, Set<Word<I>>> adjustStates(Set<DefaultQuery<I, Boolean>> answers, ICNode<I> tree) {
        return adjustStates(answers, new HashSet<>(), tree);
    }

    private Pair<ICNode<I>, Set<Word<I>>> adjustStates(Set<DefaultQuery<I, Boolean>> answers, Set<Word<I>> removed,
            ICNode<I> tree) {
        if (tree.isLeaf()) {
            if (removed.contains(tree.accessSequence)) {
                return Pair.of(null, new HashSet<>(tree.origins));
            }
            return Pair.of(tree, new HashSet<>());
        } else {
            Set<Word<I>> removeLeft = new HashSet<>();
            DiscriminationTreeIterators.leafIterator(tree.getChild(false)).forEachRemaining(l -> {
                for (DefaultQuery<I, Boolean> query : answers) {
                    if (query.getInput().equals(((ICNode<I>) l).accessSequence.concat(tree.getDiscriminator()))
                            && query.getOutput().equals(true)) {
                        removeLeft.add(((ICNode<I>) l).accessSequence);
                    }
                }
            });
            HashSet<Word<I>> adjustLeft = new HashSet<>();
            adjustLeft.addAll(removeLeft);
            adjustLeft.addAll(removed);
            Pair<ICNode<I>, Set<Word<I>>> leftAdjustment = adjustStates(answers, adjustLeft, tree.getChild(false));
            ICNode<I> leftPrime = leftAdjustment.getFirst();
            Set<Word<I>> originsLeft = leftAdjustment.getSecond();
            if (leftPrime == null) {
                Pair<ICNode<I>, Set<Word<I>>> rightAdjustment = adjustStates(answers, removed, tree.getChild(true));
                ICNode<I> rightPrime = rightAdjustment.getFirst();
                Set<Word<I>> originsRight = rightAdjustment.getSecond();

                if (rightPrime == null) {
                    HashSet<Word<I>> extraOrigins = new HashSet<>();
                    extraOrigins.addAll(tree.origins);
                    extraOrigins.addAll(originsLeft);
                    extraOrigins.addAll(originsRight);

                    return Pair.of(null, new HashSet<>(extraOrigins));
                }

                rightPrime.origins.addAll(tree.origins);
                rightPrime.origins.addAll(originsLeft);
                rightPrime.setParent(null);
                rightPrime.setParentOutcome(null);
                return Pair.of(rightPrime, new HashSet<>());
            }
            Set<Word<I>> removeRight = new HashSet<>();
            DiscriminationTreeIterators.leafIterator(tree.getChild(true)).forEachRemaining(l -> {
                for (DefaultQuery<I, Boolean> query : answers) {
                    if (query.getInput().equals(((ICNode<I>) l).accessSequence.concat(tree.getDiscriminator()))
                            && query.getOutput().equals(false)) {
                        removeRight.add(((ICNode<I>) l).accessSequence);
                    }
                }
            });

            HashSet<Word<I>> adjustRight = new HashSet<>();
            adjustRight.addAll(removeRight);
            adjustRight.addAll(removed);
            Pair<ICNode<I>, Set<Word<I>>> rightAdjustment = adjustStates(answers, adjustRight, tree.getChild(true));
            ICNode<I> rightPrime = rightAdjustment.getFirst();
            Set<Word<I>> originsRight = rightAdjustment.getSecond();

            if (rightPrime == null) {
                leftPrime.origins.addAll(tree.origins);
                leftPrime.origins.addAll(originsRight);
                leftPrime.setParent(null);
                leftPrime.setParentOutcome(null);
                return Pair.of(leftPrime, new HashSet<>());
            }

            ICNode<I> node = new ICNode<>();
            node.origins.addAll(tree.origins);
            node.setDiscriminator(tree.getDiscriminator());
            node.setChildren(leftPrime, rightPrime);
            return Pair.of(node, new HashSet<>());
        }
    }

    private ICNode<I> advanceHypothesis(Word<I> query, Boolean answer, ICNode<I> tree) {
        return advanceHypothesis(query, answer, new HashSet<>(), tree);
    }

    private ICNode<I> advanceHypothesis(Word<I> query, Boolean answer, Set<Word<I>> extraOrigins, ICNode<I> tree) {
        ICNode<I> newNode = new ICNode<>();
        if (tree.isLeaf()) {
            newNode.accessSequence = tree.accessSequence;
            newNode.origins.addAll(tree.origins);
            newNode.origins.addAll(extraOrigins);
            newNode.accepting = tree.accepting;
            if (tree.accessSequence.equals(query)) {
                newNode.accepting = answer;
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
                newNode.setChildren(advanceHypothesis(query, answer, tree.getChild(false)),
                        advanceHypothesis(query, answer, tree.getChild(true)));
            } else if (answer) {
                newNode.setChildren(advanceHypothesis(query, true, tree.getChild(false)),
                        advanceHypothesis(query, true, moved, tree.getChild(true)));
            } else {
                newNode.setChildren(advanceHypothesis(query, false, moved, tree.getChild(false)),
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
            if (!tree.origins.isEmpty()) {
                Word<I> trans = tree.origins.iterator().next();
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

    private Set<DefaultQuery<I, Boolean>> implications(Word<I> leaf, Set<DefaultQuery<I, Boolean>> current,
            ICNode<I> currentTree) {
        if (currentTree.isLeaf()) {
            if (currentTree.accessSequence.equals(leaf)) {
                return current.stream().map(q -> new DefaultQuery<>(leaf.concat(q.getInput()), q.getOutput()))
                        .collect(Collectors.toSet());

            }
            return new HashSet<>();
        }
        Set<DefaultQuery<I, Boolean>> leftCurrent = new HashSet<>(current);
        leftCurrent.add(new DefaultQuery<>(currentTree.getDiscriminator(), false));
        Set<DefaultQuery<I, Boolean>> left = implications(leaf, leftCurrent, currentTree.getChild(false));

        Set<DefaultQuery<I, Boolean>> rightCurrent = new HashSet<>(current);
        rightCurrent.add(new DefaultQuery<>(currentTree.getDiscriminator(), true));
        Set<DefaultQuery<I, Boolean>> right = implications(leaf, rightCurrent, currentTree.getChild(true));

        Set<DefaultQuery<I, Boolean>> result = new HashSet<>(left);
        result.addAll(right);
        return result;
    }

    private void initialiseCounterexample(Boolean answer, Word<I> cex) {
        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        if (hyp.computeOutput(cex) == answer) {
            finishCounterexample(answer, hyp.getState(Word.epsilon()), Word.epsilon(), cex);
        } else {
            splitCounterexample(Word.epsilon(), cex, Word.epsilon());
        }
    }

    private void handleCounterexample(Boolean answer, CexActivity bsState) {
        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        if (bsState.u.size() == 1 && bsState.v.equals(Word.epsilon())) {
            finishCounterexample(answer, Objects.requireNonNull(hyp.getState(bsState.pre.concat(bsState.u))),
                    Objects.requireNonNull(hyp.getState(bsState.pre)).concat(bsState.u), bsState.post);
        } else if (hyp.computeOutput(query) == answer) {
            splitCounterexample(bsState.pre, bsState.u, bsState.v.concat(bsState.post));
        } else {
            splitCounterexample(bsState.pre.concat(bsState.u), bsState.v, bsState.post);
        }
    }

    private void splitCounterexample(Word<I> pre, Word<I> middle, Word<I> post) {
        Word<I> u = middle.prefix((middle.length() + 1) / 2);
        Word<I> v = middle.suffix((middle.length()) / 2);

        assert u.concat(v).equals(middle) && ((u.length() - v.length()) == 0 || (u.length() - v.length()) == 1);
        ICHypothesisDFA<I> hyp = extractHypothesis(new HashSet<>(), tree);
        activity = new CexActivity(pre, u, v, post);
        query = Objects.requireNonNull(hyp.getState(pre.concat(u))).concat(v).concat(post);
    }

    private ICNode<I> replaceLeaf(Word<I> s, ICNode<I> node, ICNode<I> tree) {
        if (tree.isLeaf() && tree.accessSequence.equals(s)) {
            node.origins.addAll(tree.origins);
            return node;
        }
        if (!tree.isLeaf()) {
            tree.setChildren(replaceLeaf(s, node, tree.getChild(false)), replaceLeaf(s, node, tree.getChild(true)));
            return tree;
        }
        return tree;
    }

    private void finishCounterexample(Boolean swap, Word<I> shrt, Word<I> lng, Word<I> e) {
        // FIXME: This is for debug purposes. In a correct implementation should be
        // guaranteed.
        assert tree.getLeaves().contains(shrt);

        Boolean duplicate = tree.getLeaves().contains(lng);
        ICNode<I> node = new ICNode<>();
        node.setDiscriminator(e);
        ICNode<I> longNode = new ICNode<>(lng);
        ICNode<I> shortNode = new ICNode<>(shrt);
        node.setChildren(swap ? longNode : shortNode, swap ? shortNode : longNode);

        tree = replaceLeaf(shrt, node, tree);

        if (!duplicate) {
            alphabet.forEach(a -> tree.origins.add(lng.append(a)));
        }

        // FIXME: Should implications be a list? Does order matter?
        Set<DefaultQuery<I, Boolean>> newImp = implications(lng, new HashSet<>(), tree);
        newImp.add(new DefaultQuery<>(shrt.concat(e), swap));
        applyAnswers(newImp);

        activity = new QueryActivity(e, shrt, lng);
        query = shrt.concat(e);
    }

    public List<Pair<Integer, CompactDFA<I>>> learn(int limit, int sample) {
        List<Pair<Integer, CompactDFA<I>>> hyps = new LinkedList<>();
        Boolean answer = oracle.answerQuery(query);
        for (int i = 0; i < limit; i++) {
            Pair<ICHypothesisDFA<I>, Word<I>> pair = update(answer);
            ICHypothesisDFA<I> hyp = pair.getFirst();
            if (i % sample == 0) {
                hyps.add(Pair.of(i, DFAs.or(hyp, hyp, alphabet)));
            }
            answer = oracle.answerQuery(pair.getSecond());
        }
        return hyps;
    }
}