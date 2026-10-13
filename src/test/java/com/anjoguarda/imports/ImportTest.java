package com.anjoguarda.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ImportTest {

    private static final MediaType PDF = MediaType.APPLICATION_PDF;
    private static final MediaType DOCX = MediaType.parseMediaType(ImportText.DOCX);
    private static final Path STORAGE = createStorage();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void importsDir(DynamicPropertyRegistry registry) {
        registry.add("anjo.imports.dir", () -> STORAGE.toString());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() throws IOException {
        if (tableExists("audit_logs")) {
            jdbcTemplate.update("DELETE FROM audit_logs");
        }
        if (tableExists("import_items")) {
            jdbcTemplate.update("DELETE FROM import_items");
        }
        if (tableExists("imports")) {
            jdbcTemplate.update("DELETE FROM imports");
        }
        jdbcTemplate.update("DELETE FROM daily_notes");
        jdbcTemplate.update("DELETE FROM calendar_confirmations");
        jdbcTemplate.update("DELETE FROM calendar_exceptions");
        jdbcTemplate.update("DELETE FROM schedule_rules");
        jdbcTemplate.update("DELETE FROM custody_plans");
        jdbcTemplate.update("DELETE FROM guardians");
        jdbcTemplate.update("DELETE FROM children");
        jdbcTemplate.update("DELETE FROM family_access");
        jdbcTemplate.update("DELETE FROM families");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
        if (Files.exists(STORAGE)) {
            try (var files = Files.list(STORAGE)) {
                files.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException error) {
                        throw new IllegalStateException(error);
                    }
                });
            }
        }
        insertUser("ana@example.com");
        insertUser("outra@example.com");
    }

    @Test
    void storesTheOriginalPdfAndExtractsTextWithoutTouchingTheCalendar() throws IOException {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        byte[] pdf = pdfWithText("Visita");

        ResponseEntity<Map<String, Object>> created = upload(ana, lia, "pasta/visita.pdf", pdf, PDF);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String importId = (String) created.getBody().get("id");
        assertThat(created.getBody()).containsEntry("childId", lia);
        assertThat(created.getBody()).containsEntry("filename", "visita.pdf");
        assertThat(created.getBody()).containsEntry("sourceType", "PDF");
        assertThat(created.getBody()).containsEntry("status", "EXTRAIDO");
        assertThat(created.getBody().get("extractedText").toString()).contains("Visita");
        assertThat(created.getBody()).doesNotContainKey("storageKey");
        assertThat(created.getBody().get("createdAt")).isNotNull();

        ResponseEntity<Map<String, Object>> loaded = restTemplate.exchange(
                "/api/v1/imports/" + importId,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(ana)),
                new ParameterizedTypeReference<>() {});
        assertThat(loaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loaded.getBody()).containsEntry("status", "EXTRAIDO");
        assertThat(loaded.getBody().get("extractedText").toString()).contains("Visita");

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM imports WHERE id = ?", String.class, UUID.fromString(importId));
        assertThat(storageKey).doesNotContain("visita");
        assertThat(Files.readAllBytes(STORAGE.resolve(storageKey))).isEqualTo(pdf);
        assertThat(count("calendar_exceptions")).isZero();
        assertThat(count("calendar_confirmations")).isZero();
        assertThat(count("daily_notes")).isZero();

        String other = login("outra@example.com");
        assertThat(upload(other, lia, "oculto.pdf", pdf, PDF).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                        "/api/v1/imports/" + importId,
                        HttpMethod.GET,
                        new HttpEntity<>(null, bearer(other)),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                        "/api/v1/imports/" + UUID.randomUUID(),
                        HttpMethod.GET,
                        new HttpEntity<>(null, bearer(ana)),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void extractsDocxParagraphsAndTablesAndRejectsOtherFiles() throws IOException {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");

        ResponseEntity<Map<String, Object>> docx = upload(ana, lia, "relatorio.docx", docxWithTable(), DOCX);
        assertThat(docx.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(docx.getBody()).containsEntry("sourceType", "DOCX");
        assertThat(docx.getBody()).containsEntry("filename", "relatorio.docx");
        assertThat(docx.getBody().get("extractedText").toString()).contains("Visita").contains("Escola");

        ResponseEntity<Map<String, Object>> blank = upload(ana, lia, "scan.pdf", blankPdf(), PDF);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(blank.getBody()).containsEntry("status", "EXTRAIDO");
        assertThat(blank.getBody()).containsEntry("extractedText", "");

        assertThat(uploadStatus(ana, lia, "nota.txt", "texto".getBytes(StandardCharsets.US_ASCII), MediaType.TEXT_PLAIN))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadStatus(ana, lia, "falso.pdf", new byte[] {0x00, 0x01}, PDF))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadStatus(ana, lia, "vazio.pdf", new byte[0], PDF)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadStatus(ana, lia, "grande.pdf", new byte[(int) ImportService.MAX_BYTES + 1], PDF))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count("calendar_confirmations")).isZero();
        assertThat(count("daily_notes")).isZero();
        assertThat(count("calendar_exceptions")).isZero();
    }

    @Test
    void keepsTheMarkedObservationOutOfTheCalendarUntilItIsApproved() throws IOException {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        byte[] docx = docxLines("texto solto", "OBSERVACAO 2026-03-04 ESCOLA Reuniao da escola");

        ResponseEntity<Map<String, Object>> created = upload(ana, lia, "revisao.docx", docx, DOCX);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String importId = (String) created.getBody().get("id");
        assertThat(created.getBody().get("extractedText").toString()).contains("texto solto");
        Map<String, Object> item = onlyItem(created.getBody());
        assertThat(item).containsEntry("type", "OBSERVACAO");
        assertThat(item).containsEntry("confidence", 1);
        assertThat(item).containsEntry("conflict", false);
        assertThat(item.get("approved")).isNull();
        assertThat(item.get("resultingEntityId")).isNull();
        assertThat(proposed(item)).containsEntry("date", "2026-03-04");
        assertThat(proposed(item)).containsEntry("category", "ESCOLA");
        assertThat(proposed(item)).containsEntry("text", "Reuniao da escola");
        assertThat(notes(ana, lia)).isEmpty();

        String itemId = (String) item.get("id");
        ResponseEntity<Map<String, Object>> approved = approve(ana, importId, List.of(Map.of("itemId", itemId, "approved", true)));
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody()).containsEntry("status", "REVISADO");
        Map<String, Object> decided = onlyItem(approved.getBody());
        assertThat(decided).containsEntry("approved", true);
        assertThat(decided).containsEntry("resultingEntityType", "daily_note");

        List<Map<String, Object>> written = notes(ana, lia);
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).containsEntry("text", "Reuniao da escola");
        assertThat(decided).containsEntry("resultingEntityId", written.get(0).get("id"));
        assertThat(countWhere("IMPORT_APPROVED")).isEqualTo(1);
        assertThat(countWhere("NOTE_CREATED")).isEqualTo(1);

        assertThat(approve(ana, importId, List.of(Map.of("itemId", itemId, "approved", true))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        String other = login("outra@example.com");
        assertThat(approve(other, importId, List.of(Map.of("itemId", itemId, "approved", false))).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(approve(ana, UUID.randomUUID().toString(), List.of()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resolvesAConflictBeforeWritingAndRejectsMergeOfAConfirmation() throws IOException {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        postNote(ana, lia, "2026-03-04", "ESCOLA", "Original");

        ResponseEntity<Map<String, Object>> imported = upload(
                ana, lia, "conflito.docx", docxLines("OBSERVACAO 2026-03-04 SAUDE Texto novo"), DOCX);
        String importId = (String) imported.getBody().get("id");
        Map<String, Object> item = onlyItem(imported.getBody());
        assertThat(item).containsEntry("conflict", true);
        String itemId = (String) item.get("id");

        assertThat(approve(ana, importId, List.of(Map.of("itemId", itemId, "approved", true))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notesOn(ana, lia, "2026-03-04").get(0)).containsEntry("text", "Original");
        assertThat(notesOn(ana, lia, "2026-03-04").get(0)).containsEntry("category", "ESCOLA");

        ResponseEntity<Map<String, Object>> kept = approve(
                ana, importId, List.of(Map.of("itemId", itemId, "approved", true, "resolution", "MANTER")));
        assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(onlyItem(kept.getBody())).containsEntry("resultingEntityId", notesOn(ana, lia, "2026-03-04").get(0).get("id"));
        assertThat(notesOn(ana, lia, "2026-03-04").get(0)).containsEntry("text", "Original");

        ResponseEntity<Map<String, Object>> replacement = upload(
                ana, lia, "troca.docx", docxLines("OBSERVACAO 2026-03-04 SAUDE Trocada"), DOCX);
        String replacementId = (String) replacement.getBody().get("id");
        String replacementItem = (String) onlyItem(replacement.getBody()).get("id");
        ResponseEntity<Map<String, Object>> replaced = approve(
                ana,
                replacementId,
                List.of(Map.of("itemId", replacementItem, "approved", true, "resolution", "SUBSTITUIR")));
        assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notesOn(ana, lia, "2026-03-04").get(0)).containsEntry("category", "SAUDE");
        assertThat(notesOn(ana, lia, "2026-03-04").get(0)).containsEntry("text", "Trocada");

        ResponseEntity<Map<String, Object>> merge = upload(
                ana, lia, "mescla.docx", docxLines("OBSERVACAO 2026-03-04 ESCOLA Extra"), DOCX);
        String mergeId = (String) merge.getBody().get("id");
        String mergeItem = (String) onlyItem(merge.getBody()).get("id");
        ResponseEntity<Map<String, Object>> merged = approve(
                ana, mergeId, List.of(Map.of("itemId", mergeItem, "approved", true, "resolution", "MESCLAR")));
        assertThat(merged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notesOn(ana, lia, "2026-03-04").get(0)).containsEntry("category", "SAUDE");
        assertThat(notesOn(ana, lia, "2026-03-04").get(0)).containsEntry("text", "Trocada\nExtra");

        ResponseEntity<Map<String, Object>> rejectedImport = upload(
                ana, lia, "rejeita.docx", docxLines("OBSERVACAO 2026-03-05 ROTINA Nada"), DOCX);
        String rejectedId = (String) rejectedImport.getBody().get("id");
        String rejectedItem = (String) onlyItem(rejectedImport.getBody()).get("id");
        ResponseEntity<Map<String, Object>> rejected = approve(
                ana, rejectedId, List.of(Map.of("itemId", rejectedItem, "approved", false)));
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(onlyItem(rejected.getBody()).get("resultingEntityId")).isNull();
        assertThat(notesOn(ana, lia, "2026-03-05")).isEmpty();

        List<Map<String, Object>> guardians = guardiansOf(ana, lia);
        savePlan(ana, lia, (String) guardians.get(0).get("id"), (String) guardians.get(1).get("id"));
        assertThat(postConfirmation(ana, lia, "2026-03-02", "REALIZADO").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map<String, Object>> confirmationImport = upload(
                ana, lia, "confirma.docx", docxLines("CONFIRMACAO 2026-03-02 REALIZADO"), DOCX);
        String confirmationId = (String) confirmationImport.getBody().get("id");
        Map<String, Object> confirmationItem = onlyItem(confirmationImport.getBody());
        assertThat(confirmationItem).containsEntry("conflict", true);
        assertThat(approve(
                        ana,
                        confirmationId,
                        List.of(Map.of("itemId", confirmationItem.get("id"), "approved", true, "resolution", "MESCLAR")))
                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpStatus uploadStatus(String token, String childId, String filename, byte[] bytes, MediaType mediaType) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/imports",
                HttpMethod.POST,
                new HttpEntity<>(parts(childId, filename, bytes, mediaType), multipart(token)),
                String.class);
        return HttpStatus.valueOf(response.getStatusCode().value());
    }

    private Map<String, Object> onlyItem(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(1);
        return items.get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> proposed(Map<String, Object> item) {
        return (Map<String, Object>) item.get("proposedData");
    }

    private ResponseEntity<Map<String, Object>> approve(String token, String importId, List<Map<String, Object>> decisions) {
        return restTemplate.exchange(
                "/api/v1/imports/" + importId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("decisions", decisions), bearer(token)),
                new ParameterizedTypeReference<>() {});
    }

    private List<Map<String, Object>> notes(String token, String childId) {
        return notesOn(token, childId, "2026-03-04");
    }

    private List<Map<String, Object>> notesOn(String token, String childId, String date) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/api/v1/notes?childId=" + childId + "&from=" + date + "&to=" + date,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void postNote(String token, String childId, String date, String category, String text) {
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                "/api/v1/notes",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("childId", childId, "date", date, "category", category, "text", text), bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private List<Map<String, Object>> guardiansOf(String token, String childId) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/children/" + childId,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> guardians = (List<Map<String, Object>>) response.getBody().get("guardians");
        return guardians;
    }

    private void savePlan(String token, String childId, String start, String alternate) {
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "name", "Convivencia",
                        "startDate", "2026-03-02",
                        "ruleType", "ALTERNATE_DAYS",
                        "configuration", Map.of("start", start, "alternate", alternate)), bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<Map<String, Object>> postConfirmation(
            String token, String childId, String date, String status) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("childId", childId);
        body.put("date", date);
        body.put("status", status);
        return restTemplate.exchange(
                "/api/v1/calendar/confirmations",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                new ParameterizedTypeReference<>() {});
    }

    private Integer countWhere(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
    }

    private ResponseEntity<Map<String, Object>> upload(
            String token, String childId, String filename, byte[] bytes, MediaType mediaType) {
        return restTemplate.exchange(
                "/api/v1/imports",
                HttpMethod.POST,
                new HttpEntity<>(parts(childId, filename, bytes, mediaType), multipart(token)),
                new ParameterizedTypeReference<>() {});
    }

    private MultiValueMap<String, Object> parts(String childId, String filename, byte[] bytes, MediaType mediaType) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mediaType);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("childId", childId);
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        return parts;
    }

    private HttpHeaders multipart(String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class,
                table);
        return count != null && count > 0;
    }

    private void insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password_hash, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                email,
                email,
                passwordEncoder.encode("senha-certa"),
                "ACTIVE");
    }

    private String login(String email) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", "senha-certa")),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private String registerChild(String token, String name) {
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                "/api/v1/children",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "name", name,
                        "birthDate", "2018-05-02",
                        "guardians", List.of(
                                Map.of("name", "Ana", "relationship", "mae"),
                                Map.of("name", "Bruno", "relationship", "pai"))), bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) created.getBody().get("id");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docxLines(String... lines) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            for (String line : lines) {
                document.createParagraph().createRun().setText(line);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] docxWithTable() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Visita");
            document.createTable(1, 1).getRow(0).getCell(0).setText("Escola");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("anjo-importacao");
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
