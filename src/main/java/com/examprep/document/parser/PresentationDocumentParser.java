package com.examprep.document.parser;

import com.examprep.common.exception.InsufficientMaterialException;
import com.examprep.common.exception.ProcessingException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

/**
 * Presentation extraction with real slide references. Each slide keeps its title and body
 * so the study package can cite "Slide 4" truthfully.
 */
@Component
public class PresentationDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String extension) {
        return "pptx".equals(extension) || "ppt".equals(extension);
    }

    @Override
    public ExtractionResult extract(Path file, String originalFilename) {
        try {
            List<ChunkBuilder.RawSection> sections = "ppt".equals(extensionOf(originalFilename))
                    ? extractLegacy(file)
                    : extractOpenXml(file);
            List<ExtractedChunk> chunks = ChunkBuilder.fromSections(sections);
            if (chunks.isEmpty()) {
                throw new InsufficientMaterialException(
                        "No readable text was found in this presentation. It may contain only images.");
            }
            int slideCount = sections.size();
            return new ExtractionResult(chunks, slideCount, null);
        } catch (ProcessingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProcessingException(
                    "This presentation could not be read. It may be corrupted or password protected.", ex);
        }
    }

    private List<ChunkBuilder.RawSection> extractOpenXml(Path file) throws Exception {
        List<ChunkBuilder.RawSection> sections = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file); XMLSlideShow slides = new XMLSlideShow(in)) {
            int index = 1;
            for (XSLFSlide slide : slides.getSlides()) {
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String value = textShape.getText();
                        if (value != null && !value.isBlank()) {
                            if (textShape.getPlaceholder() == Placeholder.TITLE) {
                                text.insert(0, value.trim() + "\n\n");
                            } else {
                                text.append(value.trim()).append("\n\n");
                            }
                        }
                    }
                }
                addSlide(sections, index++, text);
            }
        }
        return sections;
    }

    private List<ChunkBuilder.RawSection> extractLegacy(Path file) throws Exception {
        List<ChunkBuilder.RawSection> sections = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file); HSLFSlideShow slides = new HSLFSlideShow(in)) {
            int index = 1;
            for (HSLFSlide slide : slides.getSlides()) {
                StringBuilder text = new StringBuilder();
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        String value = textShape.getText();
                        if (value != null && !value.isBlank()) {
                            text.append(value.trim()).append("\n\n");
                        }
                    }
                }
                addSlide(sections, index++, text);
            }
        }
        return sections;
    }

    private static void addSlide(List<ChunkBuilder.RawSection> sections, int index, StringBuilder text) {
        String content = text.toString().trim();
        if (content.isEmpty()) {
            return;
        }
        sections.add(new ChunkBuilder.RawSection("s" + index, "Slide " + index, content));
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
