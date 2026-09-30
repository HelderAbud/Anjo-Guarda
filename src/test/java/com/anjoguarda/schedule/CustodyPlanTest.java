package com.anjoguarda.schedule;

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
class CustodyPlanTest {

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
    void savesRecurringRuleWithGuardianIdsAndDoesNotStoreTheMonth() {
        String ana = login("ana@example.com");
        String childId = registerChild(ana, "Lia");
        String otherChild = registerChild(ana, "Teo");
        List<Map<String, Object>> guardians = guardiansOf(ana, childId);
        String startGuardian = (String) guardians.get(0).get("id");
        String alternateGuardian = (String) guardians.get(1).get("id");
        List<Map<String, Object>> otherGuardians = guardiansOf(ana, otherChild);
        String foreignGuardian = (String) otherGuardians.get(0).get("id");
        String teoWeekend = (String) otherGuardians.get(1).get("id");

        ResponseEntity<String> endedBeforeStart = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(plan("ALTERNATE_DAYS", "2026-03-02", "2026-03-01", Map.of(
                        "start", startGuardian,
                        "alternate", alternateGuardian)), bearer(ana)),
                String.class);
        assertThat(endedBeforeStart.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> namedGuardian = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(plan("ALTERNATE_DAYS", "2026-03-02", null, Map.of(
                        "start", "Ana",
                        "alternate", alternateGuardian)), bearer(ana)),
                String.class);
        assertThat(namedGuardian.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> foreign = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(plan("WEEKENDS", "2026-03-02", null, Map.of(
                        "weekday", foreignGuardian,
                        "weekend", alternateGuardian)), bearer(ana)),
                String.class);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(plan("ALTERNATE_DAYS", "2026-03-02", null, Map.of(
                        "start", startGuardian,
                        "alternate", alternateGuardian)), bearer(ana)),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> rule = ruleOf(created.getBody());
        assertThat(rule.get("ruleType")).isEqualTo("ALTERNATE_DAYS");
        assertThat(configurationOf(rule)).containsEntry("start", startGuardian).containsEntry("alternate", alternateGuardian);

        ResponseEntity<Map> weekends = restTemplate.exchange(
                "/api/v1/children/" + otherChild + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(plan("WEEKENDS", "2026-03-02", null, Map.of(
                        "weekday", foreignGuardian,
                        "weekend", teoWeekend)), bearer(ana)),
                Map.class);
        assertThat(weekends.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ruleOf(weekends.getBody()).get("ruleType")).isEqualTo("WEEKENDS");

        ResponseEntity<Map> visible = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(ana)),
                Map.class);
        assertThat(visible.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(configurationOf(ruleOf(visible.getBody())))
                .containsEntry("start", startGuardian)
                .containsEntry("alternate", alternateGuardian);

        ResponseEntity<String> duplicate = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.POST,
                new HttpEntity<>(plan("ALTERNATE_DAYS", "2026-04-01", null, Map.of(
                        "start", startGuardian,
                        "alternate", alternateGuardian)), bearer(ana)),
                String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String other = login("outra@example.com");
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/children/" + childId + "/custody-plan",
                HttpMethod.GET,
                new HttpEntity<>(null, bearer(other)),
                String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Integer calendarDays = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'calendar_days'",
                Integer.class);
        assertThat(calendarDays).isZero();
        List<String> stored = jdbcTemplate.queryForList(
                "SELECT configuration_json::text FROM schedule_rules", String.class);
        assertThat(stored).isNotEmpty().allSatisfy(json -> assertThat(json).doesNotContain("Ana").doesNotContain("Bruno"));
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

    private Map<String, Object> plan(String ruleType, String startDate, String endDate, Map<String, String> configuration) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Convivencia");
        body.put("startDate", startDate);
        if (endDate != null) {
            body.put("endDate", endDate);
        }
        body.put("ruleType", ruleType);
        body.put("configuration", configuration);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ruleOf(Map<String, Object> body) {
        return (Map<String, Object>) body.get("rule");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> configurationOf(Map<String, Object> rule) {
        return (Map<String, String>) rule.get("configuration");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
