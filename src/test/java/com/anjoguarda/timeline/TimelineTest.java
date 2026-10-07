package com.anjoguarda.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
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
class TimelineTest {

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
        for (String table : List.of(
                "audit_logs",
                "daily_notes",
                "calendar_confirmations",
                "calendar_exceptions",
                "schedule_rules",
                "custody_plans")) {
            if (tableExists(table)) {
                jdbcTemplate.update("DELETE FROM " + table);
            }
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
    void listsThePeriodInChronologicalOrder() {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        List<Map<String, Object>> guardians = guardiansOf(ana, lia);
        String start = (String) guardians.get(0).get("id");
        String alternate = (String) guardians.get(1).get("id");
        savePlan(ana, lia, start, alternate);

        postException(ana, lia, "2026-03-03", start);
        ResponseEntity<Map<String, Object>> confirmed = restTemplate.exchange(
                "/api/v1/calendar/confirmations",
                HttpMethod.POST,
                new HttpEntity<>(confirmation(lia, "2026-03-04"), bearer(ana)),
                new ParameterizedTypeReference<>() {});
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> note = restTemplate.exchange(
                "/api/v1/notes",
                HttpMethod.POST,
                new HttpEntity<>(noteBody(lia, "2026-03-05", "ESCOLA", "Reuniao"), bearer(ana)),
                new ParameterizedTypeReference<>() {});
        assertThat(note.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String noteId = (String) note.getBody().get("id");
        restTemplate.exchange(
                "/api/v1/notes/" + noteId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("category", "SAUDE", "text", "Consulta"), bearer(ana)),
                String.class);
        restTemplate.exchange(
                "/api/v1/notes",
                HttpMethod.POST,
                new HttpEntity<>(noteBody(lia, "2026-04-02", "ROTINA", "Fora do mes"), bearer(ana)),
                String.class);

        ResponseEntity<Map<String, Object>> timeline = timeline(ana, lia, "2026-03-01", "2026-03-31", null);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> events = eventsOf(timeline.getBody());
        assertThat(datesOf(events)).isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(one(events, "EXCECAO", "2026-03-03")).containsEntry("newGuardianId", start);
        assertThat(one(events, "CONFIRMACAO", "2026-03-04")).containsEntry("status", "REALIZADO");
        assertThat(one(events, "OBSERVACAO", "2026-03-05"))
                .containsEntry("category", "SAUDE")
                .containsEntry("text", "Consulta");
        assertThat(actions(events))
                .contains("EXCEPTION_UPSERT", "CONFIRMATION_UPSERT", "NOTE_CREATED", "NOTE_UPDATED");
        assertThat(events).noneMatch(event -> "2026-04-02".equals(event.get("date")));

        assertThat(eventsOf(timeline(ana, lia, "2026-03-01", "2026-03-31", "EXCECAO").getBody()))
                .allMatch(event -> "EXCECAO".equals(event.get("type")))
                .hasSize(1);

        restTemplate.exchange(
                "/api/v1/notes/" + noteId, HttpMethod.DELETE, new HttpEntity<>(null, bearer(ana)), Void.class);
        List<Map<String, Object>> afterDelete = eventsOf(timeline(ana, lia, "2026-03-01", "2026-03-31", null).getBody());
        assertThat(afterDelete).noneMatch(event -> "OBSERVACAO".equals(event.get("type")));
        assertThat(actions(afterDelete)).contains("NOTE_DELETED");

        assertThat(timeline(ana, lia, "2026-03-31", "2026-03-01", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(timeline(ana, lia, "2026-03-01", "2026-03-31", "DOCUMENTO").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        String other = login("outra@example.com");
        assertThat(timeline(other, lia, "2026-03-01", "2026-03-31", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Integer calendarDays = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'timeline_events'",
                Integer.class);
        assertThat(calendarDays).isZero();
    }

    private ResponseEntity<Map<String, Object>> timeline(
            String token, String childId, String from, String to, String type) {
        String url = "/api/v1/children/" + childId + "/timeline?from=" + from + "&to=" + to;
        if (type != null) {
            url += "&type=" + type;
        }
        return restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(null, bearer(token)), new ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> eventsOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("events");
    }

    private List<String> datesOf(List<Map<String, Object>> events) {
        return events.stream().map(event -> (String) event.get("date")).toList();
    }

    private Map<String, Object> one(List<Map<String, Object>> events, String type, String date) {
        return events.stream()
                .filter(event -> type.equals(event.get("type")) && date.equals(event.get("date")))
                .findFirst()
                .orElseThrow();
    }

    private List<String> actions(List<Map<String, Object>> events) {
        return events.stream()
                .filter(event -> "AUDITORIA".equals(event.get("type")))
                .map(event -> (String) event.get("action"))
                .toList();
    }

    private Map<String, Object> confirmation(String childId, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("childId", childId);
        body.put("date", date);
        body.put("status", "REALIZADO");
        return body;
    }

    private Map<String, Object> noteBody(String childId, String date, String category, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("childId", childId);
        body.put("date", date);
        body.put("category", category);
        body.put("text", text);
        return body;
    }

    private void postException(String token, String childId, String date, String newGuardianId) {
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                "/api/v1/calendar/exceptions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "childId", childId, "date", date, "newGuardianId", newGuardianId, "reason", "Troca"),
                        bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void savePlan(String token, String childId, String start, String alternate) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Convivencia");
        body.put("startDate", "2026-03-02");
        body.put("ruleType", "ALTERNATE_DAYS");
        body.put("configuration", Map.of("start", start, "alternate", alternate));
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> guardiansOf(String token, String childId) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/children/" + childId,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) response.getBody().get("guardians");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
