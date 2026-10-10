package com.anjoguarda.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class DocumentTest {

    private static final byte[] PDF = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
    private static final Path STORAGE = createStorage();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void documentsDir(DynamicPropertyRegistry registry) {
        registry.add("anjo.documents.dir", () -> STORAGE.toString());
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
        if (tableExists("documents")) {
            jdbcTemplate.update("DELETE FROM documents");
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
    void storesTheFileUnderAGeneratedKeyAndDownloadsTheSameBytes() throws IOException {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");

        ResponseEntity<Map<String, Object>> created = upload(ana, lia, "escola", "pasta/acordo.pdf", PDF, MediaType.APPLICATION_PDF);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String documentId = (String) created.getBody().get("id");
        assertThat(created.getBody()).containsEntry("childId", lia);
        assertThat(created.getBody()).containsEntry("category", "escola");
        assertThat(created.getBody()).containsEntry("filename", "acordo.pdf");
        assertThat(created.getBody()).containsEntry("mimeType", "application/pdf");
        assertThat(created.getBody()).doesNotContainKey("storageKey");
        assertThat(((Number) created.getBody().get("size")).longValue()).isEqualTo(PDF.length);

        List<Map<String, Object>> listed = list(ana, lia);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0)).containsEntry("id", documentId);
        assertThat(listed.get(0)).containsEntry("filename", "acordo.pdf");
        assertThat(listed.get(0)).doesNotContainKey("storageKey");

        ResponseEntity<byte[]> download = download(ana, documentId);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(download.getBody()).isEqualTo(PDF);

        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM documents WHERE id = ?", String.class, UUID.fromString(documentId));
        assertThat(storageKey).doesNotContain("acordo");
        assertThat(Files.readAllBytes(STORAGE.resolve(storageKey))).isEqualTo(PDF);
        assertThat(count("DOCUMENT_UPLOADED")).isEqualTo(1);

        String other = login("outra@example.com");
        assertThat(upload(other, lia, "escola", "oculto.pdf", PDF, MediaType.APPLICATION_PDF).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                        "/api/v1/documents?childId=" + lia,
                        HttpMethod.GET,
                        new HttpEntity<>(null, bearer(other)),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(download(other, documentId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(download(ana, UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsCategoryTypeAndSizeOutsideTheContract() {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        String category = "a".repeat(40);
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        assertThat(uploadStatus(ana, lia, "  ", "acordo.pdf", PDF, MediaType.APPLICATION_PDF))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadStatus(ana, lia, "a".repeat(41), "acordo.pdf", PDF, MediaType.APPLICATION_PDF))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadStatus(ana, lia, "escola", "acordo.pdf", "texto".getBytes(StandardCharsets.US_ASCII), MediaType.TEXT_PLAIN))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadStatus(ana, lia, "escola", "acordo.pdf", jpeg, MediaType.APPLICATION_PDF))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadStatus(ana, lia, "escola", "vazio.pdf", new byte[0], MediaType.APPLICATION_PDF))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(uploadStatus(ana, lia, "escola", "grande.pdf", new byte[(int) DocumentService.MAX_BYTES + 1], MediaType.APPLICATION_PDF))
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> accepted = upload(ana, lia, category, "foto.jpg", jpeg, MediaType.IMAGE_JPEG);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(accepted.getBody()).containsEntry("category", category);
        assertThat(accepted.getBody()).containsEntry("mimeType", "image/jpeg");
    }

    private ResponseEntity<Map<String, Object>> upload(
            String token, String childId, String category, String filename, byte[] bytes, MediaType mediaType) {
        return restTemplate.exchange(
                "/api/v1/documents",
                HttpMethod.POST,
                new HttpEntity<>(parts(childId, category, filename, bytes, mediaType), multipart(token)),
                new ParameterizedTypeReference<>() {});
    }

    private HttpStatus uploadStatus(
            String token, String childId, String category, String filename, byte[] bytes, MediaType mediaType) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/documents",
                HttpMethod.POST,
                new HttpEntity<>(parts(childId, category, filename, bytes, mediaType), multipart(token)),
                String.class);
        return HttpStatus.valueOf(response.getStatusCode().value());
    }

    private List<Map<String, Object>> list(String token, String childId) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/api/v1/documents?childId=" + childId,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<byte[]> download(String token, String id) {
        return restTemplate.exchange(
                "/api/v1/documents/" + id + "/download",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                byte[].class);
    }

    private MultiValueMap<String, Object> parts(
            String childId, String category, String filename, byte[] bytes, MediaType mediaType) {
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
        parts.add("category", category);
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        return parts;
    }

    private HttpHeaders multipart(String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private Integer count(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
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

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("anjo-documentos");
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
