package com.skillsphere.identity.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Checks a candidate password against HaveIBeenPwned's breach corpus using
 * k-anonymity, per SKILLSPHERE.md §6.
 *
 * <p><b>The password never leaves this process.</b> Only the first five hex
 * characters of its SHA-1 hash are sent to the API; HIBP returns every
 * breached hash sharing that prefix (typically several hundred), and the
 * match is completed locally against the remaining 35 characters. HIBP never
 * sees the password, the full hash, or which specific hash matched.
 *
 * <p><b>Fails open.</b> This is a free, best-effort external API with no SLA,
 * checked synchronously on the registration and password-reset paths. If it
 * is slow, down, or rate-limits us, the request proceeds as if the password
 * were not found in the corpus — refusing signup because a third-party
 * service is unreachable would be a self-inflicted outage. The short timeout
 * bounds how long a request can be held up by this check.
 */
@Slf4j
@Component
public class HaveIBeenPwnedService {

    private static final String BASE_URL = "https://api.pwnedpasswords.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;

    public HaveIBeenPwnedService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();
    }

    /** @return true only if the API was reachable AND reported this password as breached. */
    public boolean isPwned(String password) {
        try {
            String sha1Hex = sha1Hex(password).toUpperCase(Locale.ROOT);
            String prefix = sha1Hex.substring(0, 5);
            String suffix = sha1Hex.substring(5);

            String body = restClient.get()
                    .uri("/range/{prefix}", prefix)
                    // Padded responses (constant-size, extra decoy suffixes) are a
                    // further anonymity improvement HIBP supports opt-in; harmless
                    // to request and slightly better for whoever is watching traffic.
                    .header("Add-Padding", "true")
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return false;
            }
            for (String line : body.split("\r?\n")) {
                int colon = line.indexOf(':');
                String candidateSuffix = colon >= 0 ? line.substring(0, colon) : line;
                if (candidateSuffix.equalsIgnoreCase(suffix)) {
                    return true;
                }
            }
            return false;
        } catch (RestClientException ex) {
            log.warn("HaveIBeenPwned check unreachable — allowing password through (fail open): {}",
                    ex.toString());
            return false;
        } catch (Exception ex) {
            log.warn("HaveIBeenPwned check failed unexpectedly — allowing password through (fail open)", ex);
            return false;
        }
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-1 is mandated by the platform; absence means a broken JVM.
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }
}
