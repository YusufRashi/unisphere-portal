package com.unisphere.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrerequisiteGraphTest {

    // Course ids used as readable names.
    private static final long CS101 = 1, CS201 = 2, CS301 = 3, CS401 = 4, MA101 = 5;

    private static PrerequisiteGraph graph(long[]... edges) {
        return new PrerequisiteGraph(List.of(edges));
    }

    @Test
    void acceptsPrerequisitesWhenNoCycleExists() {
        PrerequisiteGraph g = graph(new long[] {CS201, CS101}, new long[] {CS301, CS201});
        assertThat(g.findCycleIfReplaced(CS401, Set.of(CS301, MA101))).isEmpty();
    }

    @Test
    void detectsDirectTwoCourseCycle() {
        PrerequisiteGraph g = graph(new long[] {CS201, CS101});
        assertThat(g.findCycleIfReplaced(CS101, Set.of(CS201))).contains(List.of(CS101, CS201, CS101));
    }

    @Test
    void detectsIndirectCycleAndReportsThePath() {
        // CS301 → CS201 → CS101; making CS101 require CS301 closes the loop.
        PrerequisiteGraph g = graph(new long[] {CS301, CS201}, new long[] {CS201, CS101});
        assertThat(g.findCycleIfReplaced(CS101, Set.of(CS301))).contains(List.of(CS101, CS301, CS201, CS101));
    }

    @Test
    void diamondShapedDependenciesAreNotACycle() {
        // CS401 requires CS301 and CS201, both of which require CS101.
        PrerequisiteGraph g = graph(new long[] {CS301, CS101}, new long[] {CS201, CS101});
        assertThat(g.findCycleIfReplaced(CS401, Set.of(CS301, CS201))).isEmpty();
    }

    @Test
    void replacingPrerequisitesIgnoresTheCoursesOldEdges() {
        // CS101 currently requires CS201 (bad data); replacing it with MA101 must not see that old edge.
        PrerequisiteGraph g = graph(new long[] {CS101, CS201}, new long[] {CS201, CS101});
        assertThat(g.findCycleIfReplaced(CS101, Set.of(MA101))).isEmpty();
    }

    @Test
    void clearingPrerequisitesIsAlwaysAllowed() {
        PrerequisiteGraph g = graph(new long[] {CS201, CS101});
        assertThat(g.findCycleIfReplaced(CS201, Set.of())).isEmpty();
    }

    @Test
    void handlesLongChainsWithoutRecursion() {
        long[][] chain = new long[5000][];
        for (int i = 0; i < chain.length; i++) {
            chain[i] = new long[] {i + 2, i + 1}; // course i+2 requires course i+1
        }
        PrerequisiteGraph g = new PrerequisiteGraph(List.of(chain));
        assertThat(g.findCycleIfReplaced(1, Set.of(5001L))).isPresent();
        assertThat(g.findCycleIfReplaced(9999, Set.of(5001L))).isEmpty();
    }
}
