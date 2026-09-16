package com.skillsphere.identity.web;

import com.skillsphere.identity.internal.AuthService;
import com.skillsphere.identity.internal.PasskeyService;
import com.skillsphere.shared.security.ClientIp;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Passkey (WebAuthn) registration and authentication, per SKILLSPHERE.md
 * §6/§12.
 *
 * <p>Registration is authenticated — you add a passkey to an account you can
 * already sign in to. Authentication is deliberately public: it is itself a
 * way to sign in, so it cannot require being signed in first. Each ceremony
 * is two calls: an "options" call that hands back a fresh challenge, and the
 * call that verifies the browser's response against it.
 */
@RestController
@RequestMapping("/api/auth/passkey")
@RequiredArgsConstructor
@Tag(name = "Passkeys", description = "WebAuthn registration and authentication")
public class PasskeyController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_PATH = "/api/auth";

    private final PasskeyService passkeyService;

    @PostMapping("/register/options")
    @Operation(summary = "Start passkey registration",
            description = "Authenticated. Returns a challenge and the parameters for "
                    + "navigator.credentials.create() on the frontend.")
    public ResponseEntity<PasskeyDtos.RegistrationOptionsResponse> registrationOptions() {
        return ResponseEntity.ok(passkeyService.registrationOptions(CurrentUser.requireId()));
    }

    @PostMapping("/register")
    @Operation(summary = "Complete passkey registration",
            description = "Authenticated. Verifies the attestation response against the challenge "
                    + "just issued and, on success, stores the new credential.")
    public ResponseEntity<AuthDtos.MessageResponse> register(
            @Valid @RequestBody PasskeyDtos.RegisterPasskeyRequest request) {
        passkeyService.register(CurrentUser.requireId(), request);
        return ResponseEntity.ok(new AuthDtos.MessageResponse("Passkey registered."));
    }

    @PostMapping("/authenticate/options")
    @Operation(summary = "Start passkey login",
            description = "Public. Returns a challenge and the allowed credential ids for this address, "
                    + "for navigator.credentials.get() on the frontend.")
    public ResponseEntity<PasskeyDtos.AuthenticationOptionsResponse> authenticationOptions(
            @Valid @RequestBody PasskeyDtos.AuthenticationOptionsRequest request) {
        return ResponseEntity.ok(passkeyService.authenticationOptions(request.email()));
    }

    @PostMapping("/authenticate")
    @Operation(summary = "Complete passkey login",
            description = "Public. Verifies the assertion response against the challenge just issued "
                    + "and, on success, issues a full session — a passkey is a primary credential here, "
                    + "not only a second factor.")
    public ResponseEntity<AuthDtos.AuthResponse> authenticate(
            @Valid @RequestBody PasskeyDtos.AuthenticatePasskeyRequest request,
            HttpServletRequest servletRequest) {

        AuthService.LoginResult result = passkeyService.authenticate(
                request, ClientIp.from(servletRequest), servletRequest.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshTtl()).toString())
                .body(result.response());
    }

    private ResponseCookie refreshCookie(String value, Duration ttl) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_PATH)
                .maxAge(ttl)
                .build();
    }
}
