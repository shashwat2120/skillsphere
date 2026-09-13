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
 * The application's security rules.
 *
 * <p><b>This bean is mandatory, not optional.</b> Spring Security 7, shipped
 * with Boot 4, enforces CSRF far more aggressively than 6 did: an API with no
 * explicit {@code SecurityFilterChain} has every state-changing request blocked,
 * and the failure is quiet enough to look like a client bug. Defining the chain
 * deliberately is the only correct response.
 *
 * <p><b>Why CSRF protection is disabled here.</b> Not for convenience — CSRF
 * exists only because browsers attach credentials to cross-site requests
 * automatically, and neither of our credentials behaves that way. The access
 * token travels in an {@code Authorization} header, which a browser will never
 * attach on its own, so a forged cross-site request arrives unauthenticated.
 * The refresh token does live in a cookie, but it is {@code SameSite=Strict},
 * which stops the browser sending it from another origin at all. Both vectors
 * are closed without CSRF tokens, and keeping them would add a token dance to
 * every request for no gain. Were the refresh cookie ever relaxed to
 * {@code SameSite=Lax}, this decision would have to be revisited immediately.
 *
 * <p><b>Sessions are stateless.</b> No {@code JSESSIONID} is created, so there
 * is no session fixation surface and no sticky-session requirement — which is
 * what lets the gateway route freely across instances in Sprint 6.
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
                .csrf(csrf -> csrf.disable())          // see class notes
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        // --- public ---
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                         "/api/auth/refresh", "/api/auth/verify-email",
                                         "/api/auth/forgot-password", "/api/auth/reset-password")
                            .permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                        // A shared passport is meant to be opened by someone
                        // without an account; the share token is the credential.
                        .requestMatchers("/api/public/passports/**").permitAll()

                        // --- role-gated areas ---
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/instructor/**").hasAnyRole("INSTRUCTOR", "ADMIN")

                        // --- operational endpoints are never public ---
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
                                // The API returns JSON, never HTML, so nothing
                                // should ever be loaded from a response of ours.
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
        // Required for the refresh cookie to be sent on the refresh call.
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
