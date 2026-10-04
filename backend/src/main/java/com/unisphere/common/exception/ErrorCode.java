package com.unisphere.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable error codes returned in the {@code code} field of every Problem Details response.
 * Clients branch on the code, never on the human-readable message.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "Password does not meet the policy"),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired token"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    ACCOUNT_DEACTIVATED(HttpStatus.FORBIDDEN, "Account deactivated"),
    CSRF_CHECK_FAILED(HttpStatus.FORBIDDEN, "Request rejected"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Not acceptable"),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Duplicate resource"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The resource was modified by someone else"),
    INVALID_STATE(HttpStatus.CONFLICT, "Operation not allowed in the current state"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Data conflict"),

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account temporarily locked"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Fallback mapping for framework exceptions that only carry a status code. */
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> DATA_CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> BUSINESS_RULE_VIOLATION;
            case 429 -> RATE_LIMITED;
            default -> status >= 500 ? INTERNAL_ERROR : MALFORMED_REQUEST;
        };
    }
}
