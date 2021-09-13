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
package de.learnlib.algorithms.ikv.dfa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.acex.AcexAnalyzer;
import de.learnlib.algorithms.kv.StateInfo;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFA;
import de.learnlib.algorithms.kv.dfa.KearnsVaziraniDFAState;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import de.learnlib.datastructure.discriminationtree.model.LCAInfo;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.commons.smartcollections.ArrayStorage;
import net.automatalib.commons.util.Pair;
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
public class IKearnsVaziraniDFA<I> extends KearnsVaziraniDFA<I> {

    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet
     * @param oracle
     *         the membership oracle
     */
    @GenerateBuilder
    public IKearnsVaziraniDFA(Alphabet<I> alphabet,
                              MembershipOracle<I, Boolean> oracle,
                              AcexAnalyzer counterexampleAnalyzer,
                              KearnsVaziraniDFAState<I> startingState) {
        super(alphabet, oracle, true, true, counterexampleAnalyzer);
        super.discriminationTree = startingState.getDiscriminationTree();
        super.discriminationTree.setOracle(oracle);
        super.hypothesis = startingState.getHypothesis();
        super.stateInfos = startingState.getStateInfos();
    }

    @Override
    public void startLearning() {
        if (discriminationTree.getNodes().size() == 1) {
            super.startLearning();
        } else {
            initialize();
        }
    }

    private void initialize() {
        // Minimising the tree at the start allows us the make the tree smaller, limiting sift depth.
        minimiseTree();

        DefaultQuery<I, Boolean> nonCanonCex = analyseTree();
        while (nonCanonCex != null) {
            while (refineHypothesisSingle(nonCanonCex.getInput(), nonCanonCex.getOutput())) {}
            nonCanonCex = analyseTree();
        }
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Boolean> ceQuery) {
        if (hypothesis.size() == 0) {
            throw new IllegalStateException("Not initialized");
        }
        Word<I> input = ceQuery.getInput();
        boolean output = ceQuery.getOutput();
        if (!refineHypothesisSingle(input, output)) {
            return false;
        }
        if (repeatedCounterexampleEvaluation) {
            while (refineHypothesisSingle(input, output)) {}
        }

        if (ensureCanonical) {
            DefaultQuery<I, Boolean> nonCanonCex = analyseTree();
            while (nonCanonCex != null) {
                while (refineHypothesisSingle(nonCanonCex.getInput(), nonCanonCex.getOutput())) {}
                nonCanonCex = analyseTree();
            }
        }

        return true;
    }

    private boolean refineHypothesisSingle(Word<I> input, boolean output) {
        int inputLen = input.length();

        if (inputLen < 2) {
            return false;
        }

        if (hypothesis.accepts(input) == output) {
            return false;
        }

        KVAbstractCounterexample acex = new KVAbstractCounterexample(input, output, oracle);
        int idx = ceAnalyzer.analyzeAbstractCounterexample(acex, 1);

        Word<I> prefix = input.prefix(idx);
        StateInfo<I, Boolean> srcStateInfo = acex.getStateInfo(idx);
        I sym = input.getSymbol(idx);
        LCAInfo<Boolean, AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>> lca = acex.getLCA(idx + 1);
        assert lca != null;

        splitState(srcStateInfo, prefix, sym, lca);

        minimiseTree();

        return true;
    }

    private void minimiseTree() {
        Stack<AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>> dfsStack = new Stack<>();
        dfsStack.push(discriminationTree.getRoot());
        Set<Integer> idsRemoved = new HashSet<>();

        while (!dfsStack.isEmpty()) {
            AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>> currentNode = dfsStack.pop();
            if (currentNode.isLeaf()) {
                if (currentNode != discriminationTree.sift(currentNode.getData().accessSequence)) {
                    idsRemoved.add(currentNode.getData().id);
                    removeLeaf(currentNode);
                }
            } else {
                dfsStack.addAll(currentNode.getChildren());
            }
        }

        rebuildHypothesis(idsRemoved);
    }

