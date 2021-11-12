package de.learnlib.algorithms.continuous.base;

import java.util.HashSet;
import java.util.Set;

import de.learnlib.datastructure.discriminationtree.BinaryDTNode;
import net.automatalib.words.Word;

public class ICNode<I> extends BinaryDTNode<I, Object> {

    public Word<I> accessSequence;
    public Boolean accepting;
    public final Set<Word<I>> targets = new HashSet<>();

    public ICNode(ICNode<I> node) {
        super((Object) null);
        this.accepting = node.accepting;
        this.accessSequence = node.accessSequence;
        this.targets.addAll(node.targets);
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
        throw new UnsupportedOperationException("Not in use, use targets / accessSequence directly.");
    }

    @Override
    public void setData(Object data) {
        throw new UnsupportedOperationException("Not in use, use targets / accessSequence directly.");
    }

    @Override
    public void clearData() {
        throw new UnsupportedOperationException("Not in use, use targets / accessSequence directly.");
    }




}
