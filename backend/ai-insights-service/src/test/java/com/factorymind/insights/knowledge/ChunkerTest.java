package com.factorymind.insights.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private final Chunker chunker = new Chunker(100);

    @Test
    void paragraphsArePackedUpToTheSizeWithoutCrossingASection() {
        String a = "a".repeat(40);
        String b = "b".repeat(40);
        String c = "c".repeat(40);
        List<Chunker.Chunk> chunks = chunker.chunk(List.of(
                new DocumentParser.Section("One", null, a + "\n\n" + b + "\n\n" + c),
                new DocumentParser.Section("Two", 3, "short")));

        assertThat(chunks).extracting(Chunker.Chunk::content)
                .containsExactly(a + "\n\n" + b, c, "short");
        assertThat(chunks).extracting(Chunker.Chunk::section).containsExactly("One", "One", "Two");
        assertThat(chunks).extracting(Chunker.Chunk::index).containsExactly(0, 1, 2);
        assertThat(chunks.get(2).page()).isEqualTo(3);
    }

    @Test
    void aLongParagraphIsCutBetweenSentences() {
        String sentence = "x".repeat(60) + ".";
        List<Chunker.Chunk> chunks = chunker.chunk(List.of(
                new DocumentParser.Section(null, null, sentence + " " + sentence + " " + sentence)));

        assertThat(chunks).hasSize(3).allSatisfy(c -> assertThat(c.content()).isEqualTo(sentence));
    }
}
