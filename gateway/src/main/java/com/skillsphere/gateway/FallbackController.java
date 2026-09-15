package com.skillsphere.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Where {@code GatewayRoutes}' circuit breakers land once one trips.
 *
 * <p>A caller reaching this endpoint has not necessarily done anything
 * wrong — a service is either genuinely down or, more often in this
 * project's Windows dev setup, mid-restart after a code change. The
 * response is deliberately the same shape every other error in this
 * system already uses ({@link ProblemDetail}), so a frontend that already
 * handles {@code /errors/*} types does not need a special case for "the
 * gateway itself gave up" versus "the service answered with an error".
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public ResponseEntity<ProblemDetail> fallback(HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create("https://skillsphere.dev/errors/service_unavailable"));
        problem.setTitle("Service Unavailable");
        problem.setDetail("This part of SkillSphere is temporarily unavailable — the service "
                + "behind this request is not responding, or has failed enough recent requests "
                + "that the gateway is giving it a moment to recover. Try again shortly.");
        problem.setProperty("code", "SERVICE_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
