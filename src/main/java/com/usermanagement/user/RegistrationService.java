package com.usermanagement.user;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import com.usermanagement.extension.RegistrationContext;
import com.usermanagement.extension.RegistrationValidator;
import com.usermanagement.role.RoleService;
import com.usermanagement.user.event.UserRegisteredEvent;
import com.usermanagement.usertype.Actor;
import com.usermanagement.usertype.AttributeValidator;
import com.usermanagement.usertype.UserType;
import com.usermanagement.usertype.UserTypeRegistry;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates users, either through self-service registration ({@link Actor#USER}) or by an
 * administrator ({@link Actor#ADMIN}).
 */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final UserTypeRegistry userTypes;
    private final AttributeValidator attributeValidator;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final UniquenessChecker uniqueness;
    private final VerificationService verificationService;
    private final List<RegistrationValidator> validators;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RegistrationService(UserRepository users, UserTypeRegistry userTypes,
                               AttributeValidator attributeValidator, PasswordPolicy passwordPolicy,
                               PasswordEncoder passwordEncoder, RoleService roleService, UniquenessChecker uniqueness,
                               VerificationService verificationService, List<RegistrationValidator> validators,
                               ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.userTypes = userTypes;
        this.attributeValidator = attributeValidator;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.uniqueness = uniqueness;
        this.verificationService = verificationService;
        this.validators = validators;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public User register(RegistrationCommand command, Actor actor) {
        UserType type = userTypes.requireEnabled(command.userType());
        if (actor == Actor.USER && !type.selfRegistration()) {
            throw new ApiException(ErrorCode.REGISTRATION_NOT_ALLOWED);
        }

        String username = Identifiers.username(command.username());
        String email = Identifiers.email(command.email());
        String phone = Identifiers.phone(command.phone());

        Map<String, String> errors = new LinkedHashMap<>();
        ContactRules.check(type, email, phone, errors);
        if (actor == Actor.USER && (command.password() == null || command.password().isEmpty())) {
            errors.put("password", "is required");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (command.password() != null) {
            passwordPolicy.validate("password", command.password());
        }
        Map<String, Object> attributes = attributeValidator.forCreate(type, command.attributes(), actor);
        uniqueness.check(null, username, email, phone);

        RegistrationContext context = new RegistrationContext(type, username, email, phone,
                blankToNull(command.firstName()), blankToNull(command.lastName()), Map.copyOf(attributes), actor);
        validators.forEach(validator -> validator.validate(context));

        boolean needsVerification = actor == Actor.USER && type.verificationRequired();
        User user = new User(type.code(), needsVerification ? UserStatus.PENDING_VERIFICATION : UserStatus.ACTIVE);
        user.setUsername(username);
        user.changeEmail(email);
        user.changePhone(phone);
        user.setFirstName(context.firstName());
        user.setLastName(context.lastName());
        user.setAttributes(attributes);
        if (command.password() != null) {
            user.changePassword(passwordEncoder.encode(command.password()), clock.instant());
        }
        Set<String> roleNames = new HashSet<>(type.defaultRoles());
        if (actor == Actor.ADMIN) {
            roleNames.addAll(command.extraRoles());
        }
        user.replaceRoles(roleService.resolve(roleNames));

        User saved = users.saveAndFlush(user);
        events.publishEvent(new UserRegisteredEvent(UserSnapshot.of(saved), clock.instant()));

        if (needsVerification) {
            // One channel is enough to activate the account; email is preferred when both exist.
            saved.contactFor(Identifiers.Kind.USERNAME).ifPresent(contact -> verificationService.sendCode(saved, contact));
        }
        return saved;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
