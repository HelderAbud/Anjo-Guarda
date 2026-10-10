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
        if (tableExists("imports")) {
            jdbcTemplate.update("DELETE FROM imports");
        }
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

    private HttpStatus uploadStatus(String token, String childId, String filename, byte[] bytes, MediaType mediaType) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/imports",
                HttpMethod.POST,
                new HttpEntity<>(parts(childId, filename, bytes, mediaType), multipart(token)),
                String.class);
        return HttpStatus.valueOf(response.getStatusCode().value());
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
