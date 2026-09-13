package com.skillsphere.identity;

import com.skillsphere.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account lockout behaviour.
 *
 * <p>The test that matters most here is {@link #correctPasswordNeverLocksAnAccount()}.
 * The obvious implementation — count every login attempt — turns the security
 * control into a weapon: anyone who knows a victim's address can lock them out
 * at will, and worse, a victim repeatedly signing in correctly would lock
 * themselves out. The lockout must count failures only, which is why the check
 * reads the counter rather than incrementing it.
 *
 * <p>The suite widens the per-IP limit and narrows the account threshold to
 * three, since every test shares the loopback address and each Argon2id hash
 * costs 64 MiB and roughly a quarter of a second.
 */
class LoginLockoutTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JavaMailSender mailSender;

    private static final String PASSWORD = "correct-horse-battery";

    private String registerLearner() throws Exception {
        String email = "lockout-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Lock Test","role":"LEARNER"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    private int attemptLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("repeated wrong passwords eventually lock the account")
    void repeatedFailuresLockTheAccount() throws Exception {
        String email = registerLearner();

        // Threshold is 3 in the test profile.
        assertThat(attemptLogin(email, "wrong-password-one")).isEqualTo(401);
        assertThat(attemptLogin(email, "wrong-password-two")).isEqualTo(401);
        assertThat(attemptLogin(email, "wrong-password-three")).isEqualTo(401);

        // The account is now locked, so even the correct password is refused.
        assertThat(attemptLogin(email, PASSWORD))
                .as("a locked account must refuse even valid credentials")
                .isEqualTo(401);
    }

    /**
     * The property that stops this control becoming an attack.
     *
     * <p>If the check incremented rather than read, signing in correctly would
     * push the account toward lockout, and a user with a valid password could
     * lock themselves out by logging in a few times.
     */
    @Test
    @DisplayName("signing in correctly never counts toward lockout")
    void correctPasswordNeverLocksAnAccount() throws Exception {
        String email = registerLearner();

        // Well past the failure threshold of 3.
        for (int i = 0; i < 6; i++) {
            assertThat(attemptLogin(email, PASSWORD))
                    .as("correct sign-in number %d must succeed", i + 1)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("a successful sign-in clears earlier failures")
    void successResetsTheFailureCounter() throws Exception {
        String email = registerLearner();

        // Two failures — one short of the threshold.
        assertThat(attemptLogin(email, "mistyped-once")).isEqualTo(401);
        assertThat(attemptLogin(email, "mistyped-twice")).isEqualTo(401);

        // Getting it right clears the slate.
        assertThat(attemptLogin(email, PASSWORD)).isEqualTo(200);

        // So two more mistakes must not lock the account: without the reset,
        // failures four and five would cross the threshold and a person who
        // mistypes occasionally over a long session would be locked out.
        assertThat(attemptLogin(email, "mistyped-again")).isEqualTo(401);
        assertThat(attemptLogin(email, "mistyped-once-more")).isEqualTo(401);
        assertThat(attemptLogin(email, PASSWORD))
                .as("failures either side of a success must not accumulate")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("a locked account is indistinguishable from a wrong password")
    void lockoutDoesNotAnnounceItself() throws Exception {
        String email = registerLearner();

        String wrongPasswordBody = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-one"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        attemptLogin(email, "wrong-two");
        attemptLogin(email, "wrong-three");

        String lockedBody = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-four"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Saying "temporarily locked" would confirm the address is registered
        // and tell an attacker the attack is landing.
        assertThat(strip(lockedBody))
                .as("a locked response must look exactly like an ordinary rejection")
                .isEqualTo(strip(wrongPasswordBody));

        assertThat(lockedBody.toLowerCase())
                .doesNotContain("lock")
                .doesNotContain("too many")
                .doesNotContain("attempts");
    }

    @Test
    @DisplayName("locking one account does not affect another")
    void lockoutIsScopedToOneAccount() throws Exception {
        String victim = registerLearner();
        String bystander = registerLearner();

        attemptLogin(victim, "wrong-one");
        attemptLogin(victim, "wrong-two");
        attemptLogin(victim, "wrong-three");
        assertThat(attemptLogin(victim, PASSWORD)).isEqualTo(401);

        // A per-account lockout that leaked across accounts would let one
        // attacker take down every user on a shared address.
        assertThat(attemptLogin(bystander, PASSWORD))
                .as("an unrelated account must be unaffected")
                .isEqualTo(200);
    }

    private String strip(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]+\"", "")
                   .replaceAll("\"errorId\":\"[^\"]+\"", "");
    }
}
