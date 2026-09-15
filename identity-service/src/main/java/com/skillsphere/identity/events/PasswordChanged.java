package com.skillsphere.identity.events;

/**
 * Published after a password actually changes.
 *
 * <p>Consumed to send a notification the user did not ask for, and that is the
 * point: if they did not make the change, this message is the only way they
 * learn their account was taken, and the only chance they have to act while it
 * still matters.
 */
public record PasswordChanged(Long userId, String email, String fullName) {
}
