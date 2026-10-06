package com.usermanagement.user;

import com.usermanagement.common.persistence.BaseEntity;
import com.usermanagement.notification.Channel;
import com.usermanagement.role.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The core identity shared by every domain. Domain-specific data lives in {@link #attributes},
 * validated against the user's type (see {@code UserTypeRegistry}).
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "user_type", nullable = false, length = 50)
    private String userType;

    @Column(length = 50)
    private String username;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public User(String userType, UserStatus status) {
        this.userType = userType;
        this.status = status;
    }

    // ----------------------------------------------------------------- sign-in & lockout

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a failed password attempt and locks the account once {@code maxAttempts} is reached. */
    public void recordFailedLogin(int maxAttempts, Duration lockDuration, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void unlock() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        passwordChangedAt = now;
        unlock();
    }

    // ----------------------------------------------------------------- contact details

    /** Changing the email resets its verification. */
    public void changeEmail(String newEmail) {
        if (!Objects.equals(email, newEmail)) {
            email = newEmail;
            emailVerified = false;
        }
    }

    /** Changing the phone resets its verification. */
    public void changePhone(String newPhone) {
        if (!Objects.equals(phone, newPhone)) {
            phone = newPhone;
            phoneVerified = false;
        }
    }

    /** Marks the contact behind {@code channel} as verified and activates a pending account. */
    public void markVerified(Channel channel) {
        switch (channel) {
            case EMAIL -> emailVerified = true;
            case SMS -> phoneVerified = true;
        }
        if (status == UserStatus.PENDING_VERIFICATION) {
            status = UserStatus.ACTIVE;
        }
    }

    /**
     * The contact to use when the user identified themselves with an identifier of {@code kind}:
     * the email for an email, the phone for a phone, and email (else phone) for a username.
     */
    public Optional<Contact> contactFor(Identifiers.Kind kind) {
        Contact byEmail = email == null ? null : new Contact(Channel.EMAIL, email);
        Contact byPhone = phone == null ? null : new Contact(Channel.SMS, phone);
        return Optional.ofNullable(switch (kind) {
            case EMAIL -> byEmail;
            case PHONE -> byPhone;
            case USERNAME -> byEmail != null ? byEmail : byPhone;
        });
    }

    // ----------------------------------------------------------------- lifecycle

    /** Soft-deletes the user and erases personal data, freeing the identifiers for re-use. */
    public void anonymizeAndDelete(Instant now) {
        status = UserStatus.DELETED;
        deletedAt = now;
        username = null;
        email = null;
        phone = null;
        passwordHash = null;
        firstName = null;
        lastName = null;
        emailVerified = false;
        phoneVerified = false;
        attributes = new LinkedHashMap<>();
        roles.clear();
    }

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
    }

    // ----------------------------------------------------------------- roles

    public void replaceRoles(Collection<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    public SortedSet<String> roleNames() {
        TreeSet<String> names = new TreeSet<>();
        roles.forEach(role -> names.add(role.getName()));
        return names;
    }

    public SortedSet<String> permissions() {
        TreeSet<String> permissions = new TreeSet<>();
        roles.forEach(role -> permissions.addAll(role.getPermissions()));
        return permissions;
    }

    // ----------------------------------------------------------------- accessors

    public String getUserType() {
        return userType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = new LinkedHashMap<>(attributes);
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
