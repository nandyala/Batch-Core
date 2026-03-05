-- =============================================================================
-- schema-batch-stats.sql
-- =============================================================================
-- Custom run-statistics tables for the Spring Batch framework.
-- Populated by JobRunStatsRepository at the end of every job.
-- Records older than batch.stats.retention.days are pruned automatically.
--
-- Idempotent: IF NOT EXISTS guards make this safe to re-run on existing DBs.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- BATCH_JOB_RUN_STATS  — one row per job execution
-- -----------------------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_NAME = 'BATCH_JOB_RUN_STATS'
)
BEGIN
    CREATE TABLE BATCH_JOB_RUN_STATS (
        id               BIGINT         IDENTITY(1,1) NOT NULL,
        correlation_id   VARCHAR(20)    NOT NULL,          -- short run ID (e.g. A3F9BC12)
        job_execution_id BIGINT         NOT NULL,          -- FK to BATCH_JOB_EXECUTION
        job_name         VARCHAR(100)   NOT NULL,
        status           VARCHAR(20)    NOT NULL,          -- COMPLETED / FAILED
        start_time       DATETIME2(3)   NOT NULL,
        end_time         DATETIME2(3)   NOT NULL,
        duration_seconds INT            NOT NULL DEFAULT 0,
        total_read       BIGINT         NOT NULL DEFAULT 0,
        total_written    BIGINT         NOT NULL DEFAULT 0,
        total_filtered   BIGINT         NOT NULL DEFAULT 0,
        total_errors     BIGINT         NOT NULL DEFAULT 0,
        config_hash      VARCHAR(20)        NULL,          -- hash of non-sensitive batch config
        error_summary    NVARCHAR(2000)     NULL,          -- top N error messages
        created_at       DATETIME2(3)   NOT NULL DEFAULT GETDATE(),

        CONSTRAINT PK_JOB_RUN_STATS PRIMARY KEY (id)
    );

    CREATE INDEX IX_JOB_RUN_STATS_EXEC ON BATCH_JOB_RUN_STATS (job_execution_id);
    CREATE INDEX IX_JOB_RUN_STATS_DATE ON BATCH_JOB_RUN_STATS (created_at);
    CREATE INDEX IX_JOB_RUN_STATS_CORR ON BATCH_JOB_RUN_STATS (correlation_id);
END;

-- -----------------------------------------------------------------------------
-- BATCH_STEP_RUN_STATS  — one row per step per job execution
-- -----------------------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_NAME = 'BATCH_STEP_RUN_STATS'
)
BEGIN
    CREATE TABLE BATCH_STEP_RUN_STATS (
        id             BIGINT         IDENTITY(1,1) NOT NULL,
        job_stats_id   BIGINT         NOT NULL,            -- FK to BATCH_JOB_RUN_STATS
        step_name      VARCHAR(100)   NOT NULL,
        status         VARCHAR(20)    NOT NULL,
        read_count     BIGINT         NOT NULL DEFAULT 0,
        write_count    BIGINT         NOT NULL DEFAULT 0,
        filter_count   BIGINT         NOT NULL DEFAULT 0,
        skip_count     BIGINT         NOT NULL DEFAULT 0,
        error_detail   NVARCHAR(2000)     NULL,            -- errors captured for this step

        CONSTRAINT PK_STEP_RUN_STATS  PRIMARY KEY (id),
        CONSTRAINT FK_STEP_JOB_STATS  FOREIGN KEY (job_stats_id)
            REFERENCES BATCH_JOB_RUN_STATS (id) ON DELETE CASCADE
    );

    CREATE INDEX IX_STEP_RUN_JOB ON BATCH_STEP_RUN_STATS (job_stats_id);
END;
