package com.skillsphere.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

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
 */
@Configuration
public class GatewayRoutes {

    @Bean
    public RouterFunction<ServerResponse> apiRoute() {
        return route("identity_service_auth_api")
                .route(path("/api/auth/**"), http())
                .filter(lb("identity-service"))
                .build()
                .and(route("identity_service_admin_api")
                        .route(path("/api/admin/users/**")
                                .or(path("/api/admin/audit-log/**")), http())
                        .filter(lb("identity-service"))
                        .build())
                .and(route("analytics_service_api")
                        .route(path("/api/instructor/analytics/**"), http())
                        .filter(lb("analytics-service"))
                        .build())
                .and(route("content_service_api")
                        .route(path("/api/courses/**")
                                .or(path("/api/instructor/courses/**")), http())
                        .filter(lb("content-service"))
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
                        .build());
    }
}
