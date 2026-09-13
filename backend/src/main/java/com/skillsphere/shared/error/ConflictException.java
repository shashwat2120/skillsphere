package com.skillsphere.shared.error;

import org.springframework.http.HttpStatus;

/**
 * The request is valid but conflicts with the current state — a duplicate
 * enrolment, a second active learning path, a viva already completed.
 */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
