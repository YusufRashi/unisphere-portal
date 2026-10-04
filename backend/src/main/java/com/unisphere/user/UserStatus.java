package com.unisphere.user;

public enum UserStatus {
    /** Created by an administrator; the user has not yet set a password via the activation link. */
    PENDING_ACTIVATION,
    ACTIVE,
    /** Cannot log in; all sessions revoked. History is preserved. */
    DEACTIVATED
}
