package com.examprep.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Serialises generated content into text columns and back into typed DTOs. */
@Component
public class JsonCodec {

    private static final Logger log = LoggerFactory.getLogger(JsonCodec.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Generated content could not be serialised", ex);
        }
    }

    public <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            log.warn("Stored study content could not be read as {}", type.getSimpleName());
            return null;
        }
    }

    public <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> values = objectMapper.readValue(json, type);
            return values == null ? List.of() : values;
        } catch (JsonProcessingException ex) {
            log.warn("Stored study content list could not be read");
            return List.of();
        }
    }

    public List<String> readStrings(String json) {
        return readList(json, STRING_LIST);
    }
}
