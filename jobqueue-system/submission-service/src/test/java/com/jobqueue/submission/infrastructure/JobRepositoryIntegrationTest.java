package com.jobqueue.submission.infrastructure;

import com.jobqueue.shared.model.Job;
import com.jobqueue.shared.model.Priority;
import com.jobqueue.submission.infrastructure.persistence.JobRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@JdbcTest
@Import(JobRepositoryImpl.class)
class JobRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JobRepositoryImpl jobRepository;

    @Test
    void shouldSaveAndRetrieveJob() {
        Job job = Job.create("email.send", "{\"to\":\"test@example.com\"}",
                            Priority.HIGH, 3, "key123", 30000L);

        jobRepository.save(job);

        var retrieved = jobRepository.findById(job.getJobId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getType()).isEqualTo("email.send");
    }
}
