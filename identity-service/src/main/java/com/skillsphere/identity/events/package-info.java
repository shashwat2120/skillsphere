/**
 * Identity's published events — the only part of this module other modules may
 * depend on.
 *
 * <p>Declared as a Modulith named interface so a dependency can be expressed as
 * {@code identity :: events} rather than on the whole module. The distinction
 * is the point: notification is allowed to know that an email needs verifying,
 * and is still forbidden from touching {@code identity.domain} or
 * {@code identity.security}. The build enforces that, so the coupling cannot
 * quietly widen later.
 *
 * <p>It is also the coupling that survives Sprint 6. These records become the
 * message contracts carried over Kafka; a dependency on the domain entities
 * behind them would not survive the split at all.
 */
@org.springframework.modulith.NamedInterface("events")
package com.skillsphere.identity.events;
