package com.skillsphere.shared.error;

import org.springframework.http.HttpStatus;

/**
 * The caller is authenticated but not permitted to perform this action.
 *
 * <p>Use this only where the caller is already entitled to know the resource
 * exists — an instructor editing a course they do not own, for example. Where
 * existence itself is privileged, throw {@link NotFoundException} instead so no
 * information leaks.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
