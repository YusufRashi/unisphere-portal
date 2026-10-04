package com.unisphere.auth;

import com.unisphere.common.config.UniSphereProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token travels only in this cookie: HttpOnly (no JavaScript access), SameSite=Strict (never
 * sent on cross-site requests) and path-scoped to /api/auth (not sent with ordinary API calls).
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "unisphere_refresh";
    private static final String PATH = "/api/auth";

    private final boolean secure;

    public RefreshTokenCookies(UniSphereProperties properties) {
        this.secure = properties.security().cookieSecure();
    }

    public ResponseCookie create(String token, Duration ttl) {
        return base(token).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
