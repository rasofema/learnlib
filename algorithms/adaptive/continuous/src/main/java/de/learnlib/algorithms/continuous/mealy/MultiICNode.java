package de.learnlib.algorithms.continuous.mealy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import java.util.Set;

import de.learnlib.datastructure.discriminationtree.MultiDTNode;
import de.learnlib.datastructure.discriminationtree.iterators.DiscriminationTreeIterators;
import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import net.automatalib.graphs.Graph;
import net.automatalib.visualization.DefaultVisualizationHelper;
import net.automatalib.visualization.VisualizationHelper;
import net.automatalib.words.Word;

public class MultiICNode<I, O> extends MultiDTNode<I, Word<O>, Object>
        implements Graph<MultiICNode<I, O>, Map.Entry<Word<O>, MultiICNode<I, O>>> {

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

    /*
     * AutomataLib Graph API
     */
    @Override
    public Collection<MultiICNode<I, O>> getNodes() {
        List<MultiICNode<I, O>> nodes = new ArrayList<>();
        DiscriminationTreeIterators.nodeIterator(this).forEachRemaining(l -> {
            nodes.add((MultiICNode<I, O>) l);
        });

        return nodes;
    }

    @Override
    public Collection<Entry<Word<O>, MultiICNode<I, O>>> getOutgoingEdges(MultiICNode<I, O> node) {
        if (node.isLeaf()) {
            return Collections.emptySet();
        }

        Map<Word<O>, MultiICNode<I, O>> children = new HashMap<>();
        for (Entry<Word<O>, AbstractWordBasedDTNode<I, Word<O>, Object>> entry : node.getChildEntries()) {
            children.put(entry.getKey(), (MultiICNode<I, O>) entry.getValue());
        }

        return children.entrySet();
    }

    @Override
    public MultiICNode<I, O> getTarget(Entry<Word<O>, MultiICNode<I, O>> edge) {
        return edge.getValue();
    }

    @Override
    public VisualizationHelper<MultiICNode<I, O>, Entry<Word<O>, MultiICNode<I, O>>> getVisualizationHelper() {
        return new DefaultVisualizationHelper<MultiICNode<I, O>, Entry<Word<O>, MultiICNode<I, O>>>() {

            @Override
            public boolean getNodeProperties(MultiICNode<I, O> node, Map<String, String> properties) {
                if (!super.getNodeProperties(node, properties)) {
                    return false;
                }
                if (node.isLeaf()) {
                    properties.put(NodeAttrs.SHAPE, NodeShapes.BOX);
                    properties.put(NodeAttrs.LABEL,
                            node.accessSequence == null ? "null" : node.accessSequence.toString());
                } else {
                    final Word<I> d = node.getDiscriminator();
                    properties.put(NodeAttrs.SHAPE, NodeShapes.OVAL);
                    properties.put(NodeAttrs.LABEL, d.toString());
                }

                properties.put(NodeAttrs.LABEL,
                        properties.get(NodeAttrs.LABEL).concat(" / ").concat(node.origins.toString()));
                return true;
            }

            @Override
            public boolean getEdgeProperties(MultiICNode<I, O> src, Entry<Word<O>, MultiICNode<I, O>> edge,
                    MultiICNode<I, O> tgt, Map<String, String> properties) {
                if (!super.getEdgeProperties(src, edge, tgt, properties)) {
                    return false;
                }
                properties.put(EdgeAttrs.LABEL, String.valueOf(edge.getKey()));
                return true;
            }
        };
    }
}
