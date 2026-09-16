package com.skillsphere.identity.internal;

import com.skillsphere.identity.domain.PlatformSetting;
import com.skillsphere.identity.domain.PlatformSettingRepository;
import com.skillsphere.identity.web.PlatformSettingsDtos;
import com.skillsphere.shared.audit.AuditLogger;
import com.skillsphere.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

/**
 * Generic admin-editable platform configuration — {@code platform_settings},
 * per SKILLSPHERE.md §10.1 section I.
 *
 * <p>Deliberately opaque: this module has no reason to know or validate the
 * shape of any particular setting an admin might add, so a value is stored
 * and returned exactly as submitted, as JSON. Every write is recorded through
 * {@link AuditLogger}, the same mechanism {@link UserAdminService} uses —
 * platform settings are exactly the kind of wide-blast-radius change that
 * rule exists for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingRepository settings;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<PlatformSettingsDtos.SettingView> listAll() {
        return settings.findAll().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public PlatformSettingsDtos.SettingView get(String key) {
        return settings.findById(key)
                .map(this::toView)
                .orElseThrow(() -> new NotFoundException("PlatformSetting", key));
    }

    @Transactional
    public PlatformSettingsDtos.SettingView upsert(String key, Object value, Long adminId) {
        String valueJson = objectMapper.valueToTree(value).toString();

        Optional<PlatformSetting> existing = settings.findById(key);
        String beforeJson = existing.map(PlatformSetting::getValue).orElse(null);

        PlatformSetting entity = existing.orElseGet(() -> new PlatformSetting(key, valueJson, adminId));
        if (existing.isPresent()) {
            entity.update(valueJson, adminId);
        } else {
            settings.save(entity);
        }

        log.info("Platform setting '{}' {} by admin {}", key,
                existing.isPresent() ? "updated" : "created", adminId);
        auditLogger.record("PLATFORM_SETTING_UPDATED", "PLATFORM_SETTING", null,
                beforeJson, valueJson, "key=" + key);

        return toView(entity);
    }

    private PlatformSettingsDtos.SettingView toView(PlatformSetting entity) {
        Object value = objectMapper.readValue(entity.getValue(), Object.class);
        return new PlatformSettingsDtos.SettingView(
                entity.getKey(), value, entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
