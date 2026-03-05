package com.example.batch.model;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

/**
 * Holds aggregated execution statistics for a single job run.
 *
 * <p>Populated by {@link com.example.batch.tasklet.StatisticsAndEmailTasklet}
 * as the last step of every job. Contains both per-step breakdowns and
 * rolled-up totals across all business steps.
 */
public class JobStatistics {

    // ---------------------------------------------------------------
    // Job-level fields
    // ---------------------------------------------------------------
    private String      jobName;
    private Long        jobExecutionId;
    private BatchStatus batchStatus;
    private ExitStatus  exitStatus;
    private Date        startTime;
    private Date        endTime;

    // ---------------------------------------------------------------
    // Observability fields (populated by StatisticsAndEmailTasklet)
    // ---------------------------------------------------------------

    /** Short run ID from MDC (e.g. {@code A3F9BC12}) — consistent across all log lines + email. */
    private String correlationId = "N/A";

    /**
     * Hex hash of non-sensitive batch config properties at run time.
     * Lets you detect when configuration changed between consecutive runs.
     */
    private String configHash = "N/A";

    /**
     * Top-N skip-event error messages aggregated across all business steps.
     * Populated from {@link com.example.batch.listener.StepErrorCollector}.
     */
    private final List<String> errors = new ArrayList<>();

    // ---------------------------------------------------------------
    // Aggregated totals across all business steps
    // ---------------------------------------------------------------
    private long totalReadCount;
    private long totalWriteCount;
    private long totalSkipCount;
    private long totalFilterCount;
    private long totalReadSkipCount;
    private long totalProcessSkipCount;
    private long totalWriteSkipCount;

    // ---------------------------------------------------------------
    // Per-step breakdown
    // ---------------------------------------------------------------
    private final List<StepStats> stepStats = new ArrayList<>();

    // ---------------------------------------------------------------
    // Computed helpers
    // ---------------------------------------------------------------

    /**
     * Returns a human-readable status derived from step exit codes.
     *
     * <p>When this tasklet runs, the job's {@link BatchStatus} is still
     * {@code STARTED}. We inspect step exit codes to determine the true outcome.
     */
    public String getComputedStatus() {
        for (StepStats s : stepStats) {
            if (ExitStatus.FAILED.getExitCode().equals(s.getExitStatus().getExitCode())) {
                return "FAILED";
            }
        }
        return "COMPLETED";
    }

    /** Human-readable duration, e.g. {@code "2m 15s"} or {@code "45s"}. */
    public String getFormattedDuration() {
        if (startTime == null || endTime == null) return "N/A";
        long millis = endTime.getTime() - startTime.getTime();
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    // ---------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------

    public String      getJobName()               { return jobName; }
    public void        setJobName(String v)        { this.jobName = v; }

    public Long        getJobExecutionId()         { return jobExecutionId; }
    public void        setJobExecutionId(Long v)   { this.jobExecutionId = v; }

    public BatchStatus getBatchStatus()            { return batchStatus; }
    public void        setBatchStatus(BatchStatus v){ this.batchStatus = v; }

    public ExitStatus  getExitStatus()             { return exitStatus; }
    public void        setExitStatus(ExitStatus v) { this.exitStatus = v; }

    public Date        getStartTime()              { return startTime; }
    public void        setStartTime(Date v)        { this.startTime = v; }

    public Date        getEndTime()                { return endTime; }
    public void        setEndTime(Date v)          { this.endTime = v; }

    public String      getCorrelationId()          { return correlationId; }
    public void        setCorrelationId(String v)  { this.correlationId = v; }

    public String      getConfigHash()             { return configHash; }
    public void        setConfigHash(String v)     { this.configHash = v; }

    public List<String> getErrors()                { return errors; }

    public long getTotalReadCount()                { return totalReadCount; }
    public void setTotalReadCount(long v)          { this.totalReadCount = v; }

    public long getTotalWriteCount()               { return totalWriteCount; }
    public void setTotalWriteCount(long v)         { this.totalWriteCount = v; }

    public long getTotalSkipCount()                { return totalSkipCount; }
    public void setTotalSkipCount(long v)          { this.totalSkipCount = v; }

    public long getTotalFilterCount()              { return totalFilterCount; }
    public void setTotalFilterCount(long v)        { this.totalFilterCount = v; }

    public long getTotalReadSkipCount()            { return totalReadSkipCount; }
    public void setTotalReadSkipCount(long v)      { this.totalReadSkipCount = v; }

    public long getTotalProcessSkipCount()         { return totalProcessSkipCount; }
    public void setTotalProcessSkipCount(long v)   { this.totalProcessSkipCount = v; }

    public long getTotalWriteSkipCount()           { return totalWriteSkipCount; }
    public void setTotalWriteSkipCount(long v)     { this.totalWriteSkipCount = v; }

    public List<StepStats> getStepStats()          { return stepStats; }

    // ---------------------------------------------------------------
    // Per-step detail record
    // ---------------------------------------------------------------

    public static class StepStats {

        private final String       stepName;
        private final long         readCount;
        private final long         writeCount;
        private final long         skipCount;
        private final long         filterCount;
        private final long         readSkipCount;
        private final long         processSkipCount;
        private final long         writeSkipCount;
        private final ExitStatus   exitStatus;
        /** Error messages captured by StepErrorCollector for this step. */
        private final List<String> stepErrors;

        public StepStats(String stepName,
                         long readCount,
                         long writeCount,
                         long skipCount,
                         long filterCount,
                         long readSkipCount,
                         long processSkipCount,
                         long writeSkipCount,
                         ExitStatus exitStatus,
                         List<String> stepErrors) {
            this.stepName         = stepName;
            this.readCount        = readCount;
            this.writeCount       = writeCount;
            this.skipCount        = skipCount;
            this.filterCount      = filterCount;
            this.readSkipCount    = readSkipCount;
            this.processSkipCount = processSkipCount;
            this.writeSkipCount   = writeSkipCount;
            this.exitStatus       = exitStatus;
            this.stepErrors       = stepErrors != null ? stepErrors
                                                       : Collections.emptyList();
        }

        public String       getStepName()        { return stepName; }
        public long         getReadCount()        { return readCount; }
        public long         getWriteCount()       { return writeCount; }
        public long         getSkipCount()        { return skipCount; }
        public long         getFilterCount()      { return filterCount; }
        public long         getReadSkipCount()    { return readSkipCount; }
        public long         getProcessSkipCount() { return processSkipCount; }
        public long         getWriteSkipCount()   { return writeSkipCount; }
        public ExitStatus   getExitStatus()       { return exitStatus; }
        public List<String> getStepErrors()       { return stepErrors; }
    }
}
