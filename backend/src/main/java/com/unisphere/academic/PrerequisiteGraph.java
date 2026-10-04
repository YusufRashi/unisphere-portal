package com.unisphere.academic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Directed graph "course → its prerequisites". Used to reject prerequisite changes that would make a
 * course (indirectly) require itself, which would make it impossible for anyone to ever take it.
 * Pure Java so it can be tested exhaustively without a database.
 */
public final class PrerequisiteGraph {

    private final Map<Long, Set<Long>> edges = new HashMap<>();

    /** @param edges pairs of (courseId, prerequisiteId) as stored in course_prerequisites */
    public PrerequisiteGraph(Collection<long[]> edges) {
        for (long[] edge : edges) {
            this.edges.computeIfAbsent(edge[0], k -> new HashSet<>()).add(edge[1]);
        }
    }

    /**
     * Checks whether giving {@code courseId} exactly {@code newPrerequisites} would create a cycle.
     *
     * @return the cycle as a list of course ids starting and ending with {@code courseId}, if one would exist
     */
    public Optional<List<Long>> findCycleIfReplaced(long courseId, Collection<Long> newPrerequisites) {
        Map<Long, Set<Long>> candidate = new HashMap<>(edges);
        candidate.put(courseId, new HashSet<>(newPrerequisites));

        // Depth-first search from each new prerequisite looking for a path back to courseId.
        for (Long start : newPrerequisites) {
            List<Long> path = pathTo(candidate, start, courseId);
            if (path != null) {
                List<Long> cycle = new ArrayList<>();
                cycle.add(courseId);
                cycle.addAll(path);
                return Optional.of(cycle);
            }
        }
        return Optional.empty();
    }

    /** Iterative DFS returning the path from {@code from} to {@code target} (inclusive), or null. */
    private static List<Long> pathTo(Map<Long, Set<Long>> graph, long from, long target) {
        Deque<Long> stack = new ArrayDeque<>();
        Map<Long, Long> parent = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        stack.push(from);
        visited.add(from);
        while (!stack.isEmpty()) {
            long node = stack.pop();
            if (node == target) {
                List<Long> path = new ArrayList<>();
                for (Long n = node; n != null; n = parent.get(n)) {
                    path.add(n);
                }
                Collections.reverse(path);
                return path;
            }
            for (Long next : graph.getOrDefault(node, Set.of())) {
                if (visited.add(next)) {
                    parent.put(next, node);
                    stack.push(next);
                }
            }
        }
        return null;
    }
}
