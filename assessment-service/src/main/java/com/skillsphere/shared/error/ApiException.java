package com.skillsphere.shared.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for expected, business-level failures — the ones a client should be told
 * about precisely.
 *
 * <p>The distinction that matters: subclasses of this carry a message intended
 * for a caller, while anything else that escapes a controller is treated as an
 * internal fault and reported generically. That boundary is a security control,
 * not tidiness. Reflecting arbitrary exception text back to a client is how
 * table names, SQL fragments and file paths leak, and Boot is configured with
 * {@code include-message: never} for exactly this reason.
 *
 * <p>Each failure also carries a stable machine-readable {@code code}, so the
 * frontend can branch on {@code EMAIL_ALREADY_REGISTERED} rather than matching
 * on English text that will change.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }
}
