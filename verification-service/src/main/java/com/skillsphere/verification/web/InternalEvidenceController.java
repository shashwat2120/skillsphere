package com.skillsphere.verification.web;

import com.skillsphere.verification.EvidenceLookup;
import com.skillsphere.verification.internal.EvidenceLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service only — never routed through the gateway, reachable
 * only within the Docker network / local dev host, never from a browser.
 * See assessment-service's InternalMasteryController for the identical
 * reasoning; this is career-service's other genuinely live cross-service
 * read.
 *
 * <p>Evidence issuance is infrequent (once per project verification), so
 * the case for a synchronous call here is different from mastery's "every
 * response" frequency — but it is still per-learner, request-shaped data
 * a passport view needs complete and current, not a small, near-static
 * catalog every service could reasonably cache. career-service's
 * EvidenceLookupHttpClient is the caller side of this contract.
 */
@RestController
@RequiredArgsConstructor
public class InternalEvidenceController {

    private final EvidenceLookupService evidenceLookup;

    @GetMapping("/internal/evidence")
    public List<EvidenceLookup.EvidenceItem> evidence(@RequestParam Long userId) {
        return evidenceLookup.findByUserId(userId);
    }
}
