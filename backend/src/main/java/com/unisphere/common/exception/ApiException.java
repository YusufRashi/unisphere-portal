package com.unisphere.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single exception type services throw for expected failures. It carries an {@link ErrorCode}
 * (which fixes the HTTP status) and a user-safe message. Never put internal details in the message.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /** Adds an extra, user-safe property to the Problem Details body (e.g. {@code retryAfterSeconds}). */
    public ApiException with(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    public static ApiException notFound(String resource, Object id) {
        return new ApiException(ErrorCode.NOT_FOUND, "%s %s was not found.".formatted(resource, id));
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(ErrorCode.INVALID_STATE, message);
    }
}
