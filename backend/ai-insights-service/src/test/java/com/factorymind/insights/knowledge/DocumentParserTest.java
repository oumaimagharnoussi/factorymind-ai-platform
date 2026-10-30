package com.factorymind.insights.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factorymind.insights.common.InvalidRequestException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class DocumentParserTest {

    @Test
    void frontMatterAndHeadingsDescribeTheDocument() {
        DocumentParser.Parsed parsed = DocumentParser.markdown("press.md", """
                ---
                title: HP-400 press
                kind: manual
                machines: HP-400, hp-500 ,
                ---
                Intro text.

                ## Troubleshooting

                ### Oil overheats

                1. Check the cooler.

                ## Maintenance
                Weekly: clean fins.
                """);

        assertThat(parsed.title()).isEqualTo("HP-400 press");
        assertThat(parsed.kind()).isEqualTo(DocumentKind.MANUAL);
        assertThat(parsed.machineModels()).containsExactly("hp-400", "hp-500");
        assertThat(parsed.sections()).extracting(DocumentParser.Section::heading)
                .containsExactly(null, "Troubleshooting › Oil overheats", "Maintenance");
        assertThat(parsed.sections().get(1).text()).isEqualTo("1. Check the cooler.");
    }

    @Test
    void withoutFrontMatterTheFirstHeadingIsTheTitleAndTheDocumentAppliesToEveryMachine() {
        DocumentParser.Parsed parsed = DocumentParser.markdown("loto.md", "# Lockout\r\n\r\n## Steps\r\nIsolate.\r\n");

        assertThat(parsed.title()).isEqualTo("Lockout");
        assertThat(parsed.machineModels()).isEmpty();
        assertThat(parsed.sections()).singleElement().satisfies(s -> {
            assertThat(s.heading()).isEqualTo("Steps");
            assertThat(s.text()).isEqualTo("Isolate.");
        });
    }

    @Test
    void whatThePersonTypesWinsOverTheFile() {
        DocumentParser.Parsed parsed = DocumentParser.parse("pump-notes.txt", "Seal kit: CR 10 HQQE".getBytes(StandardCharsets.UTF_8))
                .withOverrides(" CR 10 seals ", DocumentKind.PROCEDURE, List.of("CR 10"));

        assertThat(parsed.title()).isEqualTo("CR 10 seals");
        assertThat(parsed.kind()).isEqualTo(DocumentKind.PROCEDURE);
        assertThat(parsed.machineModels()).containsExactly("cr 10");
    }

    @Test
    void aPdfBecomesOneSectionPerPage() throws IOException {
        DocumentParser.Parsed parsed = DocumentParser.parse("GA37 manual.pdf",
                pdf("Drain condensate daily.", "Replace the oil separator every 4000 h."));

        assertThat(parsed.title()).isEqualTo("GA37 manual");
        assertThat(parsed.sections()).extracting(DocumentParser.Section::heading).containsExactly("Page 1", "Page 2");
        assertThat(parsed.sections()).extracting(DocumentParser.Section::page).containsExactly(1, 2);
        assertThat(parsed.sections().get(1).text()).contains("oil separator");
    }

    @Test
    void otherFilesAreRefused() {
        assertThatThrownBy(() -> DocumentParser.parse("manual.docx", new byte[] {1, 2}))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> DocumentParser.parse("broken.pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
