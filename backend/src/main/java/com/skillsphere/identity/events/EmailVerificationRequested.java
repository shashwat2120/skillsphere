package com.skillsphere.identity.events;

import java.time.Instant;

/**
 * Published when an account needs to prove control of its email address.
 *
 * <p>This sits in the identity module's root package, which makes it part of
 * the module's <em>public API</em> — other modules may depend on it, while
 * everything under {@code identity.domain} and {@code identity.security} stays
 * internal and is enforced as such by the Modulith boundary test.
 *
 * <p><b>Why an event rather than a direct call.</b> Identity does not send
 * email. It states a fact — this address needs verifying — and the notification
 * module decides what to do about it. That keeps registration fast and
 * unaffected by an SMTP outage, and it is what lets notification become its own
 * service in Sprint 6 without identity changing at all.
 *
 * <p>Because Modulith's Event Publication Registry is on the classpath, this
 * event is written to the outbox inside the same transaction that creates the
 * account. Either both happen or neither does — no more accounts stuck
 * unverified because the process died between the insert and the send.
 *
 * <p>The token travels in the event rather than being re-read by the consumer:
 * only its hash is stored, so the plaintext exists exactly once, at this moment.
 */
public record EmailVerificationRequested(
        Long userId,
        String email,
        String fullName,
        String verificationToken,
        Instant expiresAt) {
}
