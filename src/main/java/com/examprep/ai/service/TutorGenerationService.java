package com.examprep.ai.service;

import com.examprep.ai.client.GeminiGateway;
import com.examprep.ai.client.GeminiPrompt;
import com.examprep.ai.dto.AiDtos;
import com.examprep.ai.prompt.StudyPrompts;
import com.examprep.ai.retrieval.ChunkRetriever;
import com.examprep.api.document.DocumentChunkDto;
import com.examprep.api.document.DocumentContext;
import com.examprep.api.study.TutorAnswerDto;
import com.examprep.api.study.TutorMessageDto;
import com.examprep.common.exception.AiServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Answers student questions using only the retrieved excerpts of their own document. */
@Service
public class TutorGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TutorGenerationService.class);
    private static final int EXCERPTS = 6;
    private static final int HISTORY_MESSAGES = 8;

    private final GeminiGateway gateway;
    private final ObjectMapper objectMapper;
    private final ChunkRetriever retriever;

    public TutorGenerationService(GeminiGateway gateway, ObjectMapper objectMapper, ChunkRetriever retriever) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.retriever = retriever;
    }

    public TutorAnswerDto answer(DocumentContext document, List<TutorMessageDto> history, String question,
                                 Instant answeredAt) {
        List<DocumentChunkDto> excerpts = retriever.selectRelevant(document.chunks(), question, EXCERPTS);
        if (excerpts.isEmpty()) {
            return new TutorAnswerDto(
                    "The uploaded material does not contain enough information to answer that question.",
                    List.of(), true, answeredAt);
        }
        Map<String, String> labels = new LinkedHashMap<>();
        StringBuilder excerptText = new StringBuilder();
        for (DocumentChunkDto chunk : excerpts) {
            labels.put(chunk.referenceId(), chunk.location());
            excerptText.append('[').append(chunk.referenceId()).append(" | ").append(chunk.location())
                    .append("]\n").append(chunk.text()).append("\n\n");
        }
        String conversation = renderHistory(history);
        GeminiPrompt prompt = new GeminiPrompt(
                StudyPrompts.tutorSystem(document.title()),
                StudyPrompts.tutorTask(question, conversation, excerptText.toString()),
                4096);
        AiDtos.TutorReply reply = callJson(prompt);
        if (reply == null || reply.answer() == null || reply.answer().isBlank()) {
            throw new AiServiceException("The AI tutor returned an empty answer. Please try again.");
        }
        Set<String> references = new LinkedHashSet<>();
        if (reply.sourceReferences() != null) {
            for (String reference : reply.sourceReferences()) {
                String label = labels.get(reference == null ? "" : reference.trim());
                if (label != null) {
                    references.add(label);
                }
            }
        }
        boolean insufficient = Boolean.TRUE.equals(reply.insufficientInformation());
        return new TutorAnswerDto(reply.answer().trim(), List.copyOf(references), insufficient, answeredAt);
    }

    private AiDtos.TutorReply callJson(GeminiPrompt prompt) {
        AiServiceException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String raw = gateway.generateJson(prompt);
            try {
                return objectMapper.readValue(stripFence(raw), AiDtos.TutorReply.class);
            } catch (JsonProcessingException ex) {
                lastFailure = new AiServiceException(
                        "The AI tutor returned a response that could not be read. Please try again.", ex);
                log.warn("Tutor returned unparseable JSON (attempt {})", attempt);
            }
        }
        throw lastFailure;
    }

    private static String stripFence(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            if (firstNewline > 0) {
                value = value.substring(firstNewline + 1);
            }
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3);
            }
        }
        return value.trim();
    }

    private static String renderHistory(List<TutorMessageDto> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        List<TutorMessageDto> recent = history.subList(Math.max(0, history.size() - HISTORY_MESSAGES), history.size());
        StringBuilder conversation = new StringBuilder();
        for (TutorMessageDto message : recent) {
            String content = message.content() == null ? "" : message.content().trim();
            if (content.length() > 900) {
                content = content.substring(0, 900) + "\u2026";
            }
            conversation.append("ASSISTANT".equalsIgnoreCase(message.role()) ? "TUTOR: " : "STUDENT: ")
                    .append(content).append('\n');
        }
        return conversation.toString();
    }
}
