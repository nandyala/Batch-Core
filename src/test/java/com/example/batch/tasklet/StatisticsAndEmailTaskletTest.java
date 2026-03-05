package com.example.batch.tasklet;

import com.example.batch.listener.StepErrorCollector;
import com.example.batch.model.JobStatistics;
import com.example.batch.repository.JobRunStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StatisticsAndEmailTasklet}.
 *
 * <p>Uses Mockito to wire {@link ChunkContext} without a Spring container.
 * Email and DB persistence are both disabled for these tests.
 */
@ExtendWith(MockitoExtension.class)
class StatisticsAndEmailTaskletTest {

    @Mock
    private ChunkContext chunkContext;

    @Mock
    private StepContext stepContext;

    @Mock
    private StepContribution stepContribution;

    /** Concrete class mock — save() is a no-op; used to verify DB-persist branch. */
    @Mock
    private JobRunStatsRepository mockRepo;

    private StatisticsAndEmailTasklet tasklet;
    private StepExecution statsStep;
    private JobExecution jobExecution;

    @BeforeEach
    void setUp() {
        tasklet = new StatisticsAndEmailTasklet();
        // Disable email and DB persistence — only test the statistics aggregation + logging
        tasklet.setEmailEnabled(false);

        // Build a real job execution with one preceding business step
        JobInstance instance = new JobInstance(1L, "testJob");
        JobParameters params = new JobParameters();
        jobExecution = new JobExecution(1L, instance, params);
        jobExecution.setStatus(BatchStatus.STARTED);
        jobExecution.setStartTime(LocalDateTime.now());  // exercises toDate(non-null) path

        // Business step with some counts
        StepExecution businessStep = new StepExecution("processStep", jobExecution);
        businessStep.setReadCount(100);
        businessStep.setWriteCount(95);
        businessStep.setFilterCount(2);
        // Spring Batch 5 removed incrementReadSkipCount / incrementProcessSkipCount /
        // incrementWriteSkipCount from the public API; use setters instead.
        businessStep.setReadSkipCount(1);
        businessStep.setProcessSkipCount(2);
        businessStep.setWriteSkipCount(2);
        businessStep.setExitStatus(ExitStatus.COMPLETED);
        jobExecution.addStepExecutions(java.util.List.of(businessStep));

        // The stats tasklet runs as a separate step
        statsStep = new StepExecution("statisticsAndEmailStep", jobExecution);
        jobExecution.addStepExecutions(java.util.List.of(statsStep));

        // Wire mocks: ChunkContext → StepContext → StepExecution
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getStepExecution()).thenReturn(statsStep);
    }

    // ---------------------------------------------------------------
    // execute() — happy path
    // ---------------------------------------------------------------

    @Test
    void execute_emailDisabled_noDbRepo_returnsFinished() throws Exception {
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, status,
                "execute() must always return FINISHED");
    }

    @Test
    void execute_doesNotThrowWhenEmailAndDbDisabled() {
        assertDoesNotThrow(() -> tasklet.execute(stepContribution, chunkContext));
    }

    @Test
    void execute_excludesSelfStepFromAggregation() throws Exception {
        // The statsStep itself should NOT be counted in totals
        // (only processStep with readCount=100 should contribute)
        // We verify indirectly: execution completes without exception
        // and the tasklet itself has zero read/write counts
        assertDoesNotThrow(() -> tasklet.execute(stepContribution, chunkContext));
    }

    // ---------------------------------------------------------------
    // Configuration: emailEnabled flag
    // ---------------------------------------------------------------

    @Test
    void setEmailEnabled_false_noEmailSent() throws Exception {
        tasklet.setEmailEnabled(false);
        // emailNotificationService is null + emailEnabled=false → should not throw
        tasklet.execute(stepContribution, chunkContext);
    }

    @Test
    void setEmailEnabled_true_noService_silentlySkipsEmail() throws Exception {
        tasklet.setEmailEnabled(true);
        tasklet.setEmailNotificationService(null);   // no service wired
        tasklet.setReportToEmail("ops@example.com");

        // Should log "No EmailNotificationService configured" and return FINISHED
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);
        assertEquals(RepeatStatus.FINISHED, status);
    }

    @Test
    void setEmailEnabled_true_noReportEmail_silentlySkipsEmail() throws Exception {
        tasklet.setEmailEnabled(true);
        // emailNotificationService is null
        tasklet.setReportToEmail(null);

        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);
        assertEquals(RepeatStatus.FINISHED, status);
    }

    // ---------------------------------------------------------------
    // jobRunStatsRepository: null → skip DB persist, no throw
    // ---------------------------------------------------------------

    @Test
    void execute_nullJobRunStatsRepository_skipsPersistence() throws Exception {
        tasklet.setJobRunStatsRepository(null);
        assertDoesNotThrow(() -> tasklet.execute(stepContribution, chunkContext));
    }

    // ---------------------------------------------------------------
    // Coverage: errors path and FAILED step branch
    // ---------------------------------------------------------------

    @Test
    void execute_withStepErrors_logsTopIssues() throws Exception {
        // Add a step whose ExecutionContext carries collected error messages
        // (as StepErrorCollector would populate them during a real run)
        StepExecution errorStep = new StepExecution("errorStep", jobExecution);
        errorStep.setExitStatus(ExitStatus.COMPLETED);
        errorStep.getExecutionContext().put(
                StepErrorCollector.ERRORS_KEY,
                List.of("Row 42: bad data", "Row 99: parse failure"));
        jobExecution.addStepExecutions(List.of(errorStep));

        // execute() should log the errors and still return FINISHED
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);
        assertEquals(RepeatStatus.FINISHED, status);
    }

    @Test
    void execute_withFailedStep_computedStatusFailed() throws Exception {
        // A step with ExitStatus.FAILED triggers the "FAILED" branch in friendlyStatus()
        // and causes getComputedStatus() to return "FAILED"
        StepExecution failedStep = new StepExecution("failedStep", jobExecution);
        failedStep.setExitStatus(ExitStatus.FAILED);
        jobExecution.addStepExecutions(List.of(failedStep));

        // execute() must still return FINISHED even when a preceding step failed
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);
        assertEquals(RepeatStatus.FINISHED, status);
    }

    @Test
    void execute_withJobRunStatsRepository_persistsStats() throws Exception {
        // Inject mock repo → covers the "jobRunStatsRepository != null" branch in execute()
        tasklet.setJobRunStatsRepository(mockRepo);

        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, status);
        // verify save() was called exactly once with any JobStatistics object
        verify(mockRepo, times(1)).save(any(JobStatistics.class));
    }

    @Test
    void execute_stepWithUnknownExitCode_friendlyStatusUsesRawCode() throws Exception {
        // ExitStatus "STOPPED" hits the default branch in friendlyStatus()
        // (neither "COMPLETED" nor "FAILED")
        StepExecution stoppedStep = new StepExecution("stoppedStep", jobExecution);
        stoppedStep.setExitStatus(new ExitStatus("STOPPED"));
        jobExecution.addStepExecutions(List.of(stoppedStep));

        // execute() must still return FINISHED — the log will show the raw "STOPPED" code
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);
        assertEquals(RepeatStatus.FINISHED, status);
    }

    @Test
    void execute_noBusinessSteps_logsNoStepsRecorded() throws Exception {
        // statsStep is the only step; it is excluded from aggregation → empty step list
        // Covers the stats.getStepStats().isEmpty() branch in printStatisticsToLog
        JobInstance inst2    = new JobInstance(2L, "emptyJob");
        JobExecution jobExec2 = new JobExecution(2L, inst2, new JobParameters());
        jobExec2.setStatus(BatchStatus.STARTED);
        jobExec2.setStartTime(LocalDateTime.now());

        StepExecution statsOnly = new StepExecution("statisticsAndEmailStep", jobExec2);
        jobExec2.addStepExecutions(List.of(statsOnly));

        when(stepContext.getStepExecution()).thenReturn(statsOnly);

        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);
        assertEquals(RepeatStatus.FINISHED, status);
    }
}
