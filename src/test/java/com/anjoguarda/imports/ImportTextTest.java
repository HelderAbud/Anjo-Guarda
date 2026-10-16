package com.anjoguarda.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ImportTextTest {

    @Test
    void readsADocxParagraph() throws IOException {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Reuniao");
            document.write(out);
            bytes = out.toByteArray();
        }

        assertThat(ImportText.extract("DOCX", bytes)).isEqualTo("Reuniao");
    }

    @Test
    void keepsAnEmptyPdf() throws IOException {
        assertThat(ImportText.extract("PDF", pdfPages(1))).isEmpty();
    }

    @Test
    void rejectsAPdfOverOneHundredPages() throws IOException {
        ResponseStatusException error = catchThrowableOfType(
                () -> ImportText.extract("PDF", pdfPages(101)), ResponseStatusException.class);

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getReason()).isEqualTo("PDF acima de 100 páginas");
    }

    private static byte[] pdfPages(int pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int page = 0; page < pages; page++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