    private void removeLeaf(AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>> leaf) {
        Map<Boolean, AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>> newChildren = new HashMap<>();
        boolean parentOutcome = leaf.getParent().getParentOutcome();
        newChildren.put(parentOutcome, leaf.getParent().getChild(!leaf.getParentOutcome()));
        leaf.getParent().getChild(!leaf.getParentOutcome()).setParentOutcome(parentOutcome);
        leaf.getParent().getChild(!leaf.getParentOutcome()).setParent(leaf.getParent().getParent());
        newChildren.put(!parentOutcome, leaf.getParent().getParent().getChild(!parentOutcome)); // other child is unaffected.
        leaf.getParent().getParent().getChild(!parentOutcome).setParentOutcome(!parentOutcome);
        leaf.getParent().getParent().getChild(!parentOutcome).setParent(leaf.getParent().getParent());
        // Either the other child of this leaf's parent is a leaft too or it's a discriminator.
        // If it is a leaf, the leaf's parent subtree is getting completely replaced by the other leaf node.
        // If it isn't, then the leaf's parent is getting replaced by the other leaf discriminator subtree,
        // and the leaf's parent label is appended to the new discriminator.
        if (!leaf.getParent().getChild(!leaf.getParentOutcome()).isLeaf()) {
            Word<I> newDiscriminator = newChildren.get(parentOutcome).getDiscriminator();
            Word<I> oldParentDiscriminator = leaf.getParent().getDiscriminator();
            newChildren.get(parentOutcome).setDiscriminator(newDiscriminator.concat(oldParentDiscriminator));
        }

        leaf.getParent().getParent().replaceChildren(newChildren);
    }

    private void rebuildHypothesis(Set<Integer> idsRemoved) {
        Map<Integer, StateInfo<I, Boolean>> oldIds = new HashMap<>(stateInfos);
        Map<StateInfo<I, Boolean>, Integer> oldStateInfos = oldIds.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        stateInfos.clear();

        CompactDFA<I> newhyp = new CompactDFA<>(alphabet);
        Iterator<AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>> acceptingIt =
            DiscriminationTreeIterators.leafIterator(discriminationTree.getRoot().getChild(true));
        while (acceptingIt.hasNext()) {
            AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>> node = acceptingIt.next();
            node.getData().id = newhyp.addIntState(true);
            stateInfos.put(node.getData().id, node.getData());
            if (node.getData().accessSequence.getClass() == Word.epsilon().getClass()) {
                newhyp.setInitialState(node.getData().id);
            }
        }

        Iterator<AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>> rejectingIt =
            DiscriminationTreeIterators.leafIterator(discriminationTree.getRoot().getChild(false));
        while (rejectingIt.hasNext()) {
            AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>> node = rejectingIt.next();
            node.getData().id = newhyp.addIntState(false);
            stateInfos.put(node.getData().id, node.getData());
            if (node.getData().accessSequence.getClass() == Word.epsilon().getClass()) {
                newhyp.setInitialState(node.getData().id);
            }
        }

        for (StateInfo<I, Boolean> newState : stateInfos.values()) {
            for (I sym : alphabet) {
                Integer transState = hypothesis.getTransition(oldStateInfos.get(newState), sym);
                if (transState != null) {
                    if (!idsRemoved.contains(transState)) {
                        newhyp.addTransition(newState.id, sym, oldIds.get(transState).id);
                    } else {
                        Word<I> transAS = newState.accessSequence.append(sym);
                        // TODO: Sifting can get expensive quite quickly.
                        newhyp.addTransition(newState.id, sym, sift(Collections.singletonList(transAS)).get(0).id);
                    }
                }
            }
        }

        hypothesis = newhyp;

        for (StateInfo<I, Boolean> state : stateInfos.values()) {
            state.clearIncoming();
        }

        for (StateInfo<I, Boolean> state : stateInfos.values()) {
            for (I sym : alphabet) {
                stateInfos.get(hypothesis.getTransition(state.id, sym)).addIncoming(state, sym);
            }
        }
    }


