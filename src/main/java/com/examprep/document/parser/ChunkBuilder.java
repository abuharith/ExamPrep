package com.examprep.document.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns structured source sections (pages, slides, headings) into chunks of a size that
 * is useful for retrieval and generation. Reference ids are preserved so every generated
 * citation can be validated against a real chunk.
 */
public final class ChunkBuilder {

    public static final int MAX_CHUNK_CHARS = 1600;
    public static final int MAX_CHUNKS = 4000;

    private ChunkBuilder() {
    }

    public record RawSection(String referenceId, String location, String text) {
    }

    public static List<ExtractedChunk> fromSections(List<RawSection> sections) {
        List<ExtractedChunk> chunks = new ArrayList<>();
        for (RawSection section : sections) {
            if (chunks.size() >= MAX_CHUNKS) {
                break;
            }
            String text = normalise(section.text());
            if (text.isBlank()) {
                continue;
            }
            if (text.length() <= MAX_CHUNK_CHARS) {
                chunks.add(new ExtractedChunk(section.referenceId(), section.location(), text));
                continue;
            }
            List<String> parts = split(text, MAX_CHUNK_CHARS);
            for (int i = 0; i < parts.size() && chunks.size() < MAX_CHUNKS; i++) {
                String reference = i == 0 ? section.referenceId() : section.referenceId() + "-" + (i + 1);
                String location = i == 0 ? section.location() : section.location() + " (part " + (i + 1) + ")";
                chunks.add(new ExtractedChunk(reference, location, parts.get(i)));
            }
        }
        return chunks;
    }

    /** Collapses whitespace noise while keeping paragraph structure. */
    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    /**
     * Splits on paragraph, sentence and finally word boundaries so that no chunk is cut
     * in the middle of a word unless a single word is longer than the limit.
     */
    static List<String> split(String text, int maxChars) {
        List<String> parts = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > maxChars) {
            int cut = remaining.lastIndexOf("\n\n", maxChars);
            if (cut < maxChars / 2) {
                cut = remaining.lastIndexOf(". ", maxChars);
                if (cut >= 0) {
                    cut += 1;
                }
            }
            if (cut < maxChars / 2) {
                cut = remaining.lastIndexOf(' ', maxChars);
            }
            if (cut < maxChars / 2) {
                cut = maxChars;
            }
            parts.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        if (!remaining.isBlank()) {
            parts.add(remaining);
        }
        return parts;
    }
}
