package com.unisphere.audit;

/** Catalogue of audited actions. Stored as text so new actions only require adding a constant. */
public enum AuditAction {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    LOGOUT,
    REFRESH_TOKEN_REUSE_DETECTED,
    ACCOUNT_ACTIVATED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_CHANGED,
    USER_CREATED,
    USER_DEACTIVATED,
    USER_REACTIVATED
}
