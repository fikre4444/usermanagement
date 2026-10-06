package com.usermanagement.otp;

import com.usermanagement.notification.Channel;
import java.time.Duration;

/**
 * In-memory event carrying a freshly generated code to the delivery listener. It is deliberately
 * not a domain event, so the plain code is never written to the outbox.
 */
public record OtpIssuedEvent(OtpPurpose purpose, Channel channel, String destination, String code, Duration ttl) {

    @Override
    public String toString() {
        return "OtpIssuedEvent[purpose=" + purpose + ", channel=" + channel + ", destination=" + destination + "]";
    }
}
