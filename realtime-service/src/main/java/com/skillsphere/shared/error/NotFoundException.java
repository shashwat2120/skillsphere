package com.skillsphere.shared.error;

import org.springframework.http.HttpStatus;

/**
 * The requested resource does not exist, or the caller is not allowed to know
 * that it does.
 *
 * <p>Those two cases are deliberately collapsed. Returning 404 rather than 403
 * for a resource the caller may not see prevents an enumeration oracle: a
 * distinct "forbidden" response confirms the resource exists, which is enough
 * to map out other learners, submissions or courses by walking ids.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found: " + id);
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
