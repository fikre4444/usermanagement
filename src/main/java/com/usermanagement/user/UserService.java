package com.usermanagement.user;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.role.RoleService;
import com.usermanagement.user.event.UserDeletedEvent;
import com.usermanagement.user.event.UserPasswordChangedEvent;
import com.usermanagement.user.event.UserRolesChangedEvent;
import com.usermanagement.user.event.UserStatusChangedEvent;
import com.usermanagement.user.event.UserUpdatedEvent;
import com.usermanagement.usertype.Actor;
import com.usermanagement.usertype.AttributeValidator;
import com.usermanagement.usertype.UserType;
import com.usermanagement.usertype.UserTypeRegistry;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile and account management for existing users. */
@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final UserLookup lookup;
    private final UserTypeRegistry userTypes;
    private final AttributeValidator attributeValidator;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final UniquenessChecker uniqueness;
    private final VerificationService verificationService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserService(UserRepository users, UserLookup lookup, UserTypeRegistry userTypes,
                       AttributeValidator attributeValidator, PasswordPolicy passwordPolicy,
                       PasswordEncoder passwordEncoder, RoleService roleService, UniquenessChecker uniqueness,
                       VerificationService verificationService, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.lookup = lookup;
        this.userTypes = userTypes;
        this.attributeValidator = attributeValidator;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.uniqueness = uniqueness;
        this.verificationService = verificationService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return lookup.require(id);
    }

    @Transactional(readOnly = true)
    public Page<User> search(UserSearchCriteria criteria, Pageable pageable) {
        return users.findAll(criteria.toSpecification(), pageable);
    }

    public User updateProfile(UUID userId, ProfileUpdate update, Actor actor) {
        User user = lookup.require(userId);
        UserType type = userTypes.get(user.getUserType());

        String username = update.username() == null ? user.getUsername() : Identifiers.username(update.username());
        String email = update.email() == null ? user.getEmail() : Identifiers.email(update.email());
        String phone = update.phone() == null ? user.getPhone() : Identifiers.phone(update.phone());
        Map<String, String> errors = new LinkedHashMap<>();
        ContactRules.check(type, email, phone, errors);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        Map<String, Object> attributes = update.attributes() == null
                ? user.getAttributes()
                : attributeValidator.forUpdate(type, user.getAttributes(), update.attributes(), actor);
        uniqueness.check(user.getId(), username, email, phone);

        boolean emailChanged = !Objects.equals(email, user.getEmail());
        boolean phoneChanged = !Objects.equals(phone, user.getPhone());
        if (update.firstName() != null) {
            user.setFirstName(blankToNull(update.firstName()));
        }
        if (update.lastName() != null) {
            user.setLastName(blankToNull(update.lastName()));
        }
        user.setUsername(username);
        user.changeEmail(email);
        user.changePhone(phone);
        user.setAttributes(attributes);
        if (actor == Actor.ADMIN) {
            if (update.emailVerified() != null && user.getEmail() != null) {
                user.setEmailVerified(update.emailVerified());
            }
            if (update.phoneVerified() != null && user.getPhone() != null) {
                user.setPhoneVerified(update.phoneVerified());
            }
        }
        events.publishEvent(new UserUpdatedEvent(UserSnapshot.of(user), clock.instant()));

        // Ask the user to verify new contact details right away.
        if (emailChanged && email != null && !user.isEmailVerified()) {
            verificationService.trySendCode(user, user.contactFor(Identifiers.Kind.EMAIL).orElseThrow());
        }
        if (phoneChanged && phone != null && !user.isPhoneVerified()) {
            verificationService.trySendCode(user, user.contactFor(Identifiers.Kind.PHONE).orElseThrow());
        }
        return user;
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = lookup.require(userId);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.validation("currentPassword", "is incorrect");
        }
        setPassword(user, newPassword);
    }

    /** Sets a new password after validating it against the policy. Revokes all sessions. */
    public void setPassword(User user, String newPassword) {
        passwordPolicy.validate("newPassword", newPassword);
        user.changePassword(passwordEncoder.encode(newPassword), clock.instant());
        users.save(user);
        events.publishEvent(new UserPasswordChangedEvent(user.getId(), clock.instant()));
    }

    public User changeStatus(UUID userId, UserStatus status) {
        if (status != UserStatus.ACTIVE && status != UserStatus.SUSPENDED) {
            throw ApiException.validation("status", "must be ACTIVE or SUSPENDED");
        }
        User user = lookup.require(userId);
        UserStatus previous = user.getStatus();
        if (previous != status) {
            user.setStatus(status);
            events.publishEvent(new UserStatusChangedEvent(user.getId(), previous, status, clock.instant()));
        }
        return user;
    }

    public User assignRoles(UUID userId, Collection<String> roleNames) {
        User user = lookup.require(userId);
        user.replaceRoles(roleService.resolve(roleNames));
        events.publishEvent(new UserRolesChangedEvent(user.getId(), Set.copyOf(user.roleNames()), clock.instant()));
        return user;
    }

    public User unlock(UUID userId) {
        User user = lookup.require(userId);
        user.unlock();
        return user;
    }

    /** Soft-deletes the user and erases personal data. Revokes all sessions. */
    public void delete(UUID userId) {
        User user = lookup.require(userId);
        user.anonymizeAndDelete(clock.instant());
        events.publishEvent(new UserDeletedEvent(user.getId(), clock.instant()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
