package com.unisphere.user;

import com.unisphere.common.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A person who can sign in. State transitions (login bookkeeping, activation, deactivation) are
 * methods on the entity so the rules live in one place rather than being scattered across services.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "sessions_valid_after", nullable = false)
    private Instant sessionsValidAfter;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_id", nullable = false)
    @Convert(converter = RoleIdConverter.class)
    private Set<Role> roles = new HashSet<>();

    public User(String email, String firstName, String lastName, Set<Role> roles, Instant now) {
        this.email = normalizeEmail(email);
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.roles = roles.isEmpty() ? new HashSet<>() : EnumSet.copyOf(roles);
        this.status = UserStatus.PENDING_ACTIVATION;
        this.sessionsValidAfter = now;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void updateProfile(String firstName, String lastName, String phone) {
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
    }

    public void replaceRoles(Set<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    /** Records a failed attempt and locks the account once the threshold is reached. */
    public void registerFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Sets the first password from an activation link. */
    public void activate(String newPasswordHash) {
        if (status != UserStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException("Only pending accounts can be activated");
        }
        this.passwordHash = newPasswordHash;
        this.status = UserStatus.ACTIVE;
    }

    /** Changes the password and invalidates every existing session. */
    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        invalidateSessions(now);
    }

    public void deactivate(Instant now) {
        this.status = UserStatus.DEACTIVATED;
        invalidateSessions(now);
    }

    /** Re-enables a deactivated account. Accounts that never set a password go back to pending activation. */
    public void reactivate() {
        this.status = passwordHash == null ? UserStatus.PENDING_ACTIVATION : UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Any access token issued before {@code now} stops being accepted. */
    public void invalidateSessions(Instant now) {
        this.sessionsValidAfter = now;
    }
}
