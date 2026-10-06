package com.usermanagement.auth;

import com.usermanagement.audit.AuditLog;
import com.usermanagement.audit.AuditOutcome;
import com.usermanagement.common.crypto.SecureTokens;
import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opaque refresh tokens with rotation and re-use detection. */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final AuditLog auditLog;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, AuditLog auditLog,
                               Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** Starts a new session (token family) for the user. */
    @Transactional
    public IssuedToken issue(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    /**
     * Exchanges a valid refresh token for a new one in the same family. Presenting a token that was
     * already rotated means it was stolen or replayed, so the whole family is revoked.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = find(rawToken).orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        if (token.isRevoked()) {
            log.warn("Revoked refresh token presented for user {}; revoking its session", token.getUserId());
            repository.revokeFamily(token.getFamilyId(), now);
            auditLog.record("security.refresh-token-reuse", AuditOutcome.DENIED, ErrorCode.INVALID_TOKEN.name(),
                    AuditLog.target("user", token.getUserId()), Map.of("sessionId", token.getFamilyId().toString()));
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        if (token.isExpired(now)) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        token.revoke(now);
        return new Rotation(token.getUserId(), issue(token.getUserId(), token.getFamilyId()));
    }

    /** Ends the session the token belongs to. Unknown tokens are ignored. */
    @Transactional
    public void revokeSession(String rawToken) {
        find(rawToken).ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Ends every session of the user ("sign out everywhere"). */
    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    /** Revoked tokens are kept until they expire so that re-use can still be detected. */
    @Scheduled(cron = "${app.jwt.cleanup-cron:0 47 * * * *}")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private IssuedToken issue(UUID userId, UUID familyId) {
        String raw = SecureTokens.randomUrlSafe(32);
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        repository.save(new RefreshToken(userId, SecureTokens.sha256Hex(raw), familyId, expiresAt));
        return new IssuedToken(raw, expiresAt);
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(SecureTokens.sha256Hex(rawToken.trim()));
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record Rotation(UUID userId, IssuedToken next) {
    }
}
