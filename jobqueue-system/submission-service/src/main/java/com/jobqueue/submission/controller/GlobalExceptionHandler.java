package com.jobqueue.submission.controller;

import com.jobqueue.submission.domain.DuplicateJobException;
import com.jobqueue.submission.domain.JobNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler using Spring's RFC 7807 ProblemDetail.
 * Maps domain exceptions to appropriate HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(JobNotFoundException.class)
    @SuppressWarnings("null")
    public ResponseEntity<ProblemDetail> handleNotFound(JobNotFoundException ex) {
        log.warn("[GlobalExceptionHandler] JobNotFound: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://jobqueue.io/errors/job-not-found"));
        detail.setTitle("Job Not Found");
        detail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(DuplicateJobException.class)
    @SuppressWarnings("null")
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateJobException ex) {
        log.warn("[GlobalExceptionHandler] DuplicateJob: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("https://jobqueue.io/errors/duplicate-job"));
        detail.setTitle("Duplicate Job");
        detail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @SuppressWarnings("null")
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setType(URI.create("https://jobqueue.io/errors/validation-failed"));
        detail.setTitle("Validation Failed");
        detail.setDetail("One or more fields failed validation");
        detail.setProperty("errors", errors);
        detail.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(Exception.class)
    @SuppressWarnings("null")
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        log.error("[GlobalExceptionHandler] Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        detail.setType(URI.create("https://jobqueue.io/errors/internal"));
        detail.setTitle("Internal Server Error");
        detail.setProperty("timestamp", Instant.now());
        return ResponseEntity.internalServerError().body(detail);
    }
}
