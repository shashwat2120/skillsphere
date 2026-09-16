package com.skillsphere.identity.web;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class PlatformSettingsDtos {

    private PlatformSettingsDtos() {
    }

    /** {@code value} is whatever JSON shape the setting holds — opaque to this module by design. */
    public record SettingView(
            String key,
            Object value,
            Long updatedBy,
            Instant updatedAt) {
    }

    public record SettingUpdateRequest(
            @NotNull Object value) {
    }
}
