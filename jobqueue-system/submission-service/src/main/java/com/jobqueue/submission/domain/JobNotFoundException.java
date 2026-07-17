package com.jobqueue.submission.domain;

/**
 * Thrown when a job cannot be found by ID.
 */
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String message) {
        super(message);
    }
}
