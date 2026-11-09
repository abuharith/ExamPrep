package com.examprep.document.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The parsers share one extension-matching implementation and are used polymorphically. */
class AbstractDocumentParserTest {

    private final List<DocumentParser> parsers = List.of(
            new PdfDocumentParser(),
            new PresentationDocumentParser(),
            new WordDocumentParser(),
            new StructuredTextParser());

    @Test
    void baseClassIsAbstractAndEveryParserInheritsFromIt() {
        assertThat(Modifier.isAbstract(AbstractDocumentParser.class.getModifiers())).isTrue();
        assertThat(parsers).allSatisfy(parser -> assertThat(parser).isInstanceOf(AbstractDocumentParser.class));
    }

    @Test
    void everySupportedExtensionIsHandledByExactlyOneParser() {
        List<String> extensions = List.of(
                "pdf", "docx", "doc", "pptx", "ppt", "txt", "md", "markdown", "rtf", "odt", "odp");

        for (String extension : extensions) {
            long matches = parsers.stream().filter(parser -> parser.supports(extension)).count();
            assertThat(matches).as("parsers handling ." + extension).isEqualTo(1);
        }
    }

    @Test
    void matchingIsCaseInsensitiveAndRejectsUnknownOrMissingExtensions() {
        PdfDocumentParser pdf = new PdfDocumentParser();

        assertThat(pdf.supports("PDF")).isTrue();
        assertThat(pdf.supports("docx")).isFalse();
        assertThat(pdf.supports(null)).isFalse();
        assertThat(parsers).noneMatch(parser -> parser.supports("exe"));
    }

    @Test
    void parsersDeclareTheirExtensionsThroughTheVarargsConstructor() {
        assertThat(new PresentationDocumentParser().supportedExtensions()).containsExactlyInAnyOrder("pptx", "ppt");
        assertThat(new WordDocumentParser().supportedExtensions()).containsExactlyInAnyOrder("docx", "doc");
    }
}
