package com.anjoguarda.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MonthlyReportTest {

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
                "monthly_reports",
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
    void generatesANewJsonVersionWithoutReplacingThePreviousOne() {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        List<Map<String, Object>> guardians = guardiansOf(ana, lia);
        String start = (String) guardians.get(0).get("id");
        String alternate = (String) guardians.get(1).get("id");
        savePlan(ana, lia, start, alternate);
        postException(ana, lia, "2026-03-03", start);
        assertThat(postConfirmation(ana, lia, "2026-03-04", "REALIZADO", null, null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(postConfirmation(ana, lia, "2026-03-05", "ALTERADO", start, "09:00").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(postConfirmation(ana, lia, "2026-03-06", "NAO_REALIZADO", null, null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(postNote(ana, lia).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> first = generate(ana, lia, 2026, 3);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).containsEntry("version", 1);
        String firstId = (String) first.getBody().get("id");

        assertThat(versions(ana, lia)).hasSize(1);
        Integer rowsAfterList = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM monthly_reports WHERE child_id = ?", Integer.class, UUID.fromString(lia));
        assertThat(rowsAfterList).isEqualTo(1);

        ResponseEntity<Map<String, Object>> second = generate(ana, lia, 2026, 3);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).containsEntry("version", 2);
        assertThat(second.getBody().get("id")).isNotEqualTo(firstId);

        ResponseEntity<Map<String, Object>> stored = report(ana, firstId);
        assertThat(stored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stored.getBody()).containsEntry("version", 1);
        Map<String, Object> snapshot = snapshotOf(stored.getBody());
        assertThat(snapshot).containsEntry("childName", "Lia");
        assertThat(daysOf(snapshot)).hasSize(31);
        assertThat(day(snapshot, "2026-03-03")).containsEntry("exception", true).containsEntry("guardianId", start);
        assertThat(day(snapshot, "2026-03-04")).containsEntry("status", "REALIZADO");
        assertThat(day(snapshot, "2026-03-05"))
                .containsEntry("status", "ALTERADO")
                .containsEntry("realizedStartTime", "09:00:00")
                .containsEntry("realizedGuardianId", start);
        assertThat(observation(day(snapshot, "2026-03-05"))).containsEntry("text", "Reuniao");
        assertThat(day(snapshot, "2026-03-06")).containsEntry("status", "NAO_REALIZADO");
        assertThat(counts(snapshot))
                .containsEntry("PLANEJADO", 28)
                .containsEntry("REALIZADO", 1)
                .containsEntry("ALTERADO", 1)
                .containsEntry("NAO_REALIZADO", 1);
        assertThat(versions(ana, lia)).hasSize(2);

        assertThat(generate(ana, lia, 2026, 13).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String other = login("outra@example.com");
        assertThat(generate(other, lia, 2026, 3).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(report(other, firstId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'REPORT_CREATED'", Integer.class);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void exportsTheStoredVersionAsPdfWithoutChangingTheJson() throws Exception {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        List<Map<String, Object>> guardians = guardiansOf(ana, lia);
        String start = (String) guardians.get(0).get("id");
        String alternate = (String) guardians.get(1).get("id");
        savePlan(ana, lia, start, alternate);
        assertThat(postConfirmation(ana, lia, "2026-03-04", "REALIZADO", null, null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> created = generate(ana, lia, 2026, 3);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reportId = (String) created.getBody().get("id");
        String before = snapshotText(reportId);

        ResponseEntity<byte[]> pdf = pdf(ana, reportId);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(pdf.getBody(), StandardCharsets.ISO_8859_1)).startsWith("%PDF");
        String text;
        try (var document = Loader.loadPDF(pdf.getBody())) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).contains("Lia", "2026-03", "versao 1", "REALIZADO", "2026-03-04");
        assertThat(snapshotText(reportId)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM monthly_reports WHERE child_id = ?", Integer.class, UUID.fromString(lia)))
                .isEqualTo(1);

        String other = login("outra@example.com");
        assertThat(pdf(other, reportId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(pdf(ana, UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<byte[]> pdf(String token, String id) {
        return restTemplate.exchange(
                "/api/v1/reports/monthly/" + id + "/pdf",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                byte[].class);
    }

    private String snapshotText(String reportId) {
        return jdbcTemplate.queryForObject(
                "SELECT content_snapshot::text FROM monthly_reports WHERE id = ?",
                String.class,
                UUID.fromString(reportId));
    }

    private ResponseEntity<Map<String, Object>> generate(String token, String childId, int year, int month) {
        return restTemplate.exchange(
                "/api/v1/reports/monthly?childId=" + childId + "&year=" + year + "&month=" + month,
                HttpMethod.POST,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
    }

    private List<Map<String, Object>> versions(String token, String childId) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/api/v1/reports/monthly?childId=" + childId + "&year=2026&month=3",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> report(String token, String id) {
        return restTemplate.exchange(
                "/api/v1/reports/monthly/" + id,
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(token)),
                new ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshotOf(Map<String, Object> body) {
        return (Map<String, Object>) body.get("contentSnapshot");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> daysOf(Map<String, Object> snapshot) {
        return (List<Map<String, Object>>) snapshot.get("days");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> counts(Map<String, Object> snapshot) {
        return (Map<String, Object>) snapshot.get("counts");
    }

    private Map<String, Object> day(Map<String, Object> snapshot, String date) {
        return daysOf(snapshot).stream().filter(day -> date.equals(day.get("date"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> observation(Map<String, Object> day) {
        return (Map<String, Object>) day.get("observation");
    }

    private ResponseEntity<Map<String, Object>> postConfirmation(
            String token, String childId, String date, String status, String guardianId, String startTime) {
        Map<String, Object> body = new HashMap<>();
        body.put("childId", childId);
        body.put("date", date);
        body.put("status", status);
        if (guardianId != null) {
            body.put("realizedGuardianId", guardianId);
        }
        if (startTime != null) {
            body.put("realizedStartTime", startTime);
            body.put("realizedEndTime", "18:00");
        }
        return restTemplate.exchange(
                "/api/v1/calendar/confirmations",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> postNote(String token, String childId) {
        Map<String, Object> body = new HashMap<>();
        body.put("childId", childId);
        body.put("date", "2026-03-05");
        body.put("category", "ESCOLA");
        body.put("text", "Reuniao");
        return restTemplate.exchange(
                "/api/v1/notes",
                HttpMethod.POST,
                new HttpEntity<>(body, bearer(token)),
                new ParameterizedTypeReference<>() {});
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
