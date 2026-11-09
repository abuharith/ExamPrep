package com.examprep.document.parser;

/** One citable piece of extracted text with an honest location label. */
public record ExtractedChunk(String referenceId, String location, String text) {
}
