package com.bolsopass.iam.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class UserAccount {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(1);

    private final UUID id;
    private final String email;
    private final String cpf;
    private final String fullName;
    private final String passwordHash;
    private final Set<Role> roles;
    private int consecutiveFailedAttempts;
    private Instant lockedUntil;

    public UserAccount(UUID id, String email, String cpf, String fullName, String passwordHash, Set<Role> roles) {
        this.id = Objects.requireNonNull(id, "User ID cannot be null");
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("CPF is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one RBAC role is required");
        }
        this.email = email.trim().toLowerCase();
        this.cpf = cpf.trim();
        this.fullName = fullName.trim();
        this.passwordHash = passwordHash;
        this.roles = Collections.unmodifiableSet(EnumSet.copyOf(roles));
        this.consecutiveFailedAttempts = 0;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void authenticate(String providedPasswordHash, Instant now) {
        Objects.requireNonNull(now, "Timestamp cannot be null");
        if (isLocked(now)) {
            throw new AccountRateLimitedException("Account is temporarily rate-limited due to excessive failed login attempts");
        }
        if (!this.passwordHash.equals(providedPasswordHash)) {
            this.consecutiveFailedAttempts++;
            if (this.consecutiveFailedAttempts >= MAX_FAILED_ATTEMPTS) {
                this.lockedUntil = now.plus(LOCKOUT_DURATION);
                throw new AccountRateLimitedException("Maximum failed login attempts reached (HTTP 429 Too Many Requests)");
            }
            throw new InvalidCredentialsException("Invalid credentials provided");
        }
        this.consecutiveFailedAttempts = 0;
        this.lockedUntil = null;
    }

    public boolean hasRole(Role requiredRole) {
        return roles.contains(requiredRole) || roles.contains(Role.ROLE_ADMIN);
    }

    public void assertHasRole(Role requiredRole) {
        if (!hasRole(requiredRole)) {
            throw new UnauthorizedRoleException("User lacks required role: " + requiredRole);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCpf() {
        return cpf;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public int getConsecutiveFailedAttempts() {
        return consecutiveFailedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}

