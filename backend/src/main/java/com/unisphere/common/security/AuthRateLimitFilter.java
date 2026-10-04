package com.unisphere.common.security;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Throttles unauthenticated auth endpoints per client IP to slow down credential stuffing and token
 * guessing. Complements per-account lockout (which protects a single account from distributed attacks).
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /**
     * Endpoints where an attacker could guess a secret (password or token). {@code /api/auth/refresh} is
     * deliberately excluded: every page load calls it, many users share one IP behind a campus NAT, and its
     * 256-bit token cannot be guessed (reuse is caught by rotation instead).
     */
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/activate",
            "/api/auth/password/forgot",
            "/api/auth/password/reset");

    private final FixedWindowRateLimiter limiter;
    private final HandlerExceptionResolver exceptionResolver;

    public AuthRateLimitFilter(FixedWindowRateLimiter limiter, HandlerExceptionResolver exceptionResolver) {
        this.limiter = limiter;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = limiter.tryAcquire(request.getRemoteAddr() + "|" + request.getRequestURI());
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            ApiException ex = new ApiException(ErrorCode.RATE_LIMITED,
                    "Too many attempts. Please wait %d seconds and try again.".formatted(retryAfter))
                    .with("retryAfterSeconds", retryAfter);
            exceptionResolver.resolveException(request, response, null, ex);
            return;
        }
        chain.doFilter(request, response);
    }
}
