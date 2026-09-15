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
 * The application's security rules — this service's own copy, local JWT
 * validation against the same shared secret every service verifies with.
 *
 * <p>{@code /api/public/passports/**} is the one genuinely unauthenticated
 * route: a shared passport is meant to be opened by someone with no
 * account at all, and the share token itself is the credential — matching
 * the monolith's own permitAll rule for this exact path.
 * {@code /api/careers/**} and {@code /api/passport/**} need only a signed-in
 * caller, no role gate.
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
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                        // A shared passport is meant to be opened by someone
                        // without an account; the share token is the credential.
                        .requestMatchers("/api/public/passports/**").permitAll()

                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // Default deny. /api/careers/** and /api/passport/**
                        // fall through to here — any signed-in user, no role
                        // gate, matching the monolith's own rule for both.
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
