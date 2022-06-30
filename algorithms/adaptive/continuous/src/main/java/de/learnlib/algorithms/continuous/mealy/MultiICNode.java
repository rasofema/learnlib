package de.learnlib.algorithms.continuous.mealy;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.learnlib.datastructure.discriminationtree.MultiDTNode;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import net.automatalib.words.Word;

public class MultiICNode<I, O> extends MultiDTNode<I, Word<O>, Object> {

    public Word<I> accessSequence;
    public final Set<Word<I>> origins = new HashSet<>();
    // Store outputs in state even though it's a transtition property and not a
    // state property? Controversial but it works.
    public final Map<I, O> outputs = new HashMap<>();

    public MultiICNode(MultiICNode<I, O> node) {
        super((Object) null);
        this.outputs.putAll(node.outputs);
        this.accessSequence = node.accessSequence;
        this.origins.addAll(node.origins);
    }

    public MultiICNode(Word<I> accessSequence) {
        super((Object) null);
        this.accessSequence = accessSequence;
    }

    public MultiICNode() {
        super((Object) null);
    }

    @Override
    public MultiICNode<I, O> child(Word<O> out) {
        assert !isLeaf();
        return (MultiICNode<I, O>) this.getChild(out);
    }

    public Collection<MultiICNode<I, O>> getChildrenNative() {
        if (isLeaf()) {
            return Collections.emptySet();
        }

        HashSet<MultiICNode<I, O>> children = new HashSet<>(super.getChildren().size());
        for (AbstractWordBasedDTNode<I, Word<O>, Object> multiICNode : super.getChildren()) {
            children.add((MultiICNode<I, O>) multiICNode);
        }
        return children;
    }

    @Override
    public int getDepth() {
        throw new UnsupportedOperationException("Not in use.");
    }

    @Override
    public void setDepth(int newDepth) {
        throw new UnsupportedOperationException("Not in use.");
    }

    @Override
    public Object getData() {
        throw new UnsupportedOperationException("Not in use, use origins / accessSequence directly.");
    }

    @Override
    public void setData(Object data) {
        throw new UnsupportedOperationException("Not in use, use origins / accessSequence directly.");
    }

    @Override
    public void clearData() {
        throw new UnsupportedOperationException("Not in use, use origins / accessSequence directly.");
    }

    public Set<Word<I>> getLeaves() {
        Set<Word<I>> leaves = new HashSet<>();
        DiscriminationTreeIterators.leafIterator(this)
                .forEachRemaining(n -> leaves.add(((MultiICNode<I, O>) n).accessSequence));
        return leaves;
    }

    public Set<Word<I>> getNodeOrigins() {
        HashSet<Word<I>> allOrigins = new HashSet<>();
        DiscriminationTreeIterators.nodeIterator(this)
                .forEachRemaining(n -> allOrigins.addAll(((MultiICNode<I, O>) n).origins));
        return allOrigins;
    }

    public void restrictOrigins(Set<Word<I>> origins) {
        this.origins.removeIf(o -> !origins.contains(o));

        if (!this.isLeaf()) {
            this.getChildren().stream().map(c -> (MultiICNode<I, O>) c).forEach(c -> c.restrictOrigins(origins));
        }
    }

    public void setChild(Word<O> out, MultiICNode<I, O> newChild) {
        newChild.setParentOutcome(out);
        newChild.setParent(this);

        if (isLeaf()) {
            children = new HashMap<>();
        }

        children.put(out, newChild);
        super.replaceChildren(children);
    }

    public void removeChild(Word<O> out) {
        children.remove(out);
    }
}
