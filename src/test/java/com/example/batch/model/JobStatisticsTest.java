package com.example.batch.model;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobStatistics}.
 *
 * <p>Tests the two computed helpers:
 * <ul>
 *   <li>{@link JobStatistics#getComputedStatus()} — infers overall result from step exit codes.</li>
 *   <li>{@link JobStatistics#getFormattedDuration()} — human-readable run time string.</li>
 * </ul>
 */
class JobStatisticsTest {

    // ---------------------------------------------------------------
    // getComputedStatus
    // ---------------------------------------------------------------

    @Test
    void computedStatus_noSteps_returnsCompleted() {
        JobStatistics stats = new JobStatistics();
        // No steps recorded — default is COMPLETED
        assertEquals("COMPLETED", stats.getComputedStatus());
    }

    @Test
    void computedStatus_allStepsCompleted_returnsCompleted() {
        JobStatistics stats = new JobStatistics();
        stats.getStepStats().add(stepStats("step1", ExitStatus.COMPLETED));
        stats.getStepStats().add(stepStats("step2", ExitStatus.COMPLETED));

        assertEquals("COMPLETED", stats.getComputedStatus());
    }

    @Test
    void computedStatus_oneStepFailed_returnsFailed() {
        JobStatistics stats = new JobStatistics();
        stats.getStepStats().add(stepStats("step1", ExitStatus.COMPLETED));
        stats.getStepStats().add(stepStats("step2", ExitStatus.FAILED));

        assertEquals("FAILED", stats.getComputedStatus(),
                "Even one failed step should make the overall status FAILED");
    }

    @Test
    void computedStatus_allStepsFailed_returnsFailed() {
        JobStatistics stats = new JobStatistics();
        stats.getStepStats().add(stepStats("step1", ExitStatus.FAILED));
        stats.getStepStats().add(stepStats("step2", ExitStatus.FAILED));

        assertEquals("FAILED", stats.getComputedStatus());
    }

    @Test
    void computedStatus_customExitStatus_returnsCompleted() {
        // Non-standard (not FAILED) exit codes should not trigger FAILED status
        JobStatistics stats = new JobStatistics();
        stats.getStepStats().add(stepStats("step1", new ExitStatus("NOOP")));

        assertEquals("COMPLETED", stats.getComputedStatus(),
                "Only FAILED exit code triggers FAILED status");
    }

    // ---------------------------------------------------------------
    // getFormattedDuration
    // ---------------------------------------------------------------

    @Test
    void formattedDuration_nullStartTime_returnsNA() {
        JobStatistics stats = new JobStatistics();
        stats.setEndTime(new Date());
        // startTime is null

        assertEquals("N/A", stats.getFormattedDuration());
    }

    @Test
    void formattedDuration_nullEndTime_returnsNA() {
        JobStatistics stats = new JobStatistics();
        stats.setStartTime(new Date());
        // endTime is null

        assertEquals("N/A", stats.getFormattedDuration());
    }

    @Test
    void formattedDuration_bothNull_returnsNA() {
        JobStatistics stats = new JobStatistics();
        assertEquals("N/A", stats.getFormattedDuration());
    }

    @Test
    void formattedDuration_underOneMinute_showsSecondsOnly() {
        JobStatistics stats = new JobStatistics();
        long now = System.currentTimeMillis();
        stats.setStartTime(new Date(now));
        stats.setEndTime(new Date(now + 45_000L));  // 45 seconds

        String result = stats.getFormattedDuration();
        assertEquals("45s", result,
                "Duration under 60s should show seconds only: " + result);
    }

    @Test
    void formattedDuration_exactlyOneMinute_showsMinutesAndZeroSeconds() {
        JobStatistics stats = new JobStatistics();
        long now = System.currentTimeMillis();
        stats.setStartTime(new Date(now));
        stats.setEndTime(new Date(now + 60_000L));  // exactly 1 minute

        String result = stats.getFormattedDuration();
        assertEquals("1m 0s", result,
                "Exactly 60s should show '1m 0s': " + result);
    }

    @Test
    void formattedDuration_twoMinutesFifteenSeconds_formattedCorrectly() {
        JobStatistics stats = new JobStatistics();
        long now = System.currentTimeMillis();
        stats.setStartTime(new Date(now));
        stats.setEndTime(new Date(now + 135_000L));  // 2m 15s

        String result = stats.getFormattedDuration();
        assertEquals("2m 15s", result,
                "135s should format as '2m 15s': " + result);
    }

    @Test
    void formattedDuration_zeroMillis_returnsZeroSeconds() {
        JobStatistics stats = new JobStatistics();
        long now = System.currentTimeMillis();
        stats.setStartTime(new Date(now));
        stats.setEndTime(new Date(now));  // same instant

        String result = stats.getFormattedDuration();
        assertEquals("0s", result);
    }

    // ---------------------------------------------------------------
    // Getters / setters (smoke-test the POJO)
    // ---------------------------------------------------------------

    @Test
    void settersAndGetters_workCorrectly() {
        JobStatistics stats = new JobStatistics();
        stats.setJobName("invoiceJob");
        stats.setJobExecutionId(42L);
        stats.setCorrelationId("ABC123");
        stats.setConfigHash("DEF456");
        stats.setTotalReadCount(100L);
        stats.setTotalWriteCount(95L);
        stats.setTotalSkipCount(5L);
        stats.setTotalFilterCount(2L);
        stats.setTotalReadSkipCount(1L);
        stats.setTotalProcessSkipCount(2L);
        stats.setTotalWriteSkipCount(2L);

        assertEquals("invoiceJob", stats.getJobName());
        assertEquals(42L, stats.getJobExecutionId());
        assertEquals("ABC123", stats.getCorrelationId());
        assertEquals("DEF456", stats.getConfigHash());
        assertEquals(100L, stats.getTotalReadCount());
        assertEquals(95L, stats.getTotalWriteCount());
        assertEquals(5L, stats.getTotalSkipCount());
        assertEquals(2L, stats.getTotalFilterCount());
        assertEquals(1L, stats.getTotalReadSkipCount());
        assertEquals(2L, stats.getTotalProcessSkipCount());
        assertEquals(2L, stats.getTotalWriteSkipCount());

        // Mutable list — can add errors
        stats.getErrors().add("some error");
        assertEquals(1, stats.getErrors().size());
    }

    // ---------------------------------------------------------------
    // StepStats inner class
    // ---------------------------------------------------------------

    @Test
    void stepStats_nullErrors_defaultsToEmptyList() {
        JobStatistics.StepStats s = new JobStatistics.StepStats(
                "myStep", 100L, 95L, 5L, 2L, 1L, 2L, 2L,
                ExitStatus.COMPLETED, null);  // null errors

        assertNotNull(s.getStepErrors(), "stepErrors must never be null");
        assertTrue(s.getStepErrors().isEmpty());
    }

    @Test
    void stepStats_allFields_accessible() {
        List<String> errors = List.of("err1", "err2");
        JobStatistics.StepStats s = new JobStatistics.StepStats(
                "processStep", 50L, 48L, 2L, 1L, 0L, 1L, 1L,
                ExitStatus.COMPLETED, errors);

        assertEquals("processStep",      s.getStepName());
        assertEquals(50L,                s.getReadCount());
        assertEquals(48L,                s.getWriteCount());
        assertEquals(2L,                 s.getSkipCount());
        assertEquals(1L,                 s.getFilterCount());
        assertEquals(0L,                 s.getReadSkipCount());
        assertEquals(1L,                 s.getProcessSkipCount());
        assertEquals(1L,                 s.getWriteSkipCount());
        assertEquals(ExitStatus.COMPLETED, s.getExitStatus());
        assertEquals(errors,             s.getStepErrors());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static JobStatistics.StepStats stepStats(String name, ExitStatus exitStatus) {
        return new JobStatistics.StepStats(name, 0, 0, 0, 0, 0, 0, 0, exitStatus, null);
    }
}
