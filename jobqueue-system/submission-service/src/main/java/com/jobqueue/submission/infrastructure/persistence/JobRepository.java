package com.jobqueue.submission.infrastructure.persistence;

import com.jobqueue.shared.model.Job;
import java.util.Optional;

public interface JobRepository {
    void save(Job job);
    Optional<Job> findById(String jobId);
    void updateStatus(String jobId, String status, String error);
}
