/**
 * Notification: the in-app notification store, delivery preferences and queued
 * email.
 *
 * <p>Another pure event consumer. Mail in particular must never sit on a
 * request: an SMTP handshake takes seconds, and a learner registering should
 * not wait for one.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Notification",
        allowedDependencies = {"shared", "identity :: events"})
package com.skillsphere.notification;
