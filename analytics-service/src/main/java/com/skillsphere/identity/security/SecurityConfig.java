package com.skillsphere.identity.security;

// Jackson 3, shipped with Boot 4, moved from com.fasterxml.jackson to
// tools.jackson. Boot 3 code using the old namespace will not compile here.
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * The application's security rules — this service's own copy, not a call
 * back to identity-service, since local JWT validation is what lets every
 * service verify a request without a network round trip per call. Same
 * shared {@code skillsphere.security.jwt.secret} every service signs and
 * verifies with; see the monolith's identity.package-info for the full
 * reasoning behind keeping this local rather than centralised.
 *
 * <p>Scoped much narrower than the monolith's own copy: this service serves
 * exactly one endpoint family, {@code /api/instructor/analytics/**},
 * gated to INSTRUCTOR/ADMIN — no public routes, no admin catch-all, nothing
 * this service doesn't actually expose.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Value("${skillsphere.security.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())          // see identity-service's own SecurityConfig
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Scraped by Prometheus (see monitoring/prometheus/prometheus.yml), which
                        // only reaches this port from inside the Docker Desktop host bridge on
                        // this dev machine -- never from the public internet. Safe specifically
                        // because management.endpoints.web.exposure.include already curates
                        // what exists under /actuator to health, info, circuitbreakers and this
                        // endpoint; nothing sensitive like env, heapdump or beans is ever exposed
                        // for this rule to leak. Everything else under /actuator stays ADMIN-only.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()

                        .requestMatchers("/api/instructor/**").hasAnyRole("INSTRUCTOR", "ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Default deny. Adding an endpoint without a rule makes
                        // it authenticated rather than open, so forgetting to
                        // secure something fails safe.
                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))

                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .contentSecurityPolicy(csp ->
                                csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS for the separate React origin.
     *
     * <p>Origins are configured explicitly and never wildcarded. A wildcard
     * cannot be combined with credentials anyway, and listing origins means a
     * misconfigured deployment fails loudly instead of quietly accepting
     * requests from anywhere.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest request,
                                   jakarta.servlet.http.HttpServletResponse response,
                                   org.springframework.security.core.AuthenticationException ex)
            throws java.io.IOException {
        writeProblem(response, request.getRequestURI(), HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "Authentication is required to access this resource.");
    }

    private void writeForbidden(jakarta.servlet.http.HttpServletRequest request,
                                jakarta.servlet.http.HttpServletResponse response,
                                org.springframework.security.access.AccessDeniedException ex)
            throws java.io.IOException {
        writeProblem(response, request.getRequestURI(), HttpStatus.FORBIDDEN,
                "FORBIDDEN", "You do not have permission to perform this action.");
    }

    /**
     * Security rejections are written in the same RFC 9457 shape as every other
     * error, so a client has exactly one error format to handle rather than one
     * for the application and a different one for the security filters.
     */
    private void writeProblem(jakarta.servlet.http.HttpServletResponse response,
                              String path, HttpStatus status, String code, String detail)
            throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://skillsphere.dev/errors/" + code.toLowerCase()));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
