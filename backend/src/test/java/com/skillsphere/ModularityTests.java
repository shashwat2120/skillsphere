package com.skillsphere;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces the architecture rather than merely documenting it.
 *
 * <p>This is the test that protects Sprint 6. Every module declares the
 * dependencies it is allowed to have, and this build step fails if any module
 * reaches into another's internals or forms a dependency it never declared.
 * Without it, "we kept strict boundaries" is a claim nobody can check until the
 * day the split is attempted and turns out to be a rewrite.
 *
 * <p>It runs as pure static analysis — no Spring context, no database — so it
 * stays fast enough to sit in the normal build.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(SkillSphereApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void printsModuleStructure() {
        modules.forEach(System.out::println);
    }

    /**
     * Generates C4 component diagrams and a module canvas under
     * {@code target/spring-modulith-docs}. Worth keeping because the diagrams
     * are produced from the code itself, so the Sprint 6 architecture slide can
     * never drift from what was actually built.
     */
    @Test
    void writesDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
