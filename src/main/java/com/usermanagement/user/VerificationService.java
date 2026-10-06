package com.usermanagement.user;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import com.usermanagement.notification.Channel;
import com.usermanagement.otp.OtpPurpose;
import com.usermanagement.otp.OtpService;
import com.usermanagement.user.event.UserVerifiedEvent;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Proves that a user owns their email address or phone number using one-time codes. */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final UserLookup lookup;
    private final OtpService otpService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public VerificationService(UserLookup lookup, OtpService otpService, ApplicationEventPublisher events,
                               Clock clock) {
        this.lookup = lookup;
        this.otpService = otpService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Sends a verification code to the email or phone given as identifier. Silently does nothing
     * if no such user exists or the contact is already verified, so the endpoint cannot be used to
     * discover accounts.
     */
    @Transactional
    public void requestCode(String identifier) {
        Identifiers.Kind kind = Identifiers.kindOf(identifier);
        if (kind == Identifiers.Kind.USERNAME) {
            throw ApiException.validation("identifier", "must be an email address or a phone number");
        }
        lookup.findByIdentifier(identifier)
                .filter(user -> !user.isDeleted() && user.getStatus() != UserStatus.SUSPENDED)
                .ifPresent(user -> user.contactFor(kind)
                        .filter(contact -> !isVerified(user, contact.channel()))
                        .ifPresent(contact -> sendCode(user, contact)));
    }

    /** Sends a code for the given contact of the user. */
    @Transactional
    public void sendCode(User user, Contact contact) {
        otpService.issue(user.getId(), purposeFor(contact.channel()), contact.channel(), contact.destination());
    }

    /** Like {@link #sendCode} but never fails, e.g. when a code was sent moments ago (cooldown). */
    @Transactional
    public void trySendCode(User user, Contact contact) {
        try {
            sendCode(user, contact);
        } catch (ApiException ex) {
            log.debug("Verification code not sent to user {}: {}", user.getId(), ex.getMessage());
        }
    }

    @Transactional
    public User confirm(String identifier, String code) {
        Identifiers.Kind kind = Identifiers.kindOf(identifier);
        User user = lookup.findByIdentifier(identifier)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        Contact contact = user.contactFor(kind).filter(c -> kind != Identifiers.Kind.USERNAME)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        otpService.verify(user.getId(), purposeFor(contact.channel()), contact.destination(), code);
        markVerified(user, contact.channel());
        return user;
    }

    /** Marks a contact verified (after any successful proof of possession) and publishes the event. */
    @Transactional
    public void markVerified(User user, Channel channel) {
        if (!isVerified(user, channel) || user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.markVerified(channel);
            events.publishEvent(new UserVerifiedEvent(user.getId(), channel, clock.instant()));
        }
    }

    private static OtpPurpose purposeFor(Channel channel) {
        return channel == Channel.EMAIL ? OtpPurpose.EMAIL_VERIFICATION : OtpPurpose.PHONE_VERIFICATION;
    }

    private static boolean isVerified(User user, Channel channel) {
        return channel == Channel.EMAIL ? user.isEmailVerified() : user.isPhoneVerified();
    }
}
