-- =============================================================================
-- schema-primary.sql
-- PRIMARY DATABASE — business tables used by batch jobs
-- =============================================================================
-- Run against: the database pointed to by datasource.url
-- Safe to re-run: all statements are guarded by IF NOT EXISTS checks.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- TRANSACTION_STAGING
-- Target table for file-to-db-job.
-- The MERGE writer in file-to-db-job.xml uses transaction_id as the upsert key,
-- so re-running the same input file is fully idempotent.
-- ─────────────────────────────────────────────────────────────────────────────
IF NOT EXISTS (
    SELECT 1 FROM sys.tables
    WHERE  name = 'TRANSACTION_STAGING' AND schema_id = SCHEMA_ID('dbo')
)
BEGIN
    CREATE TABLE dbo.TRANSACTION_STAGING
    (
        id               BIGINT         IDENTITY(1,1) NOT NULL,
        transaction_id   VARCHAR(50)    NOT NULL,
        account_number   VARCHAR(20)    NOT NULL,
        amount           DECIMAL(18,2)  NOT NULL,
        -- CREDIT or DEBIT — enforced by TransactionProcessor before insert
        transaction_type VARCHAR(10)    NOT NULL,
        transaction_date DATE           NOT NULL,
        description      NVARCHAR(500)  NULL,
        created_at       DATETIME2      NOT NULL CONSTRAINT DF_TXN_STAGING_CREATED  DEFAULT GETDATE(),
        updated_at       DATETIME2      NOT NULL CONSTRAINT DF_TXN_STAGING_UPDATED  DEFAULT GETDATE(),
        CONSTRAINT PK_TRANSACTION_STAGING PRIMARY KEY CLUSTERED (id),
        CONSTRAINT UQ_TRANSACTION_STAGING_TXN_ID UNIQUE (transaction_id),
        CONSTRAINT CHK_TXN_STAGING_TYPE  CHECK (transaction_type IN ('CREDIT', 'DEBIT')),
        CONSTRAINT CHK_TXN_STAGING_AMOUNT CHECK (amount > 0)
    );

    CREATE INDEX IX_TXN_STAGING_DATE   ON dbo.TRANSACTION_STAGING (transaction_date);
    CREATE INDEX IX_TXN_STAGING_ACCT   ON dbo.TRANSACTION_STAGING (account_number);

    PRINT 'Created table: TRANSACTION_STAGING';
END
ELSE
    PRINT 'Table already exists, skipping: TRANSACTION_STAGING';
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- WORK_ITEM
-- Source and target table for multithreaded-job.
-- The job reads PENDING rows, processes them, and updates status to PROCESSED.
-- ─────────────────────────────────────────────────────────────────────────────
IF NOT EXISTS (
    SELECT 1 FROM sys.tables
    WHERE  name = 'WORK_ITEM' AND schema_id = SCHEMA_ID('dbo')
)
BEGIN
    CREATE TABLE dbo.WORK_ITEM
    (
        id            BIGINT          IDENTITY(1,1) NOT NULL,
        payload       NVARCHAR(2000)  NOT NULL,
        priority      INT             NOT NULL CONSTRAINT DF_WORK_ITEM_PRIORITY DEFAULT 0,
        -- PENDING | PROCESSED | FAILED
        status        VARCHAR(20)     NOT NULL CONSTRAINT DF_WORK_ITEM_STATUS   DEFAULT 'PENDING',
        created_at    DATETIME2       NOT NULL CONSTRAINT DF_WORK_ITEM_CREATED  DEFAULT GETDATE(),
        processed_at  DATETIME2       NULL,
        CONSTRAINT PK_WORK_ITEM PRIMARY KEY CLUSTERED (id),
        CONSTRAINT CHK_WORK_ITEM_STATUS CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
    );

    -- The paging reader in multithreaded-job.xml sorts by id and filters on status.
    -- This composite index covers both in one seek.
    CREATE INDEX IX_WORK_ITEM_STATUS_ID ON dbo.WORK_ITEM (status, id);

    PRINT 'Created table: WORK_ITEM';
END
ELSE
    PRINT 'Table already exists, skipping: WORK_ITEM';
GO
