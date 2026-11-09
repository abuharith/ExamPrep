package com.examprep.document.parser;

import com.examprep.common.exception.InsufficientMaterialException;
import com.examprep.common.exception.ProcessingException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.WriteOutContentHandler;
import org.springframework.stereotype.Component;

/**
 * Text, Markdown, RTF and OpenDocument extraction. These formats do not expose dependable
 * page boundaries, so chunks are cited by part number rather than a fabricated page.
 */
@Component
public class StructuredTextParser implements DocumentParser {

    private static final int MAX_EXTRACTED_CHARS = 4_000_000;

    @Override
    public boolean supports(String extension) {
        return switch (extension) {
            case "txt", "md", "markdown", "rtf", "odt", "odp" -> true;
            default -> false;
        };
    }

    @Override
    public ExtractionResult extract(Path file, String originalFilename) {
        String extension = extensionOf(originalFilename);
        try {
            List<ChunkBuilder.RawSection> sections = switch (extension) {
                case "txt", "md", "markdown" -> plainTextSections(file, "md".equals(extension)
                        || "markdown".equals(extension));
                case "rtf", "odt", "odp" -> tikaSections(file);
                default -> throw new ProcessingException("Unsupported document format.");
            };
            List<ExtractedChunk> chunks = ChunkBuilder.fromSections(sections);
            if (chunks.isEmpty()) {
                throw new InsufficientMaterialException(
                        "No readable text was found in this document. Check that the file is not empty.");
            }
            int pages = "odp".equals(extension) ? sections.size() : 0;
            return new ExtractionResult(chunks, pages, null);
        } catch (ProcessingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProcessingException("This document could not be read. It may be corrupted or unsupported.", ex);
        }
    }

    private List<ChunkBuilder.RawSection> plainTextSections(Path file, boolean markdown) throws Exception {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (text.length() > MAX_EXTRACTED_CHARS) {
            text = text.substring(0, MAX_EXTRACTED_CHARS);
        }
        if (!markdown) {
            return List.of(new ChunkBuilder.RawSection("c1", "Document text", text));
        }
        List<ChunkBuilder.RawSection> sections = new ArrayList<>();
        String heading = null;
        StringBuilder body = new StringBuilder();
        int index = 0;
        for (String line : text.split("\n", -1)) {
            if (line.matches("^#{1,4}\\s+.*")) {
                index = flush(sections, heading, body, index);
                heading = line.replaceFirst("^#{1,4}\\s+", "").trim();
                body.append(line.trim()).append("\n\n");
            } else {
                body.append(line).append('\n');
            }
        }
        flush(sections, heading, body, index);
        return sections;
    }

    private List<ChunkBuilder.RawSection> tikaSections(Path file) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        WriteOutContentHandler handler = new WriteOutContentHandler(writer, MAX_EXTRACTED_CHARS);
        try (InputStream in = Files.newInputStream(file)) {
            parser.parse(in, handler, metadata, new ParseContext());
        }
        String text = ChunkBuilder.normalise(writer.toString());
        List<ChunkBuilder.RawSection> sections = new ArrayList<>();
        int index = 0;
        StringBuilder body = new StringBuilder();
        for (String paragraph : text.split("\n\n")) {
            body.append(paragraph).append("\n\n");
            if (body.length() >= ChunkBuilder.MAX_CHUNK_CHARS) {
                index++;
                sections.add(new ChunkBuilder.RawSection("c" + index, "Part " + index, body.toString()));
                body.setLength(0);
            }
        }
        if (body.length() > 0) {
            index++;
            sections.add(new ChunkBuilder.RawSection("c" + index, "Part " + index, body.toString()));
        }
        return sections;
    }

    private static int flush(List<ChunkBuilder.RawSection> sections, String heading, StringBuilder body, int index) {
        if (body.length() == 0) {
            return index;
        }
        int number = index + 1;
        String location = heading == null || heading.isBlank() ? "Part " + number : "Section: " + heading;
        sections.add(new ChunkBuilder.RawSection("c" + number, location, body.toString()));
        body.setLength(0);
        return number;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
