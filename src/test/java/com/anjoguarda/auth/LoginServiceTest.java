package com.anjoguarda.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class LoginServiceTest {

    private UserAccountRepository users;
    private PasswordEncoder passwordEncoder;
    private LoginService service;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new LoginService(
                users,
                mock(RefreshTokenRepository.class),
                mock(com.anjoguarda.audit.AuditLogRepository.class),
                passwordEncoder,
                mock(AccessTokenIssuer.class));
    }

    @Test
    void hashesAPasswordWhenTheUserIsMissing() {
        when(users.findByEmail("ana@example.com")).thenReturn(Optional.empty());

        ResponseStatusException error = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.login("  ana@example.com  ", "senha"), ResponseStatusException.class);

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(users).findByEmail("ana@example.com");
        verify(passwordEncoder).matches("senha", LoginService.ABSENT_PASSWORD_HASH);
    }

    @Test
    void hashesADummyPasswordWhenTheUserIsInactive() {
        UserAccount user = mock(UserAccount.class);
        when(user.getStatus()).thenReturn("INACTIVE");
        when(user.getPasswordHash()).thenReturn("hash-real");
        when(users.findByEmail("ana@example.com")).thenReturn(Optional.of(user));

        ResponseStatusException error = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.login("ana@example.com", "senha"), ResponseStatusException.class);

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(passwordEncoder).matches("senha", LoginService.ABSENT_PASSWORD_HASH);
        verify(passwordEncoder, never()).matches("senha", "hash-real");
    }
}
