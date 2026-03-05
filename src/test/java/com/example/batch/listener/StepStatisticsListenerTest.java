package com.example.batch.listener;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StepStatisticsListener}.
 *
 * <p>The listener only logs — tests verify it does not throw and
 * does not interfere with the step's own exit status.
 */
class StepStatisticsListenerTest {

    private final StepStatisticsListener listener = new StepStatisticsListener();

    private StepExecution buildStepExecution(String stepName) {
        JobInstance instance = new JobInstance(1L, "testJob");
        JobExecution jobExec  = new JobExecution(instance, 1L, new JobParameters());
        return new StepExecution(stepName, jobExec);
    }

    @Test
    void beforeStep_doesNotThrow() {
        StepExecution se = buildStepExecution("myStep");
        assertDoesNotThrow(() -> listener.beforeStep(se));
    }

    @Test
    void afterStep_returnsNull_doesNotOverrideExitStatus() {
        StepExecution se = buildStepExecution("myStep");
        // Set a specific exit status before calling afterStep
        se.setExitStatus(ExitStatus.COMPLETED);

        ExitStatus result = listener.afterStep(se);

        assertNull(result, "afterStep must return null to preserve the step's own exit status");
    }

    @Test
    void afterStep_withSkipsAndRollbacks_doesNotThrow() {
        StepExecution se = buildStepExecution("processStep");
        // Spring Batch 5 removed incrementReadCount/incrementWriteCount/incrementFilterCount
        // and the individual skip-count increment methods; use setters instead.
        se.setReadCount(2);
        se.setWriteCount(1);
        se.setFilterCount(1);
        se.incrementCommitCount();    // still public in SB 5
        se.incrementRollbackCount();  // still public in SB 5
        se.setReadSkipCount(1);
        se.setProcessSkipCount(1);
        se.setWriteSkipCount(1);

        assertDoesNotThrow(() -> listener.afterStep(se),
                "afterStep should log all counts without throwing");
    }
}
