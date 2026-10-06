package com.anjoguarda.notes;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
class NoteTest {

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
        if (tableExists("audit_logs")) {
            jdbcTemplate.update("DELETE FROM audit_logs");
        }
        if (tableExists("daily_notes")) {
            jdbcTemplate.update("DELETE FROM daily_notes");
        }
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
    void writesOneActiveObservationPerChildAndDate() {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");

        ResponseEntity<String> blank = post(ana, lia, "2026-03-03", "ESCOLA", "  ");
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> unknownCategory = post(ana, lia, "2026-03-03", "INVALIDA", "Reuniao");
        assertThat(unknownCategory.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> created = postOk(ana, lia, "2026-03-03", "ESCOLA", "Reuniao da escola");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String noteId = (String) created.getBody().get("id");
        assertThat(created.getBody()).containsEntry("childId", lia);
        assertThat(created.getBody()).containsEntry("date", "2026-03-03");
        assertThat(created.getBody()).containsEntry("category", "ESCOLA");
        assertThat(created.getBody()).containsEntry("text", "Reuniao da escola");

        List<Map<String, Object>> listed = list(ana, lia, "2026-03-01", "2026-03-31");
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0)).containsEntry("id", noteId);

        ResponseEntity<String> duplicate = post(ana, lia, "2026-03-03", "ROTINA", "Outra");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> invertedPeriod = restTemplate.exchange(
                "/api/v1/notes?childId=" + lia + "&from=2026-03-31&to=2026-03-01",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(ana)),
                String.class);
        assertThat(invertedPeriod.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> updated = restTemplate.exchange(
                "/api/v1/notes/" + noteId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("category", "SAUDE", "text", "Consulta"), bearer(ana)),
                new ParameterizedTypeReference<>() {});
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("category", "SAUDE");
        assertThat(updated.getBody()).containsEntry("text", "Consulta");
        assertThat(list(ana, lia, "2026-03-01", "2026-03-31")).hasSize(1);

        String other = login("outra@example.com");
        assertThat(post(other, lia, "2026-03-04", "ROTINA", "Oculta").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                        "/api/v1/notes?childId=" + lia + "&from=2026-03-01&to=2026-03-31",
                        HttpMethod.GET,
                        new HttpEntity<>(null, bearer(other)),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                        "/api/v1/notes/" + noteId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(Map.of("category", "ROTINA", "text", "Alheia"), bearer(other)),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                        "/api/v1/notes/" + noteId,
                        HttpMethod.DELETE,
                        new HttpEntity<>(null, bearer(other)),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/v1/notes/" + noteId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(ana)),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(list(ana, lia, "2026-03-01", "2026-03-31")).isEmpty();

        Integer stillThere = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_notes WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                UUID.fromString(noteId));
        assertThat(stillThere).isEqualTo(1);

        ResponseEntity<Map<String, Object>> again = postOk(ana, lia, "2026-03-03", "ROTINA", "Dia seguinte na mesma data");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getBody().get("id")).isNotEqualTo(noteId);
        assertThat(list(ana, lia, "2026-03-01", "2026-03-31")).hasSize(1);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_notes WHERE child_id = ? AND date = ?",
                Integer.class,
                UUID.fromString(lia),
                java.sql.Date.valueOf("2026-03-03"));
        assertThat(rows).isEqualTo(2);
        assertThat(count("NOTE_CREATED")).isEqualTo(2);
        assertThat(count("NOTE_UPDATED")).isEqualTo(1);
        assertThat(count("NOTE_DELETED")).isEqualTo(1);
    }

    @Test
    void searchesActiveObservationsByTextAndCategory() {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        postOk(ana, lia, "2026-03-03", "ESCOLA", "Reuniao da escola");
        postOk(ana, lia, "2026-03-04", "SAUDE", "Consulta");
        postOk(ana, lia, "2026-03-05", "ESCOLA", "Passeio no parque");
        postOk(ana, lia, "2026-04-01", "ESCOLA", "Reuniao fora do mes");

        assertThat(list(ana, lia, "2026-03-01", "2026-03-31")).hasSize(3);
        assertThat(search(ana, lia, "2026-03-01", "2026-03-31", "   ", null)).hasSize(3);
        assertThat(texts(search(ana, lia, "2026-03-01", "2026-03-31", "reuniao", null)))
                .containsExactly("Reuniao da escola");
        assertThat(texts(search(ana, lia, "2026-03-01", "2026-03-31", "DA ESCOLA", "ESCOLA")))
                .containsExactly("Reuniao da escola");
        assertThat(texts(search(ana, lia, "2026-03-01", "2026-03-31", null, "ESCOLA")))
                .containsExactly("Reuniao da escola", "Passeio no parque");
        assertThat(search(ana, lia, "2026-03-01", "2026-03-31", "consulta", "ESCOLA")).isEmpty();

        String noteId = (String) list(ana, lia, "2026-03-03", "2026-03-03").get(0).get("id");
        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/v1/notes/" + noteId,
                HttpMethod.DELETE,
                new HttpEntity<>(null, bearer(ana)),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(search(ana, lia, "2026-03-01", "2026-03-31", "reuniao", null)).isEmpty();

        String other = login("outra@example.com");
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/notes?childId=" + lia + "&from=2026-03-01&to=2026-03-31&q=consulta",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(other)),
                String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> badCategory = restTemplate.exchange(
                "/api/v1/notes?childId=" + lia + "&from=2026-03-01&to=2026-03-31&category=INVALIDA",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(ana)),
                String.class);
        assertThat(badCategory.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Integer count(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
    }

    private ResponseEntity<String> post(String token, String childId, String date, String category, String text) {
        return restTemplate.exchange(
                "/api/v1/notes",
                HttpMethod.POST,
                new HttpEntity<>(body(childId, date, category, text), bearer(token)),
                String.class);
    }

    private ResponseEntity<Map<String, Object>> postOk(
            String token, String childId, String date, String category, String text) {
        return restTemplate.exchange(
                "/api/v1/notes",
                HttpMethod.POST,
                new HttpEntity<>(body(childId, date, category, text), bearer(token)),
                new ParameterizedTypeReference<>() {});
    }

    private List<Map<String, Object>> list(String token, String childId, String from, String to) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/api/v1/notes?childId=" + childId + "&from=" + from + "&to=" + to,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Map<String, Object>> search(
            String token, String childId, String from, String to, String q, String category) {
        String url = "/api/v1/notes?childId=" + childId + "&from=" + from + "&to=" + to;
        if (q != null) {
            url += "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
        }
        if (category != null) {
            url += "&category=" + category;
        }
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(null, bearer(token)), new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<String> texts(List<Map<String, Object>> notes) {
        return notes.stream().map(note -> (String) note.get("text")).toList();
    }

    private Map<String, Object> body(String childId, String date, String category, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("childId", childId);
        body.put("date", date);
        body.put("category", category);
        body.put("text", text);
        return body;
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
}
