package com.jobqueue.submission.domain;

/**
 * Thrown when a request is submitted with an idempotency key that has already been processed.
 */
public class DuplicateJobException extends RuntimeException {
    public DuplicateJobException(String message) {
        super(message);
    }
}
