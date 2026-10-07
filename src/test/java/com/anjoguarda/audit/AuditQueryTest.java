package com.anjoguarda.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuditQueryTest {

    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM guardians");
        jdbcTemplate.update("DELETE FROM children");
        jdbcTemplate.update("DELETE FROM family_access");
        jdbcTemplate.update("DELETE FROM families");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
        insertUser("ana@example.com");
        insertUser("outra@example.com");
    }

    @Test
    void recordsSuccessfulLoginAndListsOnlyTheCallerAudits() {
        ResponseEntity<String> rejected = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "ana@example.com", "password", "senha-errada"),
                String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count("LOGIN")).isZero();

        String ana = login("ana@example.com");
        String outra = login("outra@example.com");
        assertThat(count("LOGIN")).isEqualTo(2);
        String metadata = jdbcTemplate.queryForObject(
                "SELECT metadata::text FROM audit_logs WHERE action = 'LOGIN' LIMIT 1", String.class);
        assertThat(metadata).doesNotContain("senha");

        String lia = registerChild(ana, "Lia");
        ResponseEntity<Map<String, Object>> note = restTemplate.exchange(
                "/api/v1/notes",
                HttpMethod.POST,
                new HttpEntity<>(noteBody(lia), bearer(ana)),
                new ParameterizedTypeReference<>() {});
        assertThat(note.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LocalDate today = LocalDate.now(BRAZIL);
        ResponseEntity<List<Map<String, Object>>> listed = list(ana, today, today);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> actions = listed.getBody().stream().map(row -> (String) row.get("action")).toList();
        assertThat(actions).containsExactly("LOGIN", "NOTE_CREATED");
        String anaId = userId("ana@example.com");
        assertThat(listed.getBody().get(0)).containsEntry("entityType", "user").containsEntry("entityId", anaId);
        assertThat(listed.getBody()).noneMatch(row -> userId("outra@example.com").equals(row.get("entityId")));

        ResponseEntity<List<Map<String, Object>>> past = list(ana, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2));
        assertThat(past.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(past.getBody()).isEmpty();

        ResponseEntity<String> inverted = restTemplate.exchange(
                "/api/v1/audit?from=" + today + "&to=" + today.minusDays(1),
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(ana)),
                String.class);
        assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> anonymous = restTemplate.getForEntity(
                "/api/v1/audit?from=" + today + "&to=" + today, String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(outra).isNotBlank();
    }

    private ResponseEntity<List<Map<String, Object>>> list(String token, LocalDate from, LocalDate to) {
        return restTemplate.exchange(
                "/api/v1/audit?from=" + from + "&to=" + to,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
    }

    private Integer count(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
    }

    private String userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id::text FROM users WHERE email = ?", String.class, email);
    }

    private Map<String, Object> noteBody(String childId) {
        Map<String, Object> body = new HashMap<>();
        body.put("childId", childId);
        body.put("date", "2026-03-05");
        body.put("category", "ESCOLA");
        body.put("text", "Reuniao");
        return body;
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
                        "guardians", List.of(Map.of("name", "Ana", "relationship", "mae"))), bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) created.getBody().get("id");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
