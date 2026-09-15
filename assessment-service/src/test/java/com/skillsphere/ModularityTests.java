package com.skillsphere;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces the architecture rather than merely documenting it.
 *
 * <p>This service bundles two Sprint 5 modules (assessment and skill) on
 * purpose — see this project's pom.xml for why — but "bundled in one
 * process" was never meant to mean "the boundary between them stops
 * mattering." assessment still must not reach into skill.internal (or
 * skill.domain) directly, only through the MasteryUpdater/SkillLookup
 * seam — this is what checks that stays true as the code changes, exactly
 * as it did for the whole monolith before the split.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(AssessmentServiceApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void printsModuleStructure() {
        modules.forEach(System.out::println);
    }

    @Test
    void writesDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
