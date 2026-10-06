package com.usermanagement.auth;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import com.usermanagement.otp.OtpPurpose;
import com.usermanagement.otp.OtpService;
import com.usermanagement.user.Contact;
import com.usermanagement.user.Identifiers;
import com.usermanagement.user.PasswordPolicy;
import com.usermanagement.user.User;
import com.usermanagement.user.UserLookup;
import com.usermanagement.user.UserService;
import com.usermanagement.user.UserStatus;
import com.usermanagement.user.VerificationService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in flows: password, one-time code, token refresh, sign-out and password reset.
 * <p>
 * Methods that record failed attempts use {@code noRollbackFor = ApiException.class} so the attempt
 * counter is persisted even though an error is returned.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserLookup lookup;
    private final UserService userService;
    private final VerificationService verificationService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;
    private final OtpService otpService;
    private final AuthProperties properties;
    private final Clock clock;
    /** Compared against when the user does not exist, so response time does not reveal it. */
    private final String dummyHash;

    public AuthService(UserLookup lookup, UserService userService, VerificationService verificationService,
                       PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, TokenService tokenService,
                       RefreshTokenService refreshTokens, OtpService otpService, AuthProperties properties,
                       Clock clock) {
        this.lookup = lookup;
        this.userService = userService;
        this.verificationService = verificationService;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.otpService = otpService;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-attack-protection");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokens login(String identifier, String password) {
        Optional<User> found = lookup.findByIdentifier(identifier).filter(user -> !user.isDeleted());
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = found.get();
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(properties.maxFailedAttempts(), properties.lockDuration(), now);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        ensureCanSignIn(user);
        user.recordSuccessfulLogin(now);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokens refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
        User user = lookup.findById(rotation.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        ensureCanSignIn(user);
        TokenService.AccessToken accessToken = tokenService.issueAccessToken(user);
        return new AuthTokens(accessToken, rotation.next(), user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revokeSession(refreshToken);
    }

    // ------------------------------------------------------------------ one-time code sign-in

    /** Sends a sign-in code. Unknown identifiers are silently ignored to prevent account discovery. */
    @Transactional
    public void requestLoginCode(String identifier) {
        requireOtpLoginEnabled();
        Identifiers.Kind kind = Identifiers.kindOf(identifier);
        lookup.findByIdentifier(identifier)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE
                        || user.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(user -> user.contactFor(kind).ifPresent(contact ->
                        otpService.issue(user.getId(), OtpPurpose.LOGIN, contact.channel(), contact.destination())));
    }

    /** Signs in with a code. Receiving the code also proves ownership of the contact. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokens loginWithCode(String identifier, String code) {
        requireOtpLoginEnabled();
        Identifiers.Kind kind = Identifiers.kindOf(identifier);
        User user = lookup.findByIdentifier(identifier)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        Contact contact = user.contactFor(kind).orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        otpService.verify(user.getId(), OtpPurpose.LOGIN, contact.destination(), code);
        verificationService.markVerified(user, contact.channel());
        ensureCanSignIn(user);
        user.recordSuccessfulLogin(clock.instant());
        return issueTokens(user);
    }

    // ------------------------------------------------------------------ password reset

    /** Sends a reset code. Always "succeeds" so the endpoint cannot be used to discover accounts. */
    @Transactional
    public void forgotPassword(String identifier) {
        Identifiers.Kind kind = Identifiers.kindOf(identifier);
        lookup.findByIdentifier(identifier)
                .filter(user -> !user.isDeleted() && user.getStatus() != UserStatus.SUSPENDED)
                .ifPresent(user -> user.contactFor(kind).ifPresent(contact -> {
                    try {
                        otpService.issue(user.getId(), OtpPurpose.PASSWORD_RESET, contact.channel(),
                                contact.destination());
                    } catch (ApiException ex) {
                        log.debug("Password reset code not sent: {}", ex.getMessage());
                    }
                }));
    }

    /** Resets the password with a code. Also unlocks the account and revokes all sessions. */
    @Transactional
    public void resetPassword(String identifier, String code, String newPassword) {
        passwordPolicy.validate("newPassword", newPassword);
        Identifiers.Kind kind = Identifiers.kindOf(identifier);
        User user = lookup.findByIdentifier(identifier)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        Contact contact = user.contactFor(kind).orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
        otpService.verify(user.getId(), OtpPurpose.PASSWORD_RESET, contact.destination(), code);
        verificationService.markVerified(user, contact.channel());
        userService.setPassword(user, newPassword);
    }

    // ------------------------------------------------------------------ helpers

    private AuthTokens issueTokens(User user) {
        return new AuthTokens(tokenService.issueAccessToken(user), refreshTokens.issue(user.getId()), user);
    }

    private static void ensureCanSignIn(User user) {
        switch (user.getStatus()) {
            case ACTIVE -> { }
            case PENDING_VERIFICATION -> throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED);
            case SUSPENDED, DELETED -> throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private void requireOtpLoginEnabled() {
        if (!properties.otpLoginEnabled()) {
            throw new ApiException(ErrorCode.FEATURE_DISABLED, "Sign-in with a one-time code is disabled");
        }
    }

    public record AuthTokens(TokenService.AccessToken accessToken, RefreshTokenService.IssuedToken refreshToken,
                             User user) {
    }
}
