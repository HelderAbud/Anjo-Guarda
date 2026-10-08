package com.anjoguarda.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class MonthlyReportPdf {

    private static final float LEADING = 14f;
    private static final float MARGIN = 40f;
    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private MonthlyReportPdf() {}

    static byte[] render(int version, JsonNode snapshot) {
        List<String> lines = lines(version, snapshot);
        try (PDDocument document = new PDDocument()) {
            write(document, lines);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF inválido");
        }
    }

    private static List<String> lines(int version, JsonNode snapshot) {
        List<String> lines = new ArrayList<>();
        int month = snapshot.path("month").asInt();
        lines.add(text(snapshot.path("childName").asText()));
        lines.add(snapshot.path("year").asInt() + "-" + String.format("%02d", month) + " versao " + version);
        JsonNode counts = snapshot.path("counts");
        counts.fieldNames().forEachRemaining(name -> lines.add(name + " " + counts.path(name).asInt()));
        for (JsonNode day : snapshot.path("days")) {
            StringBuilder row = new StringBuilder();
            row.append(day.path("date").asText()).append(' ');
            row.append(day.path("weekday").asText()).append(' ');
            row.append(day.path("status").asText());
            if (day.path("exception").asBoolean(false)) {
                row.append(" excecao");
            }
            if (day.hasNonNull("realizedStartTime")) {
                row.append(' ').append(day.path("realizedStartTime").asText());
                if (day.hasNonNull("realizedEndTime")) {
                    row.append('-').append(day.path("realizedEndTime").asText());
                }
            }
            if (day.hasNonNull("observation")) {
                row.append(' ').append(day.path("observation").path("text").asText());
            }
            lines.add(text(row.toString()));
        }
        return lines;
    }

    private static void write(PDDocument document, List<String> lines) throws IOException {
        PDPage page = newPage(document);
        PDPageContentStream stream = new PDPageContentStream(document, page);
        float y = page.getMediaBox().getHeight() - MARGIN;
        stream.beginText();
        stream.setFont(FONT, 11);
        stream.newLineAtOffset(MARGIN, y);
        stream.setLeading(LEADING);
        for (String line : lines) {
            if (y < MARGIN + LEADING) {
                stream.endText();
                stream.close();
                page = newPage(document);
                y = page.getMediaBox().getHeight() - MARGIN;
                stream = new PDPageContentStream(document, page);
                stream.beginText();
                stream.setFont(FONT, 11);
                stream.newLineAtOffset(MARGIN, y);
                stream.setLeading(LEADING);
            }
            stream.showText(line);
            stream.newLine();
            y -= LEADING;
        }
        stream.endText();
        stream.close();
    }

    private static PDPage newPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return page;
    }

    private static String text(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int code = value.codePointAt(offset);
            offset += Character.charCount(code);
            if (code == '\n' || code == '\r' || code == '\t') {
                out.append(' ');
            } else if ((code >= 32 && code <= 126) || (code >= 160 && code <= 255)) {
                out.append((char) code);
            } else {
                out.append('?');
            }
        }
        return out.toString();
    }
}
