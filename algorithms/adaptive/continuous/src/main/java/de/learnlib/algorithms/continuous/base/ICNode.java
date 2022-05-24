package de.learnlib.algorithms.continuous.base;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.learnlib.datastructure.discriminationtree.BinaryDTNode;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import net.automatalib.words.Word;

public class ICNode<I> extends BinaryDTNode<I, Object> {

    public Word<I> accessSequence;
    public Boolean accepting = null;
    public final Set<Word<I>> origins = new HashSet<>();

    public ICNode(ICNode<I> node) {
        super((Object) null);
        this.accepting = node.accepting;
        this.accessSequence = node.accessSequence;
        this.origins.addAll(node.origins);
    }

    public ICNode(Word<I> accessSequence) {
        super((Object) null);
        this.accessSequence = accessSequence;
    }

    public ICNode() {
        super((Object) null);
    }

    @Override
    public ICNode<I> child(Boolean out) {
        return (ICNode<I>) super.child(out);
    }

    @Override
    public ICNode<I> getChild(Boolean out) {
        return (ICNode<I>) super.getChild(out);
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
    public void replaceChildren(Map<Boolean, AbstractWordBasedDTNode<I, Boolean, Object>> repChildren) {
        throw new UnsupportedOperationException("Not in use, use setChildren.");
    }

    public Set<Word<I>> getLeaves() {
        Set<Word<I>> leaves = new HashSet<>();
        DiscriminationTreeIterators.leafIterator(this)
                .forEachRemaining(n -> leaves.add(((ICNode<I>) n).accessSequence));
        return leaves;
    }

    public Set<Word<I>> getNodeOrigins() {
        HashSet<Word<I>> allOrigins = new HashSet<>();
        DiscriminationTreeIterators.nodeIterator(this)
                .forEachRemaining(n -> allOrigins.addAll(((ICNode<I>) n).origins));
        return allOrigins;
    }

    public ICNode<I> restrictOrigins(Set<Word<I>> origins) {
        ICNode<I> result = new ICNode<>();
        result.accessSequence = this.accessSequence;
        result.accepting = this.accepting;

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

        if (!this.isLeaf()) {
            result.setDiscriminator(this.getDiscriminator());
            result.setChildren(this.getChild(false).restrictOrigins(origins),
                    this.getChild(true).restrictOrigins(origins));
        }

        return result;
    }

    public void setChildren(ICNode<I> lChild, ICNode<I> rChild) {
        lChild.setParentOutcome(false);
        rChild.setParentOutcome(true);

        lChild.setParent(this);
        rChild.setParent(this);

        HashMap<Boolean, AbstractWordBasedDTNode<I, Boolean, Object>> children = new HashMap<>();
        children.put(false, lChild);
        children.put(true, rChild);
        super.replaceChildren(children);
    }
}
