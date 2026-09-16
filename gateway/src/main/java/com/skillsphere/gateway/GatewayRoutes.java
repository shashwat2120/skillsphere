package com.skillsphere.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Where every path this gateway knows about goes.
 *
 * <p>Routing through {@code lb://<service>} rather than a fixed
 * {@code http://localhost:<port>} is what lets this file be the only thing
 * that changes as services are extracted — every client already talks to the
 * gateway's own origin, never to a backend directly, so cutting a module out
 * of the monolith only ever means adding one more specific route here first.
 *
 * <p><b>Order matters.</b> Identity's routes are listed before the
 * monolith's catch-all {@code /api/**}: {@link RouterFunction} composition
 * matches predicates in the order they are combined, first match wins, so
 * the narrower identity routes have to come first or the broad monolith
 * route would swallow every request before identity ever saw one.
 *
 * <p><b>Every route now carries a circuit breaker</b> — {@code
 * circuitBreaker(...)} is added after {@code lb(...)} on each route
 * deliberately: filters compose so the last one added wraps outermost, and
 * the breaker needs to sit around both load-balancer resolution and the
 * actual HTTP call to see failures from either. Without this, one
 * downstream service hanging (a slow database, a stuck thread pool) would
 * let every client request pile up waiting on it — this is the single
 * point every request already passes through, so it is exactly where a
 * hung backend does the most damage if nothing here fails fast. Each
 * breaker falls back to {@link FallbackController}, which returns a plain
 * 503 ProblemDetail naming the service that tripped, rather than the
 * connection-refused stack trace a caller would see otherwise. Tuning
 * (failure-rate threshold, wait duration, sliding window) lives in
 * application.yml under {@code resilience4j.circuitbreaker.instances}, one
 * instance per service so a struggling verification-service, say, cannot
 * trip identity-service's breaker too.
 */
@Configuration
public class GatewayRoutes {

    private static final URI FALLBACK = URI.create("forward:/fallback");

    @Bean
    public RouterFunction<ServerResponse> apiRoute() {
        return route("identity_service_auth_api")
                .route(path("/api/auth/**"), http())
                .filter(lb("identity-service"))
                .filter(circuitBreaker("identity-service-cb", FALLBACK))
                .build()
                .and(route("identity_service_admin_api")
                        .route(path("/api/admin/users/**")
                                .or(path("/api/admin/audit-log/**"))
                                .or(path("/api/admin/settings/**")), http())
                        .filter(lb("identity-service"))
                        .filter(circuitBreaker("identity-service-cb", FALLBACK))
                        .build())
                .and(route("analytics_service_api")
                        .route(path("/api/instructor/analytics/**")
                                .or(path("/api/me/what-if")), http())
                        .filter(lb("analytics-service"))
                        .filter(circuitBreaker("analytics-service-cb", FALLBACK))
                        .build())
                .and(route("content_service_api")
                        .route(path("/api/courses/**")
                                .or(path("/api/instructor/courses/**")), http())
                        .filter(lb("content-service"))
                        .filter(circuitBreaker("content-service-cb", FALLBACK))
                        .build())
                .and(route("assessment_service_api")
                        .route(path("/api/diagnostics/**")
                                .or(path("/api/skills/**"))
                                .or(path("/api/instructor/items/**"))
                                .or(path("/api/admin/skills/**")), http())
                        .filter(lb("assessment-service"))
                        .filter(circuitBreaker("assessment-service-cb", FALLBACK))
                        .build())
                .and(route("career_service_api")
                        .route(path("/api/careers/**")
                                .or(path("/api/passport/**"))
                                .or(path("/api/public/passports/**")), http())
                        .filter(lb("career-service"))
                        .filter(circuitBreaker("career-service-cb", FALLBACK))
                        .build())
                .and(route("verification_service_api")
                        .route(path("/api/verification/**"), http())
                        .filter(lb("verification-service"))
                        .filter(circuitBreaker("verification-service-cb", FALLBACK))
                        .build())
                .and(route("realtime_service_api")
                        // Deliberately NOT routing /ws/** here. This is the
                        // servlet-based MVC flavor of Spring Cloud Gateway,
                        // and a plain HandlerFunctions.http() route cannot
                        // proxy a WebSocket upgrade request — confirmed live:
                        // the handshake through this gateway returns 400,
                        // the identical handshake straight to
                        // realtime-service returns a correct 101 Switching
                        // Protocols. Clients connect to realtime-service
                        // directly for /ws (see frontend/vite.config.ts's
                        // dev proxy); only this service's plain HTTP API
                        // goes through the gateway.
                        .route(path("/api/realtime/**"), http())
                        .filter(lb("realtime-service"))
                        .filter(circuitBreaker("realtime-service-cb", FALLBACK))
                        .build())
                .and(route("skillsphere_backend_api")
                        .route(path("/api/**"), http())
                        // .before(uri("lb://...")) looks equivalent but is
                        // wrong: it only sets a literal URI string, and
                        // nothing then knows to resolve the lb: scheme
                        // against Eureka — that resolution is exactly what
                        // the lb() *filter* does, which a plain before-filter
                        // setting a URI does not. Confirmed by a live
                        // failure: "Unroutable protocol scheme:
                        // lb://skillsphere-backend".
                        .filter(lb("skillsphere-backend"))
                        .filter(circuitBreaker("skillsphere-backend-cb", FALLBACK))
                        .build());
    }
}
