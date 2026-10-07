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
class PasswordResetTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetService passwordResetService;

    @BeforeEach
    void cleanUsers() {
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password_hash, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                "Ana",
                "ana@example.com",
                passwordEncoder.encode("senha-antiga"),
                "ACTIVE");
    }

    @Test
    void resetReplacesPasswordAndRevokesRefresh() {
        String refresh = login("senha-antiga");

        passwordResetService.reset("ana@example.com", "senha-nova");

        assertThat(loginStatus("senha-antiga")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus("senha-nova")).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> reused = restTemplate.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", refresh), String.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String login(String password) {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "ana@example.com", "password", password),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("refreshToken");
    }

    private HttpStatus loginStatus(String password) {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "ana@example.com", "password", password),
                String.class);
        return HttpStatus.valueOf(response.getStatusCode().value());
    }
}
