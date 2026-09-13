package com.skillsphere.identity;

import com.skillsphere.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the authentication flow.
 *
 * <p>Two of these tests exist because the behaviour they assert was broken and
 * looked correct. Both are marked below. Neither defect was findable by reading
 * the code — they only appeared when the attack was actually run — so the tests
 * are the only thing standing between a future refactor and their silent
 * return.
 */
class AuthFlowIntegrationTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    /**
     * Mail is stubbed so the suite does not need an SMTP server. The delivery
     * path itself is verified separately; what matters here is that
     * registration succeeds and publishes, not that a message is transported.
     */
    @MockitoBean
    JavaMailSender mailSender;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String registerJson(String email, String role) {
        return """
                {"email":"%s","password":"correct-horse-battery","fullName":"Test User","role":"%s"}
                """.formatted(email, role);
    }

    private String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    // -----------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------

    @Test
    @DisplayName("a learner registers and is active immediately")
    void learnerRegistersActive() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(uniqueEmail(), "LEARNER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("an instructor registers as pending and cannot log in until approved")
    void instructorRegistersPending() throws Exception {
        String email = uniqueEmail();

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "INSTRUCTOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Authoring rights are granted by a human, so a pending instructor is
        // refused even with entirely correct credentials.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "correct-horse-battery")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("requesting an admin role is refused, not quietly downgraded")
    void adminRoleRefused() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(uniqueEmail(), "ADMIN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
    }

    @Test
    @DisplayName("registering a taken address does not confirm the address exists")
    void duplicateRegistrationDoesNotLeak() throws Exception {
        String email = uniqueEmail();

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "LEARNER")))
                .andExpect(status().isCreated());

        // The message must not say "already registered" — that turns
        // registration into an oracle for which addresses hold accounts.
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "LEARNER")))
                .andExpect(status().isConflict())
                .andReturn();

        String body = result.getResponse().getContentAsString().toLowerCase();
        assertThat(body)
                .as("conflict response must not reveal that the account exists")
                .doesNotContain("already")
                .doesNotContain("exists")
                .doesNotContain("registered")
                .doesNotContain("taken");
    }

    // -----------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------

    @Test
    @DisplayName("wrong password and unknown address are indistinguishable")
    void failedLoginsAreIndistinguishable() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "LEARNER")))
                .andExpect(status().isCreated());

        String wrongPassword = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "not-the-right-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownAccount = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueEmail(), "not-the-right-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Compared with the timestamp stripped, since only the text may differ.
        assertThat(stripTimestamp(wrongPassword))
                .as("a wrong password must look identical to an unknown account")
                .isEqualTo(stripTimestamp(unknownAccount));
    }

    @Test
    @DisplayName("login returns an access token and an httpOnly SameSite refresh cookie")
    void loginIssuesHardenedCookie() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "LEARNER")))
                .andExpect(status().isCreated());

        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "correct-horse-battery")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                // The refresh token must never appear in the body — that would
                // undo the httpOnly protection entirely.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie)
                .as("XSS must not be able to read the long-lived credential")
                .contains("HttpOnly")
                .as("SameSite=Strict is what makes CSRF tokens unnecessary here")
                .contains("SameSite=Strict")
                .contains("Secure")
                .as("the cookie should not ride along on every unrelated API call")
                .contains("Path=/api/auth");
    }

    // -----------------------------------------------------------------
    // Refresh rotation and theft detection
    // -----------------------------------------------------------------

    /**
     * REGRESSION. Reuse detection previously revoked every session and then
     * threw, and the exception rolled back the transaction that had just done
     * the revoking — so the warning was logged, the response was 401, and the
     * stolen chain kept working. Revocation now commits in its own transaction.
     */
    @Test
    @DisplayName("presenting a rotated refresh token burns the entire chain")
    void reuseDetectionBurnsTheChain() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "LEARNER")))
                .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "correct-horse-battery")))
                .andExpect(status().isOk())
                .andReturn();

        String first = refreshTokenFrom(login);

        MvcResult rotated = mvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", first)))
                .andExpect(status().isOk())
                .andReturn();

        String second = refreshTokenFrom(rotated);
        assertThat(second).as("refresh must rotate the token").isNotEqualTo(first);

        // The attacker replays the copied token.
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", first)))
                .andExpect(status().isUnauthorized());

        // The real user's current token must now be dead too: it is unknowable
        // which party holds it, so both are logged out.
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", second)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * REGRESSION. The per-user denylist compared issuedAt strictly less than the
     * revocation cutoff. A JWT's iat has one-second resolution, so a token
     * issued in the same second as the revocation survived it — and in an
     * automated theft, everything happens inside that second. This test runs
     * fast enough to sit on that boundary, which is exactly why it caught it.
     */
    @Test
    @DisplayName("an access token issued in the same second as revocation is still denied")
    void accessTokenRevokedWithinTheSameSecond() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "LEARNER")))
                .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "correct-horse-battery")))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = jsonField(login.getResponse().getContentAsString(), "accessToken");
        String refresh = refreshTokenFrom(login);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Trigger revocation by simulating theft.
        MvcResult rotated = mvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refresh)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(refreshTokenFrom(rotated)).isNotBlank();

        mvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refresh)))
                .andExpect(status().isUnauthorized());

        // The access token was issued moments earlier, very likely in the same
        // second as the cutoff. It must be refused regardless.
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a fresh login works after a revocation lockout")
    void recoveryAfterLockout() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "LEARNER")))
                .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "correct-horse-battery")))
                .andExpect(status().isOk())
                .andReturn();
        String refresh = refreshTokenFrom(login);

        mvc.perform(post("/api/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", refresh)));
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refresh)))
                .andExpect(status().isUnauthorized());

        // Revocation must lock out sessions, not the account itself.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "correct-horse-battery")))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------
    // Authorisation
    // -----------------------------------------------------------------

    @Test
    @DisplayName("protected endpoints reject missing and malformed tokens")
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a learner cannot reach admin endpoints")
    void learnerCannotReachAdmin() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "LEARNER")))
                .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "correct-horse-battery")))
                .andReturn();
        String token = jsonField(login.getResponse().getContentAsString(), "accessToken");

        mvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------

    @Test
    @DisplayName("a short password is rejected with a field-level error")
    void shortPasswordRejected() throws Exception {
        String body = """
                {"email":"%s","password":"short","fullName":"Test User","role":"LEARNER"}
                """.formatted(uniqueEmail());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private String refreshTokenFrom(MvcResult result) {
        jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie("refresh_token");
        return cookie == null ? null : cookie.getValue();
    }

    private String jsonField(String json, String field) {
        int start = json.indexOf("\"" + field + "\":\"") + field.length() + 4;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private String stripTimestamp(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]+\"", "")
                   .replaceAll("\"errorId\":\"[^\"]+\"", "");
    }
}
