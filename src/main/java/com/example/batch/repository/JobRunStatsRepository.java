package com.example.batch.repository;

import com.example.batch.model.JobStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Persists job and step run statistics to the custom
 * {@code BATCH_JOB_RUN_STATS} / {@code BATCH_STEP_RUN_STATS} tables.
 *
 * <h3>Retention</h3>
 * Records older than {@code batch.stats.retention.days} (default 7) are
 * automatically pruned on each save via a cleanup query.  Deleting the parent
 * row cascades to child step rows via {@code ON DELETE CASCADE}.
 *
 * <h3>XML wiring (batch-infrastructure.xml)</h3>
 * <pre>{@code
 * <bean id="jobRunStatsRepository"
 *       class="com.example.batch.repository.JobRunStatsRepository">
 *   <property name="dataSource"     ref="dataSource"/>
 *   <property name="retentionDays"  value="${batch.stats.retention.days:7}"/>
 * </bean>
 * }</pre>
 *
 * <p>No Spring annotations — wired entirely via XML.
 */
public class JobRunStatsRepository {

    private static final Logger log = LoggerFactory.getLogger(JobRunStatsRepository.class);

    private JdbcTemplate jdbc;
    private int retentionDays = 7;

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Saves aggregated statistics for a completed job run.
     * Also prunes records older than {@link #retentionDays}.
     *
     * @param stats populated by {@code StatisticsAndEmailTasklet}
     */
    public void save(JobStatistics stats) {
        try {
            long jobStatsId = insertJobStats(stats);
            insertStepStats(jobStatsId, stats);
            pruneOldRecords();
            log.info("Run statistics saved to DB. id={} correlationId={}",
                     jobStatsId, stats.getCorrelationId());
        } catch (Exception ex) {
            // Stats persistence failure must NOT fail the job itself
            log.error("Failed to persist run statistics: {}", ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------------
    // Insert helpers
    // ---------------------------------------------------------------

    private long insertJobStats(JobStatistics stats) {
        String sql = """
                INSERT INTO BATCH_JOB_RUN_STATS
                       (correlation_id, job_execution_id, job_name, status,
                        start_time, end_time, duration_seconds,
                        total_read, total_written, total_filtered, total_errors,
                        config_hash, error_summary)
                VALUES (?, ?, ?, ?,
                        ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, stats.getCorrelationId());
            ps.setLong  (2, stats.getJobExecutionId() != null ? stats.getJobExecutionId() : -1L);
            ps.setString(3, stats.getJobName());
            ps.setString(4, stats.getComputedStatus());
            ps.setTimestamp(5, toTimestamp(stats.getStartTime()));
            ps.setTimestamp(6, toTimestamp(stats.getEndTime()));
            ps.setInt   (7, durationSeconds(stats));
            ps.setLong  (8, stats.getTotalReadCount());
            ps.setLong  (9, stats.getTotalWriteCount());
            ps.setLong  (10, stats.getTotalFilterCount());
            ps.setLong  (11, stats.getTotalSkipCount());
            ps.setString(12, stats.getConfigHash());
            ps.setString(13, buildErrorSummary(stats.getErrors()));
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    private void insertStepStats(long jobStatsId, JobStatistics stats) {
        if (stats.getStepStats().isEmpty()) return;

        String sql = """
                INSERT INTO BATCH_STEP_RUN_STATS
                       (job_stats_id, step_name, status,
                        read_count, write_count, filter_count, skip_count, error_detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        for (JobStatistics.StepStats s : stats.getStepStats()) {
            jdbc.update(sql,
                    jobStatsId,
                    s.getStepName(),
                    s.getExitStatus().getExitCode(),
                    s.getReadCount(),
                    s.getWriteCount(),
                    s.getFilterCount(),
                    s.getSkipCount(),
                    buildErrorSummary(s.getStepErrors()));
        }
    }

    // ---------------------------------------------------------------
    // Retention cleanup
    // ---------------------------------------------------------------

    private void pruneOldRecords() {
        // Child step rows cascade via ON DELETE CASCADE — only need to delete parent
        int deleted = jdbc.update(
            "DELETE FROM BATCH_JOB_RUN_STATS " +
            "WHERE created_at < DATEADD(DAY, -?, GETDATE())",
            retentionDays);

        if (deleted > 0) {
            log.info("Pruned {} job run stats record(s) older than {} day(s).",
                     deleted, retentionDays);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static Timestamp toTimestamp(Date d) {
        return d != null ? new Timestamp(d.getTime()) : new Timestamp(System.currentTimeMillis());
    }

    private static int durationSeconds(JobStatistics stats) {
        if (stats.getStartTime() == null || stats.getEndTime() == null) return 0;
        long millis = stats.getEndTime().getTime() - stats.getStartTime().getTime();
        return (int) TimeUnit.MILLISECONDS.toSeconds(millis);
    }

    private static String buildErrorSummary(List<String> errors) {
        if (errors == null || errors.isEmpty()) return null;
        String joined = String.join(" | ", errors);
        // Truncate to fit NVARCHAR(2000)
        return joined.length() > 1980 ? joined.substring(0, 1980) + "…" : joined;
    }

    // ---------------------------------------------------------------
    // Setters — called by Spring XML <property> injection
    // ---------------------------------------------------------------

    public void setDataSource(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
