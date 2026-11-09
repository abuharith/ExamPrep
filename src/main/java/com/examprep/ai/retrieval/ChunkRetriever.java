package com.examprep.ai.retrieval;

import com.examprep.api.document.DocumentChunkDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Lightweight lexical retrieval for the tutor: no vector database, no extra service.
 * Chunks are ranked with a BM25-style score over question terms, with a small bonus for
 * chunks whose location is mentioned explicitly in the question.
 */
@Component
public class ChunkRetriever {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "with", "that", "this", "these", "those", "from", "into", "about", "what",
            "which", "when", "where", "who", "why", "how", "does", "did", "can", "could", "should", "would",
            "you", "your", "are", "was", "were", "will", "have", "has", "had", "not", "but", "please",
            "explain", "describe", "give", "tell", "example", "mean", "means", "between", "using", "use",
            "their", "there", "here", "more", "most", "some", "any", "all", "also", "than", "then"));
    private static final int MAX_QUERY_TERMS = 24;

    public List<DocumentChunkDto> selectRelevant(List<DocumentChunkDto> chunks, String question, int limit) {
        if (chunks.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> terms = terms(question);
        if (terms.isEmpty()) {
            return chunks.subList(0, Math.min(limit, chunks.size()));
        }
        double averageLength = chunks.stream().mapToInt(chunk -> chunk.text().length()).average().orElse(1);
        List<String> haystacks = chunks.stream()
                .map(chunk -> (chunk.text() + " " + chunk.location()).toLowerCase(Locale.ROOT))
                .toList();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String term : terms) {
            int frequency = 0;
            for (String haystack : haystacks) {
                if (haystack.contains(term)) {
                    frequency++;
                }
            }
            documentFrequency.put(term, frequency);
        }
        List<Scored> scored = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunkDto chunk = chunks.get(index);
            String haystack = haystacks.get(index);
            double score = 0;
            for (String term : terms) {
                int frequency = countOccurrences(haystack, term);
                if (frequency > 0) {
                    int df = documentFrequency.getOrDefault(term, 0);
                    double idf = Math.log(1 + (double) chunks.size() / (1 + df));
                    double saturation = frequency * 2.2
                            / (frequency + 1.2 * (0.25 + 0.75 * chunk.text().length() / averageLength));
                    score += idf * saturation;
                }
            }
            if (haystack.contains(chunk.location().toLowerCase(Locale.ROOT).replace("page ", "page "))
                    && score > 0) {
                score += 0.75;
            }
            if (score > 0) {
                scored.add(new Scored(chunk, score));
            }
        }
        if (scored.isEmpty()) {
            // Nothing matched lexically; give the model the opening material as a fallback.
            return chunks.subList(0, Math.min(limit, chunks.size()));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<DocumentChunkDto> selected = new ArrayList<>();
        for (Scored candidate : scored) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(candidate.chunk());
        }
        return selected;
    }

    /** Evenly samples the document so a long source still fits a prompt budget. */
    public List<DocumentChunkDto> sample(List<DocumentChunkDto> chunks, int maxCharacters) {
        List<DocumentChunkDto> selected = new ArrayList<>();
        int total = 0;
        for (DocumentChunkDto chunk : chunks) {
            if (total + chunk.text().length() > maxCharacters && !selected.isEmpty()) {
                break;
            }
            selected.add(chunk);
            total += chunk.text().length();
        }
        if (selected.size() == chunks.size()) {
            return selected;
        }
        // Include the closing material too, which often holds conclusions and summaries.
        List<DocumentChunkDto> extended = new ArrayList<>(selected);
        int tail = 0;
        for (int i = chunks.size() - 1; i >= 0 && extended.size() < chunks.size(); i--) {
            DocumentChunkDto chunk = chunks.get(i);
            if (extended.contains(chunk)) {
                continue;
            }
            if (total + tail + chunk.text().length() > maxCharacters) {
                continue;
            }
            extended.add(chunk);
            tail += chunk.text().length();
        }
        extended.sort(Comparator.comparing(chunk -> chunks.indexOf(chunk)));
        return extended;
    }

    static List<String> terms(String question) {
        if (question == null) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String raw : question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() < 3 || STOP_WORDS.contains(raw)) {
                continue;
            }
            terms.add(raw);
            if (terms.size() >= MAX_QUERY_TERMS) {
                break;
            }
        }
        return List.copyOf(terms);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0 && count < 20) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private record Scored(DocumentChunkDto chunk, double score) {
    }
}
