package com.example.batch.tasklet;

import com.example.batch.listener.StepErrorCollector;
import com.example.batch.model.JobStatistics;
import com.example.batch.repository.JobRunStatsRepository;
import com.example.batch.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Reusable final-step Tasklet that:
 * <ol>
 *   <li>Aggregates read / write / skip / filter counts from all preceding steps.</li>
 *   <li>Prints a formatted statistics report to the application log.</li>
 *   <li>Sends an HTML email report via {@link EmailNotificationService}.</li>
 * </ol>
 *
 * <h3>Adding to any job (XML snippet)</h3>
 * <pre>
 *   &lt;!-- statisticsAndEmailTasklet bean is declared in batch-listeners.xml --&gt;
 *
 *   &lt;batch:job id="myJob"&gt;
 *     &lt;batch:step id="step1" next="statisticsAndEmailStep"&gt; ... &lt;/batch:step&gt;
 *     &lt;batch:step id="step2" next="statisticsAndEmailStep"&gt; ... &lt;/batch:step&gt;
 *
 *     &lt;!-- Always the last step --&gt;
 *     &lt;batch:step id="statisticsAndEmailStep"&gt;
 *       &lt;batch:tasklet ref="statisticsAndEmailTasklet"
 *                      transaction-manager="transactionManager"/&gt;
 *     &lt;/batch:step&gt;
 *   &lt;/batch:job&gt;
 * </pre>
 *
 * <p>No Spring annotations — all wiring done through XML.
 */
