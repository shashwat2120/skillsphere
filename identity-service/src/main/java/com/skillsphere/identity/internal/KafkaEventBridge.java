package com.skillsphere.identity.internal;

import com.skillsphere.identity.events.AccountSuspended;
import com.skillsphere.identity.events.EmailVerificationRequested;
import com.skillsphere.identity.events.IdentityEventEnvelope;
import com.skillsphere.identity.events.InstructorApproved;
import com.skillsphere.identity.events.PasswordChanged;
import com.skillsphere.identity.events.PasswordResetRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Where an in-process fact becomes a message another process can see.
 *
 * <p>{@link AuthService}, {@link UserAdminService} and
 * {@link PasswordResetService} are completely unchanged from Sprint 5 — they
 * still just call {@code events.publishEvent(...)}, still inside Modulith's
 * transactional outbox, still with the exact-once-per-commit guarantee that
 * gave. This class is the only new thing: one more listener on those same
 * local events, whose entire job is to put the fact on Kafka so a consumer
 * outside this JVM can react to it. Keeping the publish side untouched and
 * adding a bridge is a smaller, safer change than rewriting three services
 * to talk to Kafka directly, and it is what lets this service's own tests
 * and behaviour stay provably identical to Sprint 5's.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventBridge {

    private final KafkaTemplate<String, IdentityEventEnvelope> kafka;

    @ApplicationModuleListener
    void on(EmailVerificationRequested event) {
        publish(IdentityEventEnvelope.from(event));
    }

    @ApplicationModuleListener
    void on(PasswordResetRequested event) {
        publish(IdentityEventEnvelope.from(event));
    }

    @ApplicationModuleListener
    void on(PasswordChanged event) {
        publish(IdentityEventEnvelope.from(event));
    }

    @ApplicationModuleListener
    void on(InstructorApproved event) {
        publish(IdentityEventEnvelope.from(event));
    }

    @ApplicationModuleListener
    void on(AccountSuspended event) {
        publish(IdentityEventEnvelope.from(event));
    }

    private void publish(IdentityEventEnvelope envelope) {
        log.debug("Publishing {} for user {} to Kafka", envelope.eventType(), envelope.userId());
        // Keyed by user id so Kafka's own partitioning keeps every event for
        // one account in order relative to each other, the same ordering
        // guarantee the old in-process listener got for free from running
        // synchronously per-event.
        kafka.send(IdentityEventEnvelope.TOPIC, String.valueOf(envelope.userId()), envelope);
    }
}
