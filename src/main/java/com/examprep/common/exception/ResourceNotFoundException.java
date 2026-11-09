package com.examprep.common.exception;

/**
 * Thrown when a record does not exist <em>or</em> is not owned by the requesting student.
 * The two cases are deliberately indistinguishable so that ids cannot be probed.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
