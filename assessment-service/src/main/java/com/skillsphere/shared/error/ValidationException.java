package com.skillsphere.shared.error;

import org.springframework.http.HttpStatus;

/**
 * A business rule rejected the request, as opposed to a malformed payload.
 *
 * <p>Bean Validation covers shape — required fields, lengths, ranges. This
 * covers meaning: a prerequisite edge that would close a cycle in the skill
 * graph, an item whose options contain no correct answer, a viva answer
 * submitted after its turn expired.
 */
public class ValidationException extends ApiException {

    public ValidationException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
