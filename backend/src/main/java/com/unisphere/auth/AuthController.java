package com.unisphere.auth;

import com.unisphere.auth.AuthService.Session;
import com.unisphere.auth.dto.AuthDtos.ActivateAccountRequest;
import com.unisphere.auth.dto.AuthDtos.AuthResponse;
import com.unisphere.auth.dto.AuthDtos.ChangePasswordRequest;
import com.unisphere.auth.dto.AuthDtos.CurrentUserResponse;
import com.unisphere.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.unisphere.auth.dto.AuthDtos.LoginRequest;
import com.unisphere.auth.dto.AuthDtos.ResetPasswordRequest;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.common.exception.ProblemDetailFactory;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.common.web.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String CSRF_HEADER = "X-Requested-With";
    static final String CSRF_HEADER_VALUE = "XMLHttpRequest";

    private final AuthService authService;
    private final RefreshTokenCookies cookies;
    private final ProblemDetailFactory problems;

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return sessionResponse(authService.login(body.email(), body.password(), ClientInfo.from(request)));
    }

    @Operation(summary = "Exchange the refresh-token cookie for a new access token (rotates the cookie)")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        requireCsrfHeader(csrfHeader);
        try {
            return sessionResponse(authService.refresh(refreshToken, ClientInfo.from(request)));
        } catch (ApiException e) {
            if (e.getCode() != ErrorCode.INVALID_TOKEN) {
                throw e;
            }
            // Clear the dead cookie so the browser stops presenting it.
            return ResponseEntity.status(e.getCode().status())
                    .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                    .body(problems.create(e.getCode(), e.getMessage()));
        }
    }

    @Operation(summary = "Sign out: revoke the refresh token and clear the cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader) {
        requireCsrfHeader(csrfHeader);
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @Operation(summary = "Current user")
    @GetMapping("/me")
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(authService.currentUser(CurrentUser.id()));
    }

    @Operation(summary = "Activate an account using the emailed token and choose a password")
    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateAccountRequest body) {
        authService.activate(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request a password reset email (always returns 202)")
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        authService.requestPasswordReset(body.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Set a new password using the emailed reset token")
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        authService.resetPassword(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Change password (signs out all other sessions)")
    @PostMapping("/password/change")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                                       HttpServletRequest request) {
        return sessionResponse(authService.changePassword(
                CurrentUser.id(), body.currentPassword(), body.newPassword(), ClientInfo.from(request)));
    }

    private ResponseEntity<AuthResponse> sessionResponse(Session session) {
        var access = session.accessToken();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken(), session.refreshTokenTtl()).toString())
                .body(new AuthResponse(access.value(), "Bearer", access.expiresInSeconds(),
                        CurrentUserResponse.from(session.user())));
    }

    /**
     * Cookie-authenticated endpoints must carry a custom header. Cross-site HTML forms cannot set it, and
     * cross-origin scripts cannot either without passing a CORS preflight we do not allow.
     */
    private static void requireCsrfHeader(String value) {
        if (!CSRF_HEADER_VALUE.equals(value)) {
            throw new ApiException(ErrorCode.CSRF_CHECK_FAILED, "Missing required request header.");
        }
    }
}
