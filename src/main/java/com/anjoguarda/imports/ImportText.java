package com.anjoguarda.imports;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ImportText {

    static final String PDF = "application/pdf";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private ImportText() {}

    static String sourceType(String mime, byte[] bytes) {
        if (PDF.equals(mime) && startsWith(bytes, "%PDF".getBytes(StandardCharsets.US_ASCII))) {
            return "PDF";
        }
        if (DOCX.equals(mime) && startsWith(bytes, new byte[] {0x50, 0x4B})) {
            return "DOCX";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de arquivo inválido");
    }

    static String extract(String sourceType, byte[] bytes) {
        try {
            String text = "PDF".equals(sourceType) ? pdf(bytes) : docx(bytes);
            return text == null ? "" : text.trim();
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String docx(byte[] bytes) throws IOException {
        StringBuilder out = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    append(out, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            append(out, cell.getText());
                        }
                    }
                }
            }
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append('\n');
        }
        out.append(value.trim());
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
