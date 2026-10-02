package com.anjoguarda.calendar;

import static org.assertj.core.api.Assertions.assertThat;

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
class CalendarExceptionTest {

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
        if (tableExists("calendar_exceptions")) {
            jdbcTemplate.update("DELETE FROM calendar_exceptions");
        }
        if (tableExists("schedule_rules")) {
            jdbcTemplate.update("DELETE FROM schedule_rules");
        }
        if (tableExists("custody_plans")) {
            jdbcTemplate.update("DELETE FROM custody_plans");
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
    void registersExceptionOverlaysMonthWithoutDeletingRuleAndSupportsUpsert() {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        List<Map<String, Object>> guardians = guardiansOf(ana, lia);
        String start = (String) guardians.get(0).get("id");
        String alternate = (String) guardians.get(1).get("id");

        savePlan(ana, lia, "ALTERNATE_DAYS", "2026-03-02", null, Map.of("start", start, "alternate", alternate));
        Integer rulesBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_rules", Integer.class);

        List<Map<String, Object>> before = daysOf(calendar(ana, lia, 2026, 3).getBody());
        assertThat(day(before, "2026-03-03")).containsEntry("guardianId", alternate).containsEntry("exception", false);

        ResponseEntity<Map> created = postException(ana, lia, "2026-03-03", start, "Viagem");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("childId", lia);
        assertThat(created.getBody()).containsEntry("date", "2026-03-03");
        assertThat(created.getBody()).containsEntry("newGuardianId", start);
        assertThat(created.getBody()).containsEntry("originalGuardianId", alternate);
        assertThat(created.getBody()).containsEntry("reason", "Viagem");

        List<Map<String, Object>> after = daysOf(calendar(ana, lia, 2026, 3).getBody());
        assertThat(day(after, "2026-03-02")).containsEntry("guardianId", start).containsEntry("exception", false);
        assertThat(day(after, "2026-03-03")).containsEntry("guardianId", start).containsEntry("exception", true);
        assertThat(day(after, "2026-03-04")).containsEntry("guardianId", start).containsEntry("exception", false);

        Integer rulesAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_rules", Integer.class);
        assertThat(rulesAfter).isEqualTo(rulesBefore);

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'EXCEPTION_UPSERT' AND entity_type = 'calendar_exception'",
                Integer.class);
        assertThat(audits).isEqualTo(1);

        ResponseEntity<Map> updated = postException(ana, lia, "2026-03-03", alternate, "Mudou de novo");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("newGuardianId", alternate);
        assertThat(day(daysOf(calendar(ana, lia, 2026, 3).getBody()), "2026-03-03"))
                .containsEntry("guardianId", alternate)
                .containsEntry("exception", true);

        Integer exceptions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM calendar_exceptions WHERE child_id = ?",
                Integer.class,
                UUID.fromString(lia));
        assertThat(exceptions).isEqualTo(1);

        Integer auditsAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'EXCEPTION_UPSERT'", Integer.class);
        assertThat(auditsAfter).isEqualTo(2);

        String otherChild = registerChild(ana, "Outra");
        List<Map<String, Object>> foreign = guardiansOf(ana, otherChild);
        ResponseEntity<String> badGuardian = restTemplate.exchange(
                "/api/v1/calendar/exceptions",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "childId", lia,
                                "date", "2026-03-05",
                                "newGuardianId", foreign.get(0).get("id"),
                                "reason", "x"),
                        bearer(ana)),
                String.class);
        assertThat(badGuardian.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String other = login("outra@example.com");
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/calendar/exceptions",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "childId", lia,
                                "date", "2026-03-06",
                                "newGuardianId", start,
                                "reason", "x"),
                        bearer(other)),
                String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Map> postException(
            String token, String childId, String date, String newGuardianId, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("childId", childId);
        body.put("date", date);
        body.put("newGuardianId", newGuardianId);
        body.put("reason", reason);
        return restTemplate.exchange(
                "/api/v1/calendar/exceptions",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                Map.class);
    }

    private ResponseEntity<Map> calendar(String token, String childId, int year, int month) {
        return restTemplate.exchange(
                "/api/v1/children/" + childId + "/calendar?year=" + year + "&month=" + month,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                Map.class);
    }

    private void savePlan(
            String token,
            String childId,
            String ruleType,
            String startDate,
            String endDate,
            Map<String, String> configuration) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Convivencia");
        body.put("startDate", startDate);
        if (endDate != null) {
            body.put("endDate", endDate);
        }
        body.put("ruleType", ruleType);
        body.put("configuration", configuration);
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> daysOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("days");
    }

    private Map<String, Object> day(List<Map<String, Object>> days, String date) {
        return days.stream()
                .filter(day -> date.equals(day.get("date")))
                .findFirst()
                .orElseThrow();
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
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "senha-certa"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private String registerChild(String token, String name) {
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/children",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name",
                                name,
                                "birthDate",
                                "2018-05-02",
                                "guardians",
                                List.of(
                                        Map.of("name", "Ana", "relationship", "mae"),
                                        Map.of("name", "Bruno", "relationship", "pai"))),
                        bearer(token)),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) created.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> guardiansOf(String token, String childId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/children/" + childId,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) response.getBody().get("guardians");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
