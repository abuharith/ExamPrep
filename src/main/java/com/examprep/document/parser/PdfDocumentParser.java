package com.examprep.document.parser;

import com.examprep.common.exception.InsufficientMaterialException;
import com.examprep.common.exception.ProcessingException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Page-aware PDF extraction. PDFBox reports text per page, so references are real pages
 * rather than invented section numbers. Scanned PDFs are detected and reported clearly.
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    private static final int MIN_USABLE_CHARACTERS = 120;

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public ExtractionResult extract(Path file, String originalFilename) {
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            int pageCount = pdf.getNumberOfPages();
            if (pageCount == 0) {
                throw new InsufficientMaterialException(
                        "This PDF contains no pages. The file may be empty or damaged.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            // One section per page: every citation then points at a real page instead of a
            // merged page range, and ChunkBuilder splits unusually long pages.
            List<ChunkBuilder.RawSection> sections = new ArrayList<>();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(pdf);
                if (pageText == null || pageText.isBlank()) {
                    continue;
                }
                sections.add(new ChunkBuilder.RawSection("p" + page, "Page " + page, pageText));
            }

            List<ExtractedChunk> chunks = ChunkBuilder.fromSections(sections);
            int characters = chunks.stream().mapToInt(chunk -> chunk.text().length()).sum();
            if (characters < MIN_USABLE_CHARACTERS) {
                throw new InsufficientMaterialException(
                        "No selectable text could be extracted from this PDF. It is most likely a scanned "
                                + "image; ExamPrep cannot read scans yet. Upload a text-based PDF or another format.");
            }
            return new ExtractionResult(chunks, pageCount, titleOf(pdf, originalFilename));
        } catch (InvalidPasswordException ex) {
            throw new ProcessingException("This PDF is password protected. Remove the password and upload it again.");
        } catch (IOException ex) {
            throw new ProcessingException("This PDF could not be read. It may be corrupted. Try re-exporting it.", ex);
        }
    }

    private static String titleOf(PDDocument pdf, String originalFilename) {
        PDDocumentOutline outline = pdf.getDocumentCatalog().getDocumentOutline();
        String title = pdf.getDocumentInformation() == null ? null : pdf.getDocumentInformation().getTitle();
        if (title != null && !title.isBlank() && title.length() < 300 && outline != null) {
            return title.trim();
        }
        if (title != null && !title.isBlank() && title.length() < 300) {
            return title.trim();
        }
        return null;
    }
}