    private void splitState(StateInfo<I, Boolean> stateInfo,
                            Word<I> newPrefix,
                            I sym,
                            LCAInfo<Boolean, AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>> separatorInfo) {
        int state = stateInfo.id;
        boolean oldAccepting = hypothesis.isAccepting(state);
        Set<Pair<StateInfo<I, Boolean>, I>> oldIncoming = stateInfo.fetchIncoming();

        StateInfo<I, Boolean> newStateInfo = createState(newPrefix, oldAccepting);

        AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>> stateLeaf = stateInfo.dtNode;

        AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>> separator = separatorInfo.leastCommonAncestor;
        Word<I> newDiscriminator = newDiscriminator(sym, separator.getDiscriminator());

        AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>.SplitResult sr = stateLeaf.split(newDiscriminator,
                                                                                                    separatorInfo.subtree1Label,
                                                                                                    separatorInfo.subtree2Label,
                                                                                                    newStateInfo);

        stateInfo.dtNode = sr.nodeOld;
        newStateInfo.dtNode = sr.nodeNew;

        initState(newStateInfo);

        updateTransitions(oldIncoming, stateLeaf);
    }

    private void updateTransitions(Set<Pair<StateInfo<I, Boolean>, I>> transSet,
                                   AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>> oldDtTarget) {

        List<Pair<StateInfo<I, Boolean>, I>> trans = new ArrayList<>(transSet);
        final List<Word<I>> transAs = trans.stream()
            .map(t -> t.getFirst().accessSequence.append(t.getSecond()))
            .collect(Collectors.toList());

        final List<StateInfo<I, Boolean>> succs = sift(Collections.nCopies(trans.size(), oldDtTarget), transAs);

        for (int i = 0; i < trans.size(); i++) {
            Pair<StateInfo<I, Boolean>, I> t = trans.get(i);
            setTransition(t.getFirst(), t.getSecond(), succs.get(i));
        }
    }

    private Word<I> newDiscriminator(I symbol, Word<I> succDiscriminator) {
        return succDiscriminator.prepend(symbol);
    }

    private StateInfo<I, Boolean> createState(Word<I> accessSequence, boolean accepting) {
        int state = hypothesis.addIntState(accepting);
        StateInfo<I, Boolean> si = new StateInfo<>(state, accessSequence);
        assert stateInfos.size() == state;
        stateInfos.put(si.id, si);

        return si;
    }

    private void initState(StateInfo<I, Boolean> stateInfo) {
        int alphabetSize = alphabet.size();

        Word<I> accessSequence = stateInfo.accessSequence;

        final ArrayStorage<Word<I>> transAs = new ArrayStorage<>(alphabetSize);

        for (int i = 0; i < alphabetSize; i++) {
            I sym = alphabet.getSymbol(i);
            transAs.set(i, accessSequence.append(sym));
        }

        final List<StateInfo<I, Boolean>> succs = sift(transAs);

        for (int i = 0; i < alphabetSize; i++) {
            setTransition(stateInfo, alphabet.getSymbol(i), succs.get(i));
        }
    }

    private void setTransition(StateInfo<I, Boolean> state, I symbol, StateInfo<I, Boolean> succInfo) {
        succInfo.addIncoming(state, symbol);
        hypothesis.setTransition(state.id, alphabet.getSymbolIndex(symbol), succInfo.id);
    }

    private List<StateInfo<I, Boolean>> sift(List<Word<I>> prefixes) {
        return sift(Collections.nCopies(prefixes.size(), discriminationTree.getRoot()), prefixes);
    }

    private List<StateInfo<I, Boolean>> sift(List<AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>> starts,
                                             List<Word<I>> prefixes) {

        final List<AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>>> leaves =
                discriminationTree.sift(starts, prefixes);
        final ArrayStorage<StateInfo<I, Boolean>> result = new ArrayStorage<>(leaves.size());

        for (int i = 0; i < leaves.size(); i++) {
            final AbstractWordBasedDTNode<I, Boolean, StateInfo<I, Boolean>> leaf = leaves.get(i);

            StateInfo<I, Boolean> succStateInfo = leaf.getData();
            if (succStateInfo == null) {
                // Special case: this is the *first* state of a different
                // acceptance than the initial state
                boolean initAccepting = hypothesis.isAccepting(hypothesis.getIntInitialState());
                succStateInfo = createState(prefixes.get(i), !initAccepting);
                leaf.setData(succStateInfo);
                succStateInfo.dtNode = leaf;

                initState(succStateInfo);
            }

            result.set(i, succStateInfo);
        }

        return result;
    }
}
