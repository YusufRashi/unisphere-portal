package com.unisphere.common.security;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Access to the authenticated user's id (the JWT subject) from anywhere in the request thread. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        Long id = idOrNull();
        if (id == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
        }
        return id;
    }

    /** Whether the signed-in user holds {@code role} (e.g. "ADMIN"), for checks that depend on data as well as role. */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    public static Long idOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Long.valueOf(jwt.getSubject());
        }
        return null;
    }
}
