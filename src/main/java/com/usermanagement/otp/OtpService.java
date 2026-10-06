package com.usermanagement.otp;

import com.usermanagement.common.crypto.SecureTokens;
import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import com.usermanagement.notification.Channel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and verifies one-time passwords.
 * <ul>
 *   <li>Codes are stored as SHA-256 hashes bound to subject, purpose and destination.</li>
 *   <li>Issuing a new code invalidates the previous one; a cooldown prevents spamming.</li>
 *   <li>Each code expires and allows a limited number of attempts.</li>
 * </ul>
 * Delivery happens after the transaction commits (see {@link OtpDeliveryListener}).
 */
@Service
public class OtpService {

    private final OtpRepository repository;
    private final OtpProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OtpService(OtpRepository repository, OtpProperties properties, ApplicationEventPublisher events,
                      Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /** Rejections (cooldown) happen before any write, so they never roll back the caller's transaction. */
    @Transactional(noRollbackFor = ApiException.class)
    public void issue(UUID subjectId, OtpPurpose purpose, Channel channel, String destination) {
        Instant now = clock.instant();
        repository.findFirstBySubjectIdAndPurposeOrderByCreatedAtDesc(subjectId, purpose)
                .filter(last -> last.getCreatedAt().plus(properties.resendCooldown()).isAfter(now))
                .ifPresent(last -> {
                    long wait = Duration.between(now, last.getCreatedAt().plus(properties.resendCooldown())).toSeconds() + 1;
                    throw new ApiException(ErrorCode.OTP_RESEND_TOO_SOON,
                            "A code was sent recently, please wait " + wait + " seconds before requesting another");
                });

        repository.invalidateActive(subjectId, purpose, now);
        String code = SecureTokens.numericCode(properties.length());
        repository.save(new OtpCode(subjectId, purpose, channel, hash(subjectId, purpose, destination, code),
                now.plus(properties.ttl())));
        events.publishEvent(new OtpIssuedEvent(purpose, channel, destination, code, properties.ttl()));
    }

    /**
     * Verifies and consumes a code. Runs in its own transaction so that a failed attempt is
     * recorded even though the caller's transaction is rolled back by the resulting exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verify(UUID subjectId, OtpPurpose purpose, String destination, String code) {
        Instant now = clock.instant();
        OtpCode otp = repository.findFirstBySubjectIdAndPurposeOrderByCreatedAtDesc(subjectId, purpose)
                .filter(candidate -> !candidate.isConsumed() && !candidate.isExpired(now))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        if (otp.getAttempts() >= properties.maxAttempts()) {
            throw new ApiException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }
        String presented = hash(subjectId, purpose, destination, code == null ? "" : code.trim());
        if (!SecureTokens.constantTimeEquals(otp.getCodeHash(), presented)) {
            otp.recordFailedAttempt();
            throw new ApiException(ErrorCode.INVALID_OTP);
        }
        otp.consume(now);
    }

    @Scheduled(cron = "${app.otp.cleanup-cron:0 17 * * * *}")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private static String hash(UUID subjectId, OtpPurpose purpose, String destination, String code) {
        return SecureTokens.sha256Hex(subjectId + ":" + purpose + ":" + destination + ":" + code);
    }
}
