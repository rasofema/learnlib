package de.learnlib.algorithms.continuous.mealy;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.learnlib.datastructure.discriminationtree.MultiDTNode;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import net.automatalib.words.Word;

public class MultiICNode<I, O> extends MultiDTNode<I, O, Object> {

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
    public MultiICNode<I, O> child(O out) {
        return (MultiICNode<I, O>) super.child(out);
    }

    @Override
    public MultiICNode<I, O> getChild(O out) {
        // This is quite important. A Binary tree always has each possible branch.
        // In a multi tree, we may be requesting a branch that does not exist, so we
        // create it JIT.
        return (MultiICNode<I, O>) super.child(out);
    }

    public Collection<MultiICNode<I, O>> getChildrenNative() {
        HashSet<MultiICNode<I, O>> children = new HashSet<>(super.getChildren().size());
        for (AbstractWordBasedDTNode<I, O, Object> multiICNode : super.getChildren()) {
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

    @Override
    public void replaceChildren(Map<O, AbstractWordBasedDTNode<I, O, Object>> repChildren) {
        throw new UnsupportedOperationException("Not in use, use setChildren.");
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

    public MultiICNode<I, O> restrictOrigins(Set<Word<I>> origins) {
        MultiICNode<I, O> result = new MultiICNode<>();
        result.accessSequence = this.accessSequence;

        HashSet<Word<I>> oldTargets = new HashSet<>(this.origins);
        for (Word<I> origin : oldTargets) {
            if (!origin.equals(Word.epsilon())) {
                if (origins.contains(origin.prefix(origin.length() - 1))) {
                    result.origins.add(origin);
                }
            } else if (oldTargets.contains(Word.epsilon())) {
                result.origins.add(Word.epsilon());
            }
        }

        // This here is different but expected. We don't just recursively go left and
        // right, we recursively go to all child nodes.
        if (!this.isLeaf()) {
            result.setDiscriminator(this.getDiscriminator());
            for (Entry<O, AbstractWordBasedDTNode<I, O, Object>> child : this.getChildEntries()) {
                result.setChild(child.getKey(), ((MultiICNode<I, O>) child.getValue()).restrictOrigins(origins));
            }
        }

        return result;
    }

    public void setChild(O out, MultiICNode<I, O> newChild) {
        newChild.setParentOutcome(out);
        newChild.setParent(this);

        children.put(out, newChild);
        super.replaceChildren(children);
    }
}
