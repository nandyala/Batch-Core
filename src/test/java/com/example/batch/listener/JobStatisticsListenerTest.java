package com.example.batch.listener;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link JobStatisticsListener}.
 *
 * <p>The listener is purely a logger. Tests verify that it handles
 * all common job states without throwing exceptions.
 */
class JobStatisticsListenerTest {

    private final JobStatisticsListener listener = new JobStatisticsListener();

    private JobExecution buildJobExecution() {
        JobInstance instance = new JobInstance(1L, "testJob");
        return new JobExecution(1L, instance, new JobParameters());
    }

    @Test
    void beforeJob_doesNotThrow() {
        JobExecution je = buildJobExecution();
        assertDoesNotThrow(() -> listener.beforeJob(je));
    }

    @Test
    void afterJob_doesNotThrow() {
        JobExecution je = buildJobExecution();
        assertDoesNotThrow(() -> listener.afterJob(je));
    }

    @Test
    void beforeAndAfterJob_withMultipleParams_doesNotThrow() {
        JobInstance instance = new JobInstance(2L, "invoiceJob");
        JobParameters params = new JobParameters();
        JobExecution je = new JobExecution(2L, instance, params);

        assertDoesNotThrow(() -> listener.beforeJob(je));
        assertDoesNotThrow(() -> listener.afterJob(je));
    }
}
