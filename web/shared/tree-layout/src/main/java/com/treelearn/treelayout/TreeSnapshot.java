package com.treelearn.treelayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable snapshot of the visible tree, in the caller's domain order.
 *
 * <p>Order is part of the contract: {@link #childrenOf(String)} returns a branch's children in the
 * order the caller listed them, so the same snapshot always lays out the same way. This class never
 * derives an order from a hash map — maps here are lookups only, and every list that reaches the
 * geometry comes from the input list.
 */
public final class TreeSnapshot {

    private final String rootId;
    private final List<LayoutNode> nodes;
    private final Map<String, LayoutNode> byId;
    private final Map<String, List<LayoutNode>> childrenById;

    private TreeSnapshot(String rootId, List<LayoutNode> nodes, Map<String, LayoutNode> byId,
            Map<String, List<LayoutNode>> childrenById) {
        this.rootId = rootId;
        this.nodes = nodes;
        this.byId = byId;
        this.childrenById = childrenById;
    }

    /** The empty tree: no root, no nodes. Laying it out yields an empty, finite result. */
    public static TreeSnapshot empty() {
        return new TreeSnapshot(null, List.of(), Map.of(), Map.of());
    }

    /**
     * Validates and indexes a snapshot.
     *
     * @throws IllegalArgumentException if the root is missing, blank, archived or has a parent; if
     *         an id repeats; if a parent is unknown; or if the parent chain contains a cycle
     */
    public static TreeSnapshot of(String rootId, List<LayoutNode> nodes) {
        if (rootId == null || rootId.isEmpty()) {
            throw new IllegalArgumentException("rootId must not be blank; use TreeSnapshot.empty()");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("a snapshot needs at least its root node");
        }
        List<LayoutNode> ordered = new ArrayList<>(nodes.size());
        Map<String, LayoutNode> byId = new LinkedHashMap<>();
        Map<String, List<LayoutNode>> childrenById = new LinkedHashMap<>();
        for (LayoutNode node : nodes) {
            if (node == null) {
                throw new IllegalArgumentException("snapshot contains a null node");
            }
            if (byId.put(node.id, node) != null) {
                throw new IllegalArgumentException("duplicate node id: " + node.id);
            }
            ordered.add(node);
            childrenById.put(node.id, new ArrayList<>());
        }
        if (byId.get(rootId) == null) {
            throw new IllegalArgumentException("rootId is not part of the snapshot: " + rootId);
        }
        int roots = 0;
        for (LayoutNode node : ordered) {
            if (node.parentId == null) {
                roots++;
                if (!node.id.equals(rootId)) {
                    throw new IllegalArgumentException(
                            "only the root may have no parent, but " + node.id + " has none");
                }
                continue;
            }
            List<LayoutNode> siblings = childrenById.get(node.parentId);
            if (siblings == null) {
                throw new IllegalArgumentException(
                        node.id + " names an unknown parent: " + node.parentId);
            }
            siblings.add(node);
        }
        if (roots != 1) {
            throw new IllegalArgumentException("a snapshot needs exactly one root, found " + roots);
        }
        if (byId.get(rootId).archived) {
            throw new IllegalArgumentException("the root branch cannot be archived: " + rootId);
        }
        for (LayoutNode node : ordered) {
            int steps = 0;
            String cursor = node.parentId;
            while (cursor != null) {
                if (cursor.equals(node.id)) {
                    throw new IllegalArgumentException("parent cycle through " + node.id);
                }
                if (++steps > ordered.size()) {
                    throw new IllegalArgumentException("parent chain of " + node.id + " never ends");
                }
                cursor = byId.get(cursor).parentId;
            }
        }
        for (Map.Entry<String, List<LayoutNode>> entry : childrenById.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return new TreeSnapshot(rootId, Collections.unmodifiableList(ordered),
                Collections.unmodifiableMap(byId), Collections.unmodifiableMap(childrenById));
    }

    /** Null for the empty snapshot. */
    public String rootId() {
        return rootId;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /** Every node, in input order. */
    public List<LayoutNode> nodes() {
        return nodes;
    }

    /** The node with this id, or null when it is not part of the snapshot. */
    public LayoutNode node(String id) {
        return id == null ? null : byId.get(id);
    }

    /** Direct children in input order; empty for a leaf and for an unknown id. */
    public List<LayoutNode> childrenOf(String id) {
        List<LayoutNode> children = id == null ? null : childrenById.get(id);
        return children == null ? List.of() : children;
    }

    @Override
    public String toString() {
        return "TreeSnapshot[root=" + rootId + " nodes=" + nodes.size() + "]";
    }
}
