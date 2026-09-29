package com.anjoguarda.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RefreshSessionTest {

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
    void cleanUsers() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password_hash, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                "Ana",
                "ana@example.com",
                passwordEncoder.encode("senha-certa"),
                "ACTIVE");
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheChain() {
        String firstRefresh = login();

        ResponseEntity<Map> rotated = restTemplate.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh), Map.class);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secondRefresh = (String) rotated.getBody().get("refreshToken");
        assertThat(secondRefresh).isNotBlank().isNotEqualTo(firstRefresh);

        ResponseEntity<String> reused = restTemplate.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh), String.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> successor = restTemplate.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", secondRefresh), String.class);
        assertThat(successor.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesRefresh() {
        String refresh = login();

        ResponseEntity<Void> logout = restTemplate.postForEntity(
                "/api/v1/auth/logout", Map.of("refreshToken", refresh), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> refreshed = restTemplate.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", refresh), String.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String login() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "ana@example.com", "password", "senha-certa"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("refreshToken");
    }
}
