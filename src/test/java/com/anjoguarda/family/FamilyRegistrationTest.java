package com.anjoguarda.family;

import static org.assertj.core.api.Assertions.assertThat;

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
class FamilyRegistrationTest {

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
    void registersChildForTheLoggedInUserAndHidesItFromOthers() {
        String ana = login("ana@example.com");
        Map<String, Object> body = Map.of(
                "name", "Lia",
                "birthDate", "2018-05-02",
                "guardians", List.of(
                        Map.of("name", "Ana", "relationship", "mae"),
                        Map.of("name", "Bruno", "relationship", "pai")));

        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/children", HttpMethod.POST, new HttpEntity<>(body, bearer(ana)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String childId = (String) created.getBody().get("id");
        List<Map<String, Object>> guardians = (List<Map<String, Object>>) created.getBody().get("guardians");
        assertThat(guardians).hasSize(2);
        assertThat(guardians).allSatisfy(guardian -> assertThat(guardian.get("userId")).isNull());

        ResponseEntity<List> visible = restTemplate.exchange(
                "/api/v1/children", HttpMethod.GET, new HttpEntity<>(null, bearer(ana)), List.class);
        assertThat(visible.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(visible.getBody()).hasSize(1);

        ResponseEntity<String> own = restTemplate.exchange(
                "/api/v1/children/" + childId, HttpMethod.GET, new HttpEntity<>(null, bearer(ana)), String.class);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);

        String other = login("outra@example.com");
        ResponseEntity<String> hidden = restTemplate.exchange(
                "/api/v1/children/" + childId, HttpMethod.GET, new HttpEntity<>(null, bearer(other)), String.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<List> empty = restTemplate.exchange(
                "/api/v1/children", HttpMethod.GET, new HttpEntity<>(null, bearer(other)), List.class);
        assertThat(empty.getBody()).isEmpty();
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

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
