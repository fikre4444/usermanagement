package com.usermanagement.otp;

import com.usermanagement.common.persistence.BaseEntity;
import com.usermanagement.notification.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A hashed one-time password. The plain code is never stored. */
@Entity
@Table(name = "otp_codes")
public class OtpCode extends BaseEntity {

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected OtpCode() {
    }

    OtpCode(UUID subjectId, OtpPurpose purpose, Channel channel, String codeHash, Instant expiresAt) {
        this.subjectId = subjectId;
        this.purpose = purpose;
        this.channel = channel;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    boolean isConsumed() {
        return consumedAt != null;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    void recordFailedAttempt() {
        attempts++;
    }

    void consume(Instant now) {
        consumedAt = now;
    }

    int getAttempts() {
        return attempts;
    }

    String getCodeHash() {
        return codeHash;
    }
}
