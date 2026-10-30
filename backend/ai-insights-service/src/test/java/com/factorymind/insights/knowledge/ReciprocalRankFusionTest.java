package com.factorymind.insights.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void aPassageFoundByBothSearchesBeatsTheTopOfOnlyOne() {
        // Keywords: a, b, c. Meaning: d, b. b is second in both lists, so it wins.
        var fused = KnowledgeBase.fuse(List.of(List.of(a, b, c), List.of(d, b)));

        assertThat(fused.keySet()).containsExactly(b, a, d, c);
        assertThat(fused.get(b)).isEqualTo(2.0 / 62);
    }

    @Test
    void withOneListTheOrderIsKept() {
        assertThat(KnowledgeBase.fuse(List.of(List.of(c, a, b), List.of())).keySet()).containsExactly(c, a, b);
    }
}
