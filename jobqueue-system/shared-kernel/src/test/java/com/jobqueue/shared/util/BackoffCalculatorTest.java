package com.jobqueue.shared.util;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class BackoffCalculatorTest {

    @Test
    void shouldCalculateIncreasingBackoff() {
        long delay1 = BackoffCalculator.getDelayForAttempt(1);
        long delay2 = BackoffCalculator.getDelayForAttempt(2);
        long delay3 = BackoffCalculator.getDelayForAttempt(3);

        assertThat(delay1).isZero();
        assertThat(delay2).isEqualTo(100);
        assertThat(delay3).isEqualTo(200);
        assertThat(delay3).isGreaterThan(delay2);
    }

    /**
     * Full-jitter backoff is intentionally random in [0, cappedDelay).
     * We cannot assert retry2 > retry1 since jitter can produce any value in range.
     * Instead we validate that each retry falls within [originalTime, originalTime + maxPossibleDelay].
     *
     * attempt=1 → exponential = 100 * 2^0 = 100ms → jitter in [0, 100)
     * attempt=2 → exponential = 100 * 2^1 = 200ms → jitter in [0, 200)
     */
    @Test
    void shouldApplyJitterToRetryTime() {
        Instant now = Instant.now();

        Instant retry1 = BackoffCalculator.calculateNextRetry(1, now);
        Instant retry2 = BackoffCalculator.calculateNextRetry(2, now);

        // retry1 must fall within [now, now + 100ms)
        assertThat(retry1).isAfterOrEqualTo(now);
        assertThat(retry1).isBefore(now.plusMillis(100));

        // retry2 must fall within [now, now + 200ms)
        assertThat(retry2).isAfterOrEqualTo(now);
        assertThat(retry2).isBefore(now.plusMillis(200));
    }
}
