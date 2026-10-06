package com.usermanagement.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.usermanagement.notification.Channel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserLockoutTest {

    private final Instant now = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void locksAfterMaxFailedAttemptsAndUnlocksAfterDuration() {
        User user = new User("DRIVER", UserStatus.ACTIVE);
        for (int i = 0; i < 3; i++) {
            user.recordFailedLogin(3, Duration.ofMinutes(15), now);
        }

        assertThat(user.isLocked(now)).isTrue();
        assertThat(user.isLocked(now.plus(Duration.ofMinutes(16)))).isFalse();
    }

    @Test
    void verifyingAContactActivatesAPendingAccount() {
        User user = new User("DRIVER", UserStatus.PENDING_VERIFICATION);
        user.changePhone("+251911234567");

        user.markVerified(Channel.SMS);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isPhoneVerified()).isTrue();
    }

    @Test
    void changingEmailResetsVerification() {
        User user = new User("SHIPPER", UserStatus.ACTIVE);
        user.changeEmail("a@example.com");
        user.markVerified(Channel.EMAIL);

        user.changeEmail("b@example.com");

        assertThat(user.isEmailVerified()).isFalse();
    }
}
