package com.examprep.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public final class TestDocuments {

    private TestDocuments() {
    }

    /** Realistic page length, comfortably above the scanned-PDF detection threshold. */
    public static final String PAGE_ONE = "Cell theory states that all living organisms are made of one or "
            + "more cells. Cells are the basic structural and functional units of life. The cell membrane "
            + "controls what enters and leaves the cell, while the nucleus stores genetic information in "
            + "the form of DNA and coordinates activities such as growth and division.";

    public static final String PAGE_TWO = "Mitochondria produce ATP through aerobic cellular respiration. "
            + "Organelles are specialised structures inside the cytoplasm, each with a particular role. "
            + "Ribosomes assemble proteins, the endoplasmic reticulum transports materials, and the Golgi "
            + "apparatus modifies and packages proteins for secretion.";

    public static byte[] pdf(String... pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 760);
                    content.showText(text);
                    content.endText();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    public static byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    public static byte[] docx(String heading, String body) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph headingParagraph = document.createParagraph();
            headingParagraph.setStyle("Heading1");
            headingParagraph.createRun().setText(heading);
            XWPFParagraph bodyParagraph = document.createParagraph();
            bodyParagraph.createRun().setText(body);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            return output.toByteArray();
        }
    }

    public static byte[] pptx(String slideText) throws IOException {
        try (XMLSlideShow slides = new XMLSlideShow()) {
            XSLFSlide slide = slides.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setText(slideText);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            slides.write(output);
            return output.toByteArray();
        }
    }
}
