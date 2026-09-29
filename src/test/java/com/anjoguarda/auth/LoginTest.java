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
class LoginTest {

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
    }

    @Test
    void loginWithCorrectPasswordReturnsTokensAndStoresRefreshHash() {
        insertUser("ana@example.com", "senha-certa");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "ana@example.com", "password", "senha-certa"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = (String) response.getBody().get("accessToken");
        String refreshToken = (String) response.getBody().get("refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_tokens", String.class);
        assertThat(storedHash).isNotEqualTo(refreshToken);
        assertThat(storedHash).hasSize(64);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        insertUser("ana@example.com", "senha-certa");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "ana@example.com", "password", "senha-errada"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Integer stored = jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class);
        assertThat(stored).isZero();
    }

    private void insertUser(String email, String rawPassword) {
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, password_hash, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                "Ana",
                email,
                passwordEncoder.encode(rawPassword),
                "ACTIVE");
    }
}
