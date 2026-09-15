/**
 * Identity: accounts, roles, authentication, tokens, MFA and the audit trail.
 *
 * <p>Deliberately depends on nothing but the shared kernel. It is the security
 * boundary of the system, it changes rarely, and in Sprint 6 it becomes the
 * service every other service trusts — so it must never reach outward.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"shared"})
package com.skillsphere.identity;
