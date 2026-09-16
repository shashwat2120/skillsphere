package com.skillsphere.identity.web;

import com.skillsphere.identity.internal.AuthService;
import com.skillsphere.identity.internal.MfaService;
import com.skillsphere.identity.internal.PasswordResetService;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.shared.security.ClientIp;
import com.skillsphere.shared.security.CurrentUser;
import com.skillsphere.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Authentication endpoints.
 *
 * <p>The controller deliberately holds no logic beyond HTTP concerns: read the
 * request, call the service, shape the response. Everything security-relevant
 * lives in {@link AuthService}, so it can be reasoned about and tested without a
 * servlet in sight.
 *
 * <p><b>Where the refresh token lives.</b> Not in the response body, and not in
 * {@code localStorage}. It is set as an {@code httpOnly}, {@code Secure},
 * {@code SameSite=Strict} cookie scoped to the refresh path. Each attribute
 * removes one attack:
 * <ul>
 *   <li>{@code httpOnly} — JavaScript cannot read it, so an XSS flaw cannot
 *       steal the long-lived credential. This is the single most important
 *       difference between this design and the common one.</li>
 *   <li>{@code SameSite=Strict} — the browser will not send it from another
 *       origin, which is what makes CSRF tokens unnecessary here.</li>
 *   <li>{@code Secure} — never transmitted over plain HTTP.</li>
 *   <li>Path-scoped — it is not attached to ordinary API calls, so it is not
 *       sprayed across every request that does not need it.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token refresh and email verification")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_PATH = "/api/auth";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;

    @PostMapping("/register")
    @Operation(summary = "Create an account",
            description = "Learners are active immediately; instructors start pending admin approval. "
                    + "Registration never returns a session — the address must be confirmed first.")
    public ResponseEntity<AuthDtos.RegistrationResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate",
            description = "Returns a short-lived access token in the body and sets the refresh token "
                    + "as an httpOnly cookie — unless the account has MFA enabled, in which case this "
                    + "returns a challenge (mfaRequired: true, mfaToken) instead, and no cookie is set. "
                    + "Complete the login with POST /api/auth/mfa/verify.")
    public ResponseEntity<?> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest servletRequest) {

        AuthService.LoginOutcome outcome = authService.login(
                request, ClientIp.from(servletRequest), servletRequest.getHeader(HttpHeaders.USER_AGENT));

        return switch (outcome) {
            case AuthService.SessionIssued issued -> ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE,
                            refreshCookie(issued.result().refreshToken(), issued.result().refreshTtl()).toString())
                    .body(issued.result().response());
            case AuthService.MfaRequired challenge -> ResponseEntity.ok(
                    new AuthDtos.MfaChallengeResponse(true, challenge.mfaToken(), challenge.expiresAt()));
        };
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new session",
            description = "Rotates the refresh token. Presenting an already-rotated token is treated "
                    + "as theft and revokes every session for that account.")
    public ResponseEntity<AuthDtos.AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AuthService.LoginResult result = authService.refresh(
                refreshToken, ClientIp.from(servletRequest), servletRequest.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshTtl()).toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    @Operation(summary = "End the session",
            description = "Revokes the refresh token and denies the current access token immediately, "
                    + "rather than leaving it valid until it expires.")
    public ResponseEntity<AuthDtos.MessageResponse> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        // The jti and expiry come from the request attributes set during token
        // parsing rather than being re-parsed here.
        String tokenId = (String) servletRequest.getAttribute("jwt.tokenId");
        java.time.Instant expiry = (java.time.Instant) servletRequest.getAttribute("jwt.expiresAt");

        authService.logout(refreshToken, tokenId, expiry);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .body(new AuthDtos.MessageResponse("Signed out."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link",
            description = "Always reports success, whatever address is submitted. Saying "
                    + "\"no account with that email\" would turn this into a free "
                    + "account-enumeration oracle.")
    public ResponseEntity<AuthDtos.MessageResponse> forgotPassword(
            @Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        // Identical response in every case — including a throttled request and
        // an address that does not exist.
        return ResponseEntity.ok(new AuthDtos.MessageResponse(
                "If an account exists for that address, a reset link is on its way."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a reset token",
            description = "Revokes every existing session on success. A reset usually follows a "
                    + "compromise, so leaving sessions alive would keep the attacker signed in "
                    + "while the owner believes the problem is fixed.")
    public ResponseEntity<AuthDtos.MessageResponse> resetPassword(
            @Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok()
                // The old session is dead, so clear the stale cookie rather than
                // leaving the browser to present a token that can never work.
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .body(new AuthDtos.MessageResponse(
                        "Password updated. You have been signed out everywhere — sign in again."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address using the emailed token")
    public ResponseEntity<AuthDtos.MessageResponse> verifyEmail(
            @RequestParam @NotBlank String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new AuthDtos.MessageResponse("Email address confirmed."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Send a fresh confirmation link",
            description = "For an original link that expired or never arrived. Always reports "
                    + "success, whatever address is submitted — see AuthService for why.")
    public ResponseEntity<AuthDtos.MessageResponse> resendVerification(
            @Valid @RequestBody AuthDtos.ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(new AuthDtos.MessageResponse(
                "If that address needs confirming, a new link is on its way."));
    }

    @GetMapping("/me")
    @Operation(summary = "The currently authenticated caller")
    public ResponseEntity<UserPrincipal> me(@AuthenticationPrincipal UserPrincipal principal) {
        return principal == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                : ResponseEntity.ok(principal);
    }

    // ------------------------------------------------------------------
    // MFA — TOTP
    // ------------------------------------------------------------------

    @PostMapping("/mfa/totp/enroll")
    @Operation(summary = "Start TOTP enrollment",
            description = "Authenticated only. Generates a new secret and ten recovery codes and returns "
                    + "them in plaintext — the only time either value is ever available. Confirm with "
                    + "POST /api/auth/mfa/verify before the factor actually gates login.")
    public ResponseEntity<AuthDtos.MfaEnrollResponse> enrollMfa(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(mfaService.enroll(CurrentUser.requireId()));
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Confirm a TOTP code",
            description = "Two different moments share this endpoint. Called while authenticated (with "
                    + "just `code`), it confirms an enrollment just started. Called while NOT authenticated "
                    + "(with `mfaToken` from the login challenge, plus `code` or `recoveryCode`), it "
                    + "completes that login and returns a full session, exactly like POST /api/auth/login.")
    public ResponseEntity<AuthDtos.MfaVerifyResponse> verifyMfa(
            @Valid @RequestBody AuthDtos.MfaVerifyRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        if (principal != null) {
            if (request.code() == null || request.code().isBlank()) {
                throw new ValidationException("MFA_CODE_REQUIRED", "A code is required.");
            }
            mfaService.confirmEnrollment(principal.id(), request.code());
            return ResponseEntity.ok(new AuthDtos.MfaVerifyResponse(true, null));
        }

        if (request.mfaToken() == null || request.mfaToken().isBlank()) {
            throw new ValidationException("MFA_TOKEN_REQUIRED",
                    "mfaToken is required to complete a login.");
        }

        AuthService.LoginResult result = mfaService.verifyLogin(
                request.mfaToken(), request.code(), request.recoveryCode(),
                ClientIp.from(servletRequest), servletRequest.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshTtl()).toString())
                .body(new AuthDtos.MfaVerifyResponse(true, result.response()));
    }

    // ------------------------------------------------------------------

    private ResponseCookie refreshCookie(String value, Duration ttl) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_PATH)
                .maxAge(ttl)
                .build();
    }

    private ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_PATH)
                .maxAge(0)
                .build();
    }
}
