package com.examprep.document.parser;

import com.examprep.common.exception.InsufficientMaterialException;
import com.examprep.common.exception.ProcessingException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * Word extraction that keeps heading structure. Word documents do not carry reliable page
 * numbers, so references name the section heading instead of inventing a page.
 */
@Component
public class WordDocumentParser extends AbstractDocumentParser {

    public WordDocumentParser() {
        super("docx", "doc");
    }

    @Override
    public ExtractionResult extract(Path file, String originalFilename) {
        try {
            List<ChunkBuilder.RawSection> sections = "doc".equals(extensionOf(originalFilename))
                    ? extractLegacy(file)
                    : extractOpenXml(file);
            List<ExtractedChunk> chunks = ChunkBuilder.fromSections(sections);
            if (chunks.isEmpty()) {
                throw new InsufficientMaterialException(
                        "No readable text was found in this Word document. It may contain only images.");
            }
            return new ExtractionResult(chunks, 0, null);
        } catch (ProcessingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProcessingException(
                    "This Word document could not be read. It may be corrupted or use an unsupported encoding.", ex);
        }
    }

    private List<ChunkBuilder.RawSection> extractOpenXml(Path file) throws Exception {
        List<ChunkBuilder.RawSection> sections = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file); XWPFDocument document = new XWPFDocument(in)) {
            String heading = null;
            StringBuilder body = new StringBuilder();
            int sectionNumber = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    if (isHeading(paragraph)) {
                        sectionNumber = flush(sections, heading, body, sectionNumber);
                        heading = text.trim();
                        body.append(text.trim()).append("\n\n");
                    } else {
                        body.append(text.trim()).append("\n\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    body.append(tableText(table)).append("\n\n");
                }
            }
            flush(sections, heading, body, sectionNumber);
        }
        return sections;
    }

    private static boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style != null) {
            String normalised = style.toLowerCase();
            if (normalised.startsWith("heading") || normalised.equals("title") || normalised.equals("subtitle")) {
                return true;
            }
        }
        return paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetOutlineLvl();
    }

    private List<ChunkBuilder.RawSection> extractLegacy(Path file) throws Exception {
        List<ChunkBuilder.RawSection> sections = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file); HWPFDocument document = new HWPFDocument(in)) {
            Range range = document.getRange();
            StringBuilder body = new StringBuilder();
            int sectionNumber = 0;
            for (int i = 0; i < range.numParagraphs(); i++) {
                String text = range.getParagraph(i).text();
                if (text == null) {
                    continue;
                }
                String cleaned = text.replace("\r", "\n").replace("\u0007", "").trim();
                if (cleaned.isEmpty()) {
                    continue;
                }
                body.append(cleaned).append("\n\n");
                if (body.length() >= ChunkBuilder.MAX_CHUNK_CHARS) {
                    sectionNumber = flush(sections, null, body, sectionNumber);
                }
            }
            flush(sections, null, body, sectionNumber);
        }
        return sections;
    }

    private static int flush(List<ChunkBuilder.RawSection> sections, String heading, StringBuilder body,
                             int sectionNumber) {
        if (body.length() == 0) {
            return sectionNumber;
        }
        int number = sectionNumber + 1;
        String location = heading == null || heading.isBlank() ? "Section " + number : "Section: " + heading;
        sections.add(new ChunkBuilder.RawSection("s" + number, location, body.toString()));
        body.setLength(0);
        return number;
    }

    private static String tableText(XWPFTable table) {
        StringBuilder text = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String value = cell.getText();
                if (value != null && !value.isBlank()) {
                    cells.add(value.replaceAll("\\s+", " ").trim());
                }
            }
            if (!cells.isEmpty()) {
                text.append(String.join(" | ", cells)).append('\n');
            }
        }
        return text.toString();
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
