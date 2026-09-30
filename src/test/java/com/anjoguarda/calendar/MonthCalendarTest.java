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
class MonthCalendarTest {

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
    void calculatesTheMonthInBrasiliaTimeWithoutStoringDays() {
        String ana = login("ana@example.com");
        String lia = registerChild(ana, "Lia");
        List<Map<String, Object>> liaGuardians = guardiansOf(ana, lia);
        String liaStart = (String) liaGuardians.get(0).get("id");
        String liaAlternate = (String) liaGuardians.get(1).get("id");

        ResponseEntity<Map> empty = calendar(ana, lia, 2026, 3);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody().get("timeZone")).isEqualTo("America/Sao_Paulo");
        List<Map<String, Object>> march = daysOf(empty.getBody());
        assertThat(march).hasSize(31);
        assertThat(day(march, "2026-03-01")).containsEntry("weekday", "SUNDAY").containsEntry("guardianId", null);
        assertThat(day(march, "2026-03-02")).containsEntry("weekday", "MONDAY");

        ResponseEntity<Map> leap = calendar(ana, lia, 2024, 2);
        assertThat(leap.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> february = daysOf(leap.getBody());
        assertThat(february).hasSize(29);
        assertThat(day(february, "2024-02-29")).containsEntry("weekday", "THURSDAY").containsEntry("guardianId", null);

        savePlan(ana, lia, "ALTERNATE_DAYS", "2026-03-02", "2026-03-04", Map.of(
                "start", liaStart,
                "alternate", liaAlternate));
        List<Map<String, Object>> alternating = daysOf(calendar(ana, lia, 2026, 3).getBody());
        assertThat(day(alternating, "2026-03-01")).containsEntry("guardianId", null);
        assertThat(day(alternating, "2026-03-02")).containsEntry("guardianId", liaStart);
        assertThat(day(alternating, "2026-03-03")).containsEntry("guardianId", liaAlternate);
        assertThat(day(alternating, "2026-03-04")).containsEntry("guardianId", liaStart);
        assertThat(day(alternating, "2026-03-05")).containsEntry("guardianId", null);

        String yearTurn = registerChild(ana, "Ano");
        List<Map<String, Object>> yearGuardians = guardiansOf(ana, yearTurn);
        savePlan(ana, yearTurn, "ALTERNATE_DAYS", "2025-12-31", null, Map.of(
                "start", (String) yearGuardians.get(0).get("id"),
                "alternate", (String) yearGuardians.get(1).get("id")));
        List<Map<String, Object>> january = daysOf(calendar(ana, yearTurn, 2026, 1).getBody());
        assertThat(january).hasSize(31);
        assertThat(day(january, "2026-01-01")).containsEntry("guardianId", yearGuardians.get(1).get("id"));
        assertThat(day(january, "2026-01-02")).containsEntry("guardianId", yearGuardians.get(0).get("id"));

        String weekends = registerChild(ana, "Fim");
        List<Map<String, Object>> weekendGuardians = guardiansOf(ana, weekends);
        savePlan(ana, weekends, "WEEKENDS", "2026-03-01", null, Map.of(
                "weekday", (String) weekendGuardians.get(0).get("id"),
                "weekend", (String) weekendGuardians.get(1).get("id")));
        List<Map<String, Object>> weekendMonth = daysOf(calendar(ana, weekends, 2026, 3).getBody());
        assertThat(day(weekendMonth, "2026-03-02")).containsEntry("guardianId", weekendGuardians.get(0).get("id"));
        assertThat(day(weekendMonth, "2026-03-07")).containsEntry("guardianId", weekendGuardians.get(1).get("id"));
        assertThat(day(weekendMonth, "2026-03-08")).containsEntry("guardianId", weekendGuardians.get(1).get("id"));

        String weeks = registerChild(ana, "Semana");
        List<Map<String, Object>> weekGuardians = guardiansOf(ana, weeks);
        savePlan(ana, weeks, "ALTERNATE_WEEKS", "2026-03-02", null, Map.of(
                "startWeek", (String) weekGuardians.get(0).get("id"),
                "alternateWeek", (String) weekGuardians.get(1).get("id")));
        List<Map<String, Object>> weekMonth = daysOf(calendar(ana, weeks, 2026, 3).getBody());
        assertThat(day(weekMonth, "2026-03-01")).containsEntry("guardianId", null);
        assertThat(day(weekMonth, "2026-03-02")).containsEntry("guardianId", weekGuardians.get(0).get("id"));
        assertThat(day(weekMonth, "2026-03-08")).containsEntry("guardianId", weekGuardians.get(0).get("id"));
        assertThat(day(weekMonth, "2026-03-09")).containsEntry("guardianId", weekGuardians.get(1).get("id"));

        String custom = registerChild(ana, "Custom");
        List<Map<String, Object>> customGuardians = guardiansOf(ana, custom);
        savePlan(ana, custom, "CUSTOM", "2026-03-01", null, Map.of(
                "MONDAY", (String) customGuardians.get(0).get("id"),
                "TUESDAY", (String) customGuardians.get(1).get("id"),
                "WEDNESDAY", (String) customGuardians.get(1).get("id"),
                "THURSDAY", (String) customGuardians.get(1).get("id"),
                "FRIDAY", (String) customGuardians.get(1).get("id"),
                "SATURDAY", (String) customGuardians.get(1).get("id"),
                "SUNDAY", (String) customGuardians.get(1).get("id")));
        List<Map<String, Object>> customMonth = daysOf(calendar(ana, custom, 2026, 3).getBody());
        assertThat(day(customMonth, "2026-03-02")).containsEntry("guardianId", customGuardians.get(0).get("id"));
        assertThat(day(customMonth, "2026-03-03")).containsEntry("guardianId", customGuardians.get(1).get("id"));

        ResponseEntity<String> invalidMonth = restTemplate.exchange(
                "/api/v1/children/" + lia + "/calendar?year=2026&month=13",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(ana)),
                String.class);
        assertThat(invalidMonth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String other = login("outra@example.com");
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/children/" + lia + "/calendar?year=2026&month=3",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(other)),
                String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Integer rulesBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_rules", Integer.class);
        calendar(ana, lia, 2026, 3);
        Integer rulesAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM schedule_rules", Integer.class);
        assertThat(rulesAfter).isEqualTo(rulesBefore);
        Integer calendarDays = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'calendar_days'",
                Integer.class);
        assertThat(calendarDays).isZero();
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
                new HttpEntity<>(Map.of(
                        "name", name,
                        "birthDate", "2018-05-02",
                        "guardians", List.of(
                                Map.of("name", "Ana", "relationship", "mae"),
                                Map.of("name", "Bruno", "relationship", "pai"))), bearer(token)),
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
