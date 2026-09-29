package com.anjoguarda.auth;

import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
            UserAccountRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void reset(String email, String newPassword) {
        UserAccount user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        user.replacePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeActiveByUserId(user.getId(), Instant.now());
    }
}
