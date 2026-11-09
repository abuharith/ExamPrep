package com.examprep.api.document;

/**
 * One citable unit of extracted text.
 *
 * @param referenceId stable id supplied to Gemini (for example {@code p12} or {@code s3})
 * @param location    human-readable origin, for example "Page 12" or "Slide 3"
 * @param text        extracted study material
 */
public record DocumentChunkDto(String referenceId, String location, String text) {
}
