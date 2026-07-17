package com.jobqueue.submission.domain;

import com.jobqueue.shared.model.Job;
import com.jobqueue.shared.model.JobStatus;
import com.jobqueue.shared.model.Priority;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobTest {

    @Test
    void shouldCreatePendingJob() {
        Job job = Job.create("email.send", "{\"to\":\"test@example.com\"}",
                            Priority.HIGH, 3, "key123", 30000L);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void shouldTransitionToLeased() {
        Job job = Job.create("test", "{}", Priority.NORMAL, 3, null, 30000L);
        job.transitionTo(JobStatus.LEASED);

        assertThat(job.getStatus()).isEqualTo(JobStatus.LEASED);
    }

    @Test
    void shouldNotAllowInvalidTransition() {
        Job job = Job.create("test", "{}", Priority.NORMAL, 3, null, 30000L);

        // PENDING → COMPLETED is not a valid transition; it must throw
        assertThatThrownBy(() -> job.transitionTo(JobStatus.COMPLETED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot transition from PENDING to COMPLETED");
    }

    @Test
    void shouldRecordFailureAndMoveToDeadAfterMaxRetries() {
        Job job = Job.create("test", "{}", Priority.NORMAL, 2, null, 30000L);

        job.recordFailure("First failure");
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(1);

        job.recordFailure("Second failure");
        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);
        assertThat(job.getAttempts()).isEqualTo(2);
    }

    @Test
    void shouldCheckIfReadyToExecute() {
        Job pendingJob = Job.create("test", "{}", Priority.NORMAL, 3, null, 30000L);
        assertThat(pendingJob.isReadyToExecute()).isTrue();

        Job failedJob = Job.create("test", "{}", Priority.NORMAL, 3, null, 30000L);
        failedJob.recordFailure("Error");
        failedJob.scheduleRetry(Instant.now().plusSeconds(60));
        assertThat(failedJob.isReadyToExecute()).isFalse();
    }
}
