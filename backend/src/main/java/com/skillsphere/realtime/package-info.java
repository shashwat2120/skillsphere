/**
 * Real-time: WebSocket/STOMP infrastructure, presence, the live arena and
 * instructor confusion alerts.
 *
 * <p>Separated because it scales on a different axis from everything else —
 * concurrent open connections rather than request rate. It consumes events and
 * fans them out to subscribers; it does not own domain state.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Realtime",
        allowedDependencies = {"shared", "identity", "skill", "assessment"})
package com.skillsphere.realtime;
