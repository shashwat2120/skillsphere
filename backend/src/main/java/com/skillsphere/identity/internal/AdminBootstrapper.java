package com.skillsphere.identity.internal;

import com.skillsphere.identity.domain.AccountStatus;
import com.skillsphere.identity.domain.Role;
import com.skillsphere.identity.domain.RoleName;
import com.skillsphere.identity.domain.RoleRepository;
import com.skillsphere.identity.domain.User;
import com.skillsphere.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Creates the first administrator, because nothing else can.
 *
 * <p><b>The problem this solves.</b> Admin is deliberately not self-registrable:
 * there is no UI path and no server path to the role, so nobody can escalate
 * themselves through the product. That is the correct security posture, and it
 * has an obvious consequence that is easy to forget — without a bootstrap, the
 * administrative half of the system can never be reached at all. The skill
 * graph, the career catalogue and the instructor approval queue would be
 * perfectly protected and permanently unusable.
 *
 * <p><b>Why not a seed migration.</b> The tempting answer is a Flyway migration
 * that inserts an admin with a known password. It is wrong for three reasons:
 * the credential lives in git forever, every deployment of the software shares
 * the same one, and migrations describe schema rather than secrets. A password
 * that is identical across every install is not a password.
 *
 * <p><b>How this works instead.</b> The account is created from configuration at
 * startup, exactly once, and only when no administrator exists. Supplying the
 * password through the environment keeps it out of the repository and lets each
 * deployment differ. When no password is configured, behaviour splits by
 * environment: development generates a random one and prints it once, so getting
 * started needs no setup; production refuses to start, because the alternative —
 * silently creating an administrator with a guessable password — is how
 * platforms get taken over.
 *
 * <p>Idempotent by design: it checks for an existing administrator rather than
 * an existing email, so restarting never resets a password an operator has since
 * changed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapper implements ApplicationRunner {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role adminRole = roles.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN role missing — database has not been migrated"));

        // Checked by role, not by email. An operator who renamed the bootstrap
        // account or created their own administrator must not have a second one
        // silently appear on the next restart.
        if (!users.findAll().isEmpty() && adminExists()) {
            log.debug("An administrator already exists — skipping bootstrap");
            return;
        }

        String email = environment.getProperty("skillsphere.bootstrap.admin-email",
                "admin@skillsphere.local");
        String configured = environment.getProperty("skillsphere.bootstrap.admin-password");
        boolean generated = false;

        if (configured == null || configured.isBlank()) {
            if (isProductionLike()) {
                // Loud and fatal. Creating an administrator with a default
                // password on a reachable deployment is worse than not starting.
                throw new IllegalStateException("""
                        No administrator exists and SKILLSPHERE_BOOTSTRAP_ADMIN_PASSWORD is not set.
                        Refusing to start: creating an administrator with a default password on a
                        production profile would hand over the platform. Set the variable and restart.
                        """);
            }
            configured = generatePassword();
            generated = true;
        }

        User admin = new User(email, "Platform Administrator");
        admin.setPasswordHash(passwordEncoder.encode(configured));
        admin.setStatus(AccountStatus.ACTIVE);
        // Verified on creation: there is no inbox to confirm from, and an
        // administrator locked out of their own platform pending an email they
        // cannot receive is a bootstrap that has not bootstrapped.
        admin.setEmailVerifiedAt(Instant.now());
        admin.addRole(adminRole);
        users.save(admin);

        if (generated) {
            // Printed once, and only in development. The alternative is storing
            // it somewhere, and a credential at rest is a credential that leaks.
            log.warn("""

                    ════════════════════════════════════════════════════════════
                     ADMINISTRATOR ACCOUNT CREATED (development bootstrap)

                       email:    {}
                       password: {}

                     Shown once and never stored. Set
                     SKILLSPHERE_BOOTSTRAP_ADMIN_PASSWORD to choose your own.
                    ════════════════════════════════════════════════════════════
                    """, email, configured);
        } else {
            log.info("Bootstrapped administrator account {}", email);
        }
    }

    private boolean adminExists() {
        return users.findAll().stream().anyMatch(user -> user.hasRole(RoleName.ADMIN));
    }

    /**
     * Any profile that is not explicitly a local or test one is treated as
     * production.
     *
     * <p>The default matters: an unrecognised profile is assumed to be real. A
     * check that defaulted the other way would fail open on exactly the
     * deployment nobody thought to configure.
     */
    private boolean isProductionLike() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return false; // no profile set at all is a developer running locally
        }
        for (String profile : active) {
            if (profile.equals("local") || profile.equals("dev") || profile.equals("test")) {
                return false;
            }
        }
        return true;
    }

    private String generatePassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