public class StatisticsAndEmailTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(StatisticsAndEmailTasklet.class);
    private static final String LINE = "=".repeat(65);
    private static final String THIN = "-".repeat(65);

    /** Injected via XML. Set to {@code null} to disable email silently. */
    private EmailNotificationService emailNotificationService;

    /** Email recipient — injected via XML {@code <property>}. */
    private String reportToEmail;

    /**
     * Feature flag — injected via {@code batch.email.enabled} property.
     * Defaults to {@code true}. Set to {@code false} to skip email (log report still runs).
     */
    private boolean emailEnabled = true;

    /**
     * Optional — persists run stats to DB for 7-day history.
     * Injected via XML; {@code null} = skip DB persistence silently.
     */
    private JobRunStatsRepository jobRunStatsRepository;

    // ---------------------------------------------------------------
    // Tasklet
    // ---------------------------------------------------------------

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        StepExecution currentStep = chunkContext.getStepContext().getStepExecution();

        // Aggregate counts from all preceding business steps (exclude this one)
        JobStatistics stats = buildStatistics(currentStep);

        // 1. Print full report to log
        printStatisticsToLog(stats);

        // 2. Send HTML email — errors are caught so the job still COMPLETED
        sendEmail(stats);

        // 3. Persist run summary to DB (7-day retention)
        if (jobRunStatsRepository != null) {
            jobRunStatsRepository.save(stats);
        }

        return RepeatStatus.FINISHED;
    }

    // ---------------------------------------------------------------
    // Statistics aggregation
    // ---------------------------------------------------------------

    private JobStatistics buildStatistics(StepExecution currentStep) {
        var jobExecution  = currentStep.getJobExecution();
        var jobParams     = jobExecution.getJobParameters();
        String thisStep   = currentStep.getStepName();

        JobStatistics stats = new JobStatistics();
        stats.setJobName(jobExecution.getJobInstance().getJobName());
        stats.setJobExecutionId(jobExecution.getId());
        stats.setBatchStatus(jobExecution.getStatus());   // STARTED at this point
        stats.setExitStatus(jobExecution.getExitStatus());
        stats.setStartTime(toDate(jobExecution.getStartTime()));
        stats.setEndTime(new Date());  // "now" — job hasn't officially ended yet

        // Observability fields from job parameters (set by SpringBatchApplication)
        stats.setCorrelationId(jobParams.getString("correlationId", "N/A"));
        stats.setConfigHash   (jobParams.getString("configHash",    "N/A"));

        long totalRead = 0, totalWrite = 0, totalSkip = 0, totalFilter = 0;
        long totalReadSkip = 0, totalProcessSkip = 0, totalWriteSkip = 0;

        for (StepExecution step : jobExecution.getStepExecutions()) {
            if (step.getStepName().equals(thisStep)) continue; // exclude self

            long rSkip = step.getReadSkipCount();
            long pSkip = step.getProcessSkipCount();
            long wSkip = step.getWriteSkipCount();

            totalRead        += step.getReadCount();
            totalWrite       += step.getWriteCount();
            totalSkip        += step.getSkipCount();
            totalFilter      += step.getFilterCount();
            totalReadSkip    += rSkip;
            totalProcessSkip += pSkip;
            totalWriteSkip   += wSkip;

            // Collect errors captured by StepErrorCollector for this step
            @SuppressWarnings("unchecked")
            List<String> stepErrors = (List<String>)
                    step.getExecutionContext().get(StepErrorCollector.ERRORS_KEY);

            stats.getStepStats().add(new JobStatistics.StepStats(
                    step.getStepName(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    step.getSkipCount(),
                    step.getFilterCount(),
                    rSkip, pSkip, wSkip,
                    step.getExitStatus(),
                    stepErrors));

            // Roll up step errors into job-level error list
            if (stepErrors != null) {
                stats.getErrors().addAll(stepErrors);
            }
        }

        stats.setTotalReadCount(totalRead);
        stats.setTotalWriteCount(totalWrite);
        stats.setTotalSkipCount(totalSkip);
        stats.setTotalFilterCount(totalFilter);
        stats.setTotalReadSkipCount(totalReadSkip);
        stats.setTotalProcessSkipCount(totalProcessSkip);
        stats.setTotalWriteSkipCount(totalWriteSkip);

        return stats;
    }

    // ---------------------------------------------------------------
    // Log report
    // ---------------------------------------------------------------

    private void printStatisticsToLog(JobStatistics stats) {
        log.info(LINE);
        log.info("  JOB COMPLETION REPORT");
        log.info(LINE);
        log.info("  Job              : {}", EmailNotificationService.humanizeJobName(stats.getJobName()));
        log.info("  Correlation ID   : {}", stats.getCorrelationId());
        log.info("  Config Hash      : {}", stats.getConfigHash());
        log.info("  Started          : {}", stats.getStartTime());
        log.info("  Finished         : {}", stats.getEndTime());
        log.info("  Duration         : {}", stats.getFormattedDuration());
        log.info("  Overall Result   : {}", friendlyStatus(stats.getComputedStatus()));
        log.info(THIN);

        if (stats.getStepStats().isEmpty()) {
            log.info("  No steps recorded.");
        } else {
            log.info("  STEP BREAKDOWN");
            log.info(THIN);
            for (JobStatistics.StepStats s : stats.getStepStats()) {
                log.info("  {}  [{}]",
                         EmailNotificationService.humanizeStepName(s.getStepName()),
                         friendlyStatus(s.getExitStatus().getExitCode()));
                log.info("    Records Received  : {}", s.getReadCount());
                log.info("    Successfully Saved: {}", s.getWriteCount());
                log.info("    Flagged for Review: {}", s.getFilterCount());
                log.info("    Errors            : {}", s.getSkipCount());
                log.info(THIN);
            }
        }

        log.info("  TOTALS");
        log.info(THIN);
        log.info("  Records Received  : {}", stats.getTotalReadCount());
        log.info("  Successfully Saved: {}", stats.getTotalWriteCount());
        log.info("  Flagged for Review: {}", stats.getTotalFilterCount());
        log.info("  Errors            : {}", stats.getTotalSkipCount());
        log.info(LINE);

        if (!stats.getErrors().isEmpty()) {
            log.warn("  TOP ISSUES");
            log.warn(THIN);
            for (String err : stats.getErrors()) {
                log.warn("  > {}", err);
            }
            log.warn(LINE);
        }
    }

    // ---------------------------------------------------------------
    // Email dispatch
    // ---------------------------------------------------------------

    private void sendEmail(JobStatistics stats) {
        if (!emailEnabled) {
            log.info("Email reporting is disabled (batch.email.enabled=false) — skipping.");
            return;
        }
        if (emailNotificationService == null) {
            log.info("No EmailNotificationService configured — skipping email report.");
            return;
        }
        if (reportToEmail == null || reportToEmail.isBlank()) {
            log.warn("reportToEmail not configured — skipping email report.");
            return;
        }
        try {
            log.info("Sending statistics email to: {}", reportToEmail);
            emailNotificationService.sendJobReport(stats, reportToEmail);
            log.info("Statistics email sent successfully.");
        } catch (Exception ex) {
            // A failed email must NEVER fail the job itself
            log.error("Failed to send statistics email for '{}': {}",
                      stats.getJobName(), ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Converts batch exit codes to plain business language for the log. */
    private static String friendlyStatus(String exitCode) {
        return switch (exitCode) {
            case "COMPLETED" -> "Completed Successfully";
            case "FAILED"    -> "Completed with Errors";
            default          -> exitCode;
        };
    }

    private Date toDate(LocalDateTime ldt) {
        if (ldt == null) return new Date();
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ---------------------------------------------------------------
    // Setters — called by Spring XML <property> injection
    // ---------------------------------------------------------------

    public void setEmailNotificationService(EmailNotificationService svc) {
        this.emailNotificationService = svc;
    }

    public void setReportToEmail(String reportToEmail) {
        this.reportToEmail = reportToEmail;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public void setJobRunStatsRepository(JobRunStatsRepository jobRunStatsRepository) {
        this.jobRunStatsRepository = jobRunStatsRepository;
    }
}
