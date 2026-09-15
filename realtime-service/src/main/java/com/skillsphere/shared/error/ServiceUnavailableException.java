package com.skillsphere.shared.error;

import org.springframework.http.HttpStatus;

/**
 * A dependency the request needed is unreachable right now — the local
 * Ollama server being down is the current example, not the only one this
 * is meant for.
 *
 * <p>Distinct from every other {@link ApiException} subclass in one way:
 * the message is genuinely actionable ("is Ollama running?") rather than
 * just explanatory, and that message is exactly what a demo presenter needs
 * to see instead of a generic "something went wrong" — the difference
 * between recovering in five seconds and not understanding what broke.
 */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message, cause);
    }
}
