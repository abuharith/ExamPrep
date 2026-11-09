package com.examprep.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkBuilderTest {

    @Test
    void keepsSmallSectionsIntactWithTheirReferences() {
        List<ExtractedChunk> chunks = ChunkBuilder.fromSections(List.of(
                new ChunkBuilder.RawSection("p1", "Page 1", "Cell theory states that cells are the basic unit."),
                new ChunkBuilder.RawSection("p2", "Page 2", "Mitochondria produce ATP through respiration.")));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).referenceId()).isEqualTo("p1");
        assertThat(chunks.get(0).location()).isEqualTo("Page 1");
        assertThat(chunks.get(1).referenceId()).isEqualTo("p2");
    }

    @Test
    void splitsLongSectionsAndSuffixesReferences() {
        String sentence = "Photosynthesis converts light energy into chemical energy. ";
        String longText = sentence.repeat(80);

        List<ExtractedChunk> chunks = ChunkBuilder.fromSections(
                List.of(new ChunkBuilder.RawSection("p7", "Page 7", longText)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).referenceId()).isEqualTo("p7");
        assertThat(chunks.get(0).location()).isEqualTo("Page 7");
        assertThat(chunks.get(1).referenceId()).isEqualTo("p7-2");
        assertThat(chunks.get(1).location()).contains("part 2");
        chunks.forEach(chunk -> {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(ChunkBuilder.MAX_CHUNK_CHARS);
            assertThat(chunk.text()).isNotBlank();
        });
    }

    @Test
    void skipsBlankSectionsAndNormalisesWhitespace() {
        List<ExtractedChunk> chunks = ChunkBuilder.fromSections(List.of(
                new ChunkBuilder.RawSection("p1", "Page 1", "   \n\n "),
                new ChunkBuilder.RawSection("p2", "Page 2", "Line one\r\n\r\n\r\nLine two")));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("Line one\n\nLine two");
    }
}
