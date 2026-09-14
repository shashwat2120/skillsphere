package com.skillsphere.skill.internal;

import com.skillsphere.skill.SkillLookup;
import com.skillsphere.skill.domain.Skill;
import com.skillsphere.skill.domain.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Implements the skill module's public lookup API. */
@Service
@RequiredArgsConstructor
public class SkillLookupService implements SkillLookup {

    private final SkillRepository skills;

    @Override
    @Transactional(readOnly = true)
    public Optional<SkillInfo> findById(Long skillId) {
        return skills.findById(skillId).map(this::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long skillId) {
        return skills.existsById(skillId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> namesOf(Collection<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        return skills.findAllById(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getName, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillInfo> findAllActive() {
        return skills.findByActiveTrueOrderByNameAsc().stream().map(this::toInfo).toList();
    }

    private SkillInfo toInfo(Skill skill) {
        return new SkillInfo(skill.getId(), skill.getSlug(), skill.getName(),
                skill.getLevelBand().name(), skill.isActive());
    }
}
