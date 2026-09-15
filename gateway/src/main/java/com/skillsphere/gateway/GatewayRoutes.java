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
 * <p>Right now that is one route, because there is one service: the monolith,
 * registered with Eureka as {@code skillsphere-backend}. The point of routing
 * through {@code lb://skillsphere-backend} rather than a fixed
 * {@code http://localhost:8080} is that this file does not change when that
 * changes — the day {@code assessment} is cut out into its own service, this
 * gateway gains a second route pointed at {@code lb://assessment-service} and
 * loses nothing that already worked, because every client was already talking
 * to the gateway's own origin, never to the monolith directly.
 */
@Configuration
public class GatewayRoutes {

    @Bean
    public RouterFunction<ServerResponse> apiRoute() {
        return route("skillsphere_backend_api")
                .route(path("/api/**"), http())
                // .before(uri("lb://...")) looks equivalent but is wrong: it
                // only sets a literal URI string, and nothing then knows to
                // resolve the lb: scheme against Eureka — that resolution is
                // exactly what the lb() *filter* does, which a plain
                // before-filter setting a URI does not. Confirmed by a live
                // failure: "Unroutable protocol scheme: lb://skillsphere-backend".
                .filter(lb("skillsphere-backend"))
                .build();
    }
}
