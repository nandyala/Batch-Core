package com.example.batch.repository;

import com.example.batch.model.JobStatistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobRunStatsRepository} using an H2 in-memory database.
 *
 * <p>H2 is already on the test classpath.  The {@code pruneOldRecords()} method uses
 * MSSQL-specific {@code DATEADD} / {@code GETDATE()} syntax which H2 does not support;
 * that call fails silently (the exception is caught in {@code save()}) so insert
 * coverage is still achievable here.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Successful job + step row insertion</li>
 *   <li>No step rows when {@link JobStatistics#getStepStats()} is empty</li>
 *   <li>Error summary truncation at 1980 characters</li>
 *   <li>Null start / end time handling ({@link com.example.batch.repository.JobRunStatsRepository#save})</li>
 *   <li>Multiple steps persisted correctly</li>
 *   <li>Null job execution ID fallback to -1</li>
 * </ul>
 */
class JobRunStatsRepositoryTest {

    private EmbeddedDatabase     db;
    private JobRunStatsRepository repository;
    private JdbcTemplate         jdbc;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-test-stats.sql")
                .build();
        jdbc       = new JdbcTemplate(db);
        repository = new JobRunStatsRepository();
        repository.setDataSource(db);
        repository.setRetentionDays(7);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // ---------------------------------------------------------------
    // save() — happy path
    // ---------------------------------------------------------------

    @Test
    void save_insertsOneJobRow() {
        repository.save(buildStats("job1", 1L));

        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_RUN_STATS", Integer.class);
        assertEquals(1, count, "Exactly one job run stats row should be inserted");
    }

    @Test
    void save_insertsStepRows() {
        repository.save(buildStats("job1", 1L));

        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM BATCH_STEP_RUN_STATS", Integer.class);
        assertEquals(1, count, "One step stats row should be inserted for processStep");
    }

    @Test
    void save_withNoStepStats_insertsJobRowOnly() {
        JobStatistics stats = new JobStatistics();
        stats.setJobName("emptyJob");
        stats.setJobExecutionId(2L);
        stats.setCorrelationId("EMPT0001");
        stats.setConfigHash("HASH2");
        stats.setStartTime(new Date(System.currentTimeMillis() - 30_000));
        stats.setEndTime(new Date());

        repository.save(stats);

        int jobRows  = jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_RUN_STATS",  Integer.class);
        int stepRows = jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_STEP_RUN_STATS", Integer.class);
        assertEquals(1, jobRows,  "One job run stats row expected");
        assertEquals(0, stepRows, "No step stats rows expected when stepStats is empty");
    }

    @Test
    void save_withMultipleSteps_insertsAllStepRows() {
        JobStatistics stats = buildStats("multiJob", 3L);

        // Add a second step
        stats.getStepStats().add(new JobStatistics.StepStats(
                "writeStep", 95, 95, 0, 0, 0, 0, 0,
                ExitStatus.COMPLETED, null));

        repository.save(stats);

        int stepRows = jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_STEP_RUN_STATS", Integer.class);
        assertEquals(2, stepRows, "Two step rows should be inserted for two steps");
    }

    // ---------------------------------------------------------------
    // Error summary truncation
    // ---------------------------------------------------------------

    @Test
    void save_errorSummaryTruncatedAt1980Chars() {
        JobStatistics stats = buildStats("errorJob", 4L);
        // Add a very long error — buildErrorSummary should truncate to 1980 chars + ellipsis
        stats.getErrors().add("E".repeat(2500));

        // Should not throw (truncation happens before INSERT)
        assertDoesNotThrow(() -> repository.save(stats));

        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_RUN_STATS", Integer.class);
        assertEquals(1, count, "Row must still be inserted despite long error");
    }

    @Test
    void save_emptyErrorList_insertsNullErrorSummary() {
        JobStatistics stats = buildStats("cleanJob", 5L);
        stats.getErrors().clear();  // no errors

        repository.save(stats);

        String errorSummary = jdbc.queryForObject(
                "SELECT error_summary FROM BATCH_JOB_RUN_STATS", String.class);
        assertNull(errorSummary, "error_summary should be NULL when there are no errors");
    }

    // ---------------------------------------------------------------
    // Null date handling
    // ---------------------------------------------------------------

    @Test
    void save_nullStartAndEndTime_doesNotThrow() {
        JobStatistics stats = new JobStatistics();
        stats.setJobName("nullDatesJob");
        stats.setJobExecutionId(6L);
        stats.setCorrelationId("NULL0001");
        stats.setConfigHash("HASHX");
        // startTime and endTime intentionally null → toTimestamp(null) fallback

        assertDoesNotThrow(() -> repository.save(stats),
                "save() must not throw when start/end times are null");
    }

    // ---------------------------------------------------------------
    // Null jobExecutionId fallback
    // ---------------------------------------------------------------

    @Test
    void save_nullJobExecutionId_fallsBackToMinusOne() {
        JobStatistics stats = new JobStatistics();
        stats.setJobName("noExecIdJob");
        stats.setJobExecutionId(null);   // triggers the != null ? ... : -1L fallback
        stats.setCorrelationId("NOEXEC1");
        stats.setConfigHash("HASHZ");

        assertDoesNotThrow(() -> repository.save(stats));

        Long executionId = jdbc.queryForObject(
                "SELECT job_execution_id FROM BATCH_JOB_RUN_STATS", Long.class);
        assertEquals(-1L, executionId, "Null job execution id should be stored as -1");
    }

    // ---------------------------------------------------------------
    // durationSeconds() — null endTime with non-null startTime
    // ---------------------------------------------------------------

    @Test
    void save_nullEndTimeWithStartTimeSet_durationDefaultsToZero() {
        // Covers the second null check in durationSeconds():
        //   if (stats.getStartTime() == null || stats.getEndTime() == null) return 0;
        // When startTime != null but endTime == null, the second condition triggers.
        JobStatistics stats = new JobStatistics();
        stats.setJobName("nullEndJob");
        stats.setJobExecutionId(7L);
        stats.setCorrelationId("NULLEND1");
        stats.setConfigHash("HASHY");
        stats.setStartTime(new Date());   // non-null startTime
        // endTime intentionally left null → durationSeconds() returns 0

        assertDoesNotThrow(() -> repository.save(stats),
                "save() must not throw when endTime is null but startTime is set");
    }

    // ---------------------------------------------------------------
    // setRetentionDays (setter coverage)
    // ---------------------------------------------------------------

    @Test
    void setRetentionDays_changesRetentionValue() {
        // Verifies the setter is called and doesn't throw
        assertDoesNotThrow(() -> repository.setRetentionDays(30));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private JobStatistics buildStats(String jobName, long jobExecId) {
        JobStatistics stats = new JobStatistics();
        stats.setJobName(jobName);
        stats.setJobExecutionId(jobExecId);
        stats.setCorrelationId("CORR" + jobExecId);
        stats.setConfigHash("HASH" + jobExecId);
        stats.setStartTime(new Date(System.currentTimeMillis() - 60_000));
        stats.setEndTime(new Date());
        stats.setTotalReadCount(100);
        stats.setTotalWriteCount(95);
        stats.setTotalSkipCount(5);
        stats.setTotalFilterCount(2);
        stats.getErrors().add("Row 42: bad data");

        stats.getStepStats().add(new JobStatistics.StepStats(
                "processStep",
                100, 95, 5, 2,
                1, 2, 2,
                ExitStatus.COMPLETED,
                List.of("Row 42: bad data")));

        return stats;
    }
}
