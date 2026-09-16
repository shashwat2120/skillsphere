package com.skillsphere.identity.web;

import com.skillsphere.identity.internal.PlatformSettingsService;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only read/write access to {@code platform_settings}.
 *
 * <p>Same auth pattern as {@link AdminAuditController} and
 * {@link AdminUserController}: role-gated at the controller with
 * {@code @PreAuthorize}, on top of {@code SecurityConfig}'s own
 * {@code /api/admin/**} rule — belt and braces rather than relying on either
 * alone.
 */
@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Platform settings (admin)", description = "Generic admin-editable platform configuration")
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettings;

    @GetMapping
    @Operation(summary = "All platform settings")
    public List<PlatformSettingsDtos.SettingView> list() {
        return platformSettings.listAll();
    }

    @PutMapping("/{key}")
    @Operation(summary = "Create or update one setting",
            description = "Upserts by key. The value is stored and returned exactly as submitted — "
                    + "this endpoint does not validate the shape of any particular setting.")
    public PlatformSettingsDtos.SettingView upsert(
            @PathVariable String key,
            @Valid @RequestBody PlatformSettingsDtos.SettingUpdateRequest request) {
        return platformSettings.upsert(key, request.value(), CurrentUser.requireId());
    }
}
