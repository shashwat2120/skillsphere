package com.skillsphere.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns exceptions into RFC 9457 Problem Details.
 *
 * <p>Two rules govern everything here.
 *
 * <p><b>Never leak internals.</b> Only {@link ApiException} carries a message
 * meant for a caller. Anything else is logged in full with a correlation id and
 * reported to the client as a generic failure carrying only that id. Stack
 * traces, SQL fragments and table names reaching a browser is how an attacker
 * maps a system, and it is the default behaviour people forget to switch off.
 *
 * <p><b>Authentication failures must be indistinguishable.</b> A wrong password,
 * an unknown address and a suspended account all produce the same response.
 * Anything else is an account-enumeration oracle that tells an attacker which
 * addresses are worth attacking.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        // Expected business failures: log at INFO, they are not faults.
        log.info("Business rule rejected {} {}: {} ({})",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex.getCode());
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    /**
     * Bean Validation failures. Field errors are safe to return — the client
     * supplied these values, so nothing internal is disclosed by naming them.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid.", request);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // The parser message can echo payload content, so it is logged, not returned.
        log.debug("Unreadable request body on {}", request.getRequestURI(), ex);
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body could not be parsed.", request);
    }

    /**
     * Every authentication failure collapses to one response. See class notes:
     * distinguishing them would confirm which accounts exist.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.debug("Authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Invalid credentials.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.", request);
    }

    /**
     * A concurrent write was prevented rather than lost. This is the system
     * working — 409 tells the client to retry with fresh state.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Optimistic lock conflict on {}", request.getRequestURI());
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This record changed while you were working on it. Please retry.", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        // Full detail to the log, only the id to the caller.
        log.error("Unhandled exception [{}] on {} {}",
                errorId, request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Quote this reference if you contact support.", request);
        problem.setProperty("errorId", errorId);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://skillsphere.dev/errors/" + code.toLowerCase()));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
