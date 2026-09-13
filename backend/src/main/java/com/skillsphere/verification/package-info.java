/**
 * Verification: projects, the process ledger, the AI viva, instructor review
 * and the immutable evidence store.
 *
 * <p>The differentiating module. Isolated because its workload is unlike
 * anything else here: CPU-heavy inference, bursty, and slow. Keeping it behind
 * its own boundary means a queue of vivas can never slow down a learner
 * answering a question.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Verification",
        allowedDependencies = {"shared", "skill"})
package com.skillsphere.verification;
