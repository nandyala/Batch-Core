-- =============================================================================
-- schema-secondary.sql
-- SECONDARY (REPORTING) DATABASE — tables read by db-to-file-job
-- =============================================================================
-- Run against: the database pointed to by datasource.secondary.url
-- Safe to re-run: all statements are guarded by IF NOT EXISTS checks.
--
-- In a real environment CUSTOMER_REPORT is typically a VIEW over several
-- normalised tables.  The table below serves as a stand-in that the
-- db-to-file-job reader can query directly.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- CUSTOMER_REPORT
-- Source table for db-to-file-job.
-- Populated nightly by an upstream reporting ETL.
-- The batch job queries WHERE report_date = :batchDate and exports to CSV.
-- ─────────────────────────────────────────────────────────────────────────────
IF NOT EXISTS (
    SELECT 1 FROM sys.tables
    WHERE  name = 'CUSTOMER_REPORT' AND schema_id = SCHEMA_ID('dbo')
)
BEGIN
    CREATE TABLE dbo.CUSTOMER_REPORT
    (
        id            BIGINT          IDENTITY(1,1) NOT NULL,
        customer_id   VARCHAR(20)     NOT NULL,
        customer_name NVARCHAR(200)   NOT NULL,
        region        VARCHAR(50)     NOT NULL,
        total_orders  INT             NOT NULL CONSTRAINT DF_CUST_RPT_ORDERS  DEFAULT 0,
        total_amount  DECIMAL(18,2)   NOT NULL CONSTRAINT DF_CUST_RPT_AMOUNT  DEFAULT 0.00,
        report_date   DATE            NOT NULL,
        CONSTRAINT PK_CUSTOMER_REPORT PRIMARY KEY CLUSTERED (id),
        -- One row per customer per report date
        CONSTRAINT UQ_CUSTOMER_REPORT_CID_DATE UNIQUE (customer_id, report_date),
        CONSTRAINT CHK_CUST_RPT_ORDERS CHECK (total_orders >= 0),
        CONSTRAINT CHK_CUST_RPT_AMOUNT CHECK (total_amount >= 0)
    );

    -- Primary access pattern: WHERE report_date = ? ORDER BY region, customer_name
    CREATE INDEX IX_CUSTOMER_REPORT_DATE   ON dbo.CUSTOMER_REPORT (report_date);
    CREATE INDEX IX_CUSTOMER_REPORT_REGION ON dbo.CUSTOMER_REPORT (region, customer_name);

    PRINT 'Created table: CUSTOMER_REPORT';
END
ELSE
    PRINT 'Table already exists, skipping: CUSTOMER_REPORT';
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- Optional: VIEW variant
-- If you already have CUSTOMER and ORDER tables, replace the table above
-- with this view and point the job reader at it unchanged.
-- ─────────────────────────────────────────────────────────────────────────────
/*
CREATE OR ALTER VIEW dbo.CUSTOMER_REPORT AS
SELECT
    c.customer_id,
    c.customer_name,
    c.region,
    COUNT(o.order_id)   AS total_orders,
    COALESCE(SUM(o.order_amount), 0) AS total_amount,
    CAST(o.order_date AS DATE)       AS report_date
FROM dbo.CUSTOMER c
JOIN dbo.ORDER    o ON o.customer_id = c.customer_id
GROUP BY
    c.customer_id,
    c.customer_name,
    c.region,
    CAST(o.order_date AS DATE);
GO
*/
