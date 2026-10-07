package com.anjoguarda.auth;

import com.anjoguarda.audit.AuditLog;
import com.anjoguarda.audit.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginService {

    private static final String ACTIVE = "ACTIVE";
    private static final long REFRESH_TTL_SECONDS = 60L * 60L * 24L * 14L;

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditLogRepository audits;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final SecureRandom random = new SecureRandom();

    public LoginService(
            UserAccountRepository users,
            RefreshTokenRepository refreshTokens,
            AuditLogRepository audits,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audits = audits;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        UserAccount user = users.findByEmail(email)
                .filter(account -> ACTIVE.equals(account.getStatus()))
                .filter(account -> passwordEncoder.matches(password, account.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        Instant now = Instant.now();
        String refreshToken = newRefreshToken();
        refreshTokens.save(new RefreshToken(
                UUID.randomUUID(),
                user.getId(),
                sha256(refreshToken),
                now.plusSeconds(REFRESH_TTL_SECONDS),
                now));
        audits.save(new AuditLog(UUID.randomUUID(), user.getId(), "LOGIN", "user", user.getId(), "{}"));

        return new LoginResponse(accessTokenIssuer.issue(user.getId().toString(), now), refreshToken, accessTokenIssuer.ttlSeconds());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        String hash = sha256(rawRefreshToken);
        RefreshToken current = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));

        if (current.isRevoked() || !current.getExpiresAt().isAfter(now)) {
            if (current.isRevoked()) {
                refreshTokens.revokeActiveByUserId(current.getUserId(), now);
            } else {
                refreshTokens.revokeIfActive(hash, now);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }

        if (refreshTokens.revokeIfActive(hash, now) == 0) {
            refreshTokens.revokeActiveByUserId(current.getUserId(), now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }

        String nextRefresh = newRefreshToken();
        refreshTokens.save(new RefreshToken(
                UUID.randomUUID(),
                current.getUserId(),
                sha256(nextRefresh),
                now.plusSeconds(REFRESH_TTL_SECONDS),
                now));
        return new LoginResponse(
                accessTokenIssuer.issue(current.getUserId().toString(), now),
                nextRefresh,
                accessTokenIssuer.ttlSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revokeIfActive(sha256(rawRefreshToken), Instant.now());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
