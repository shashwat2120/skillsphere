/**
 * Shared kernel: base entity types, error handling, cross-cutting configuration
 * and small value types every module needs.
 *
 * <p>Declared open so any module may depend on it. Keep it deliberately thin —
 * a shared kernel that grows into a junk drawer becomes the coupling that stops
 * modules being extractable, which is the one failure this architecture exists
 * to prevent.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.skillsphere.shared;
