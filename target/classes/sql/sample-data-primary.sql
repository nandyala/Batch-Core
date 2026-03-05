-- =============================================================================
-- sample-data-primary.sql
-- PRIMARY DATABASE — sample data for local development and testing
-- =============================================================================
-- Run against: the database pointed to by datasource.url
-- WORK_ITEM rows: exercises multithreaded-job
-- TRANSACTION_STAGING rows: pre-populated reference data (normally loaded
--   by file-to-db-job from a CSV, but a few seed rows aid manual testing)
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- WORK_ITEM — 30 PENDING items across three priority levels
-- Run multithreaded-job to watch them move to PROCESSED in parallel.
-- Re-runnable: TRUNCATE + INSERT pattern
-- ─────────────────────────────────────────────────────────────────────────────
-- Clear existing sample data (comment out on shared environments)
DELETE FROM dbo.WORK_ITEM WHERE payload LIKE '%"env":"sample"%';
GO

INSERT INTO dbo.WORK_ITEM (payload, priority, status, created_at)
VALUES
    -- High priority (priority = 10)
    ('{"env":"sample","type":"invoice-reminder","customerId":"C001","invoiceId":"INV-1001"}',  10, 'PENDING', DATEADD(MINUTE, -60, GETDATE())),
    ('{"env":"sample","type":"invoice-reminder","customerId":"C002","invoiceId":"INV-1002"}',  10, 'PENDING', DATEADD(MINUTE, -59, GETDATE())),
    ('{"env":"sample","type":"invoice-reminder","customerId":"C003","invoiceId":"INV-1003"}',  10, 'PENDING', DATEADD(MINUTE, -58, GETDATE())),
    ('{"env":"sample","type":"invoice-reminder","customerId":"C004","invoiceId":"INV-1004"}',  10, 'PENDING', DATEADD(MINUTE, -57, GETDATE())),
    ('{"env":"sample","type":"invoice-reminder","customerId":"C005","invoiceId":"INV-1005"}',  10, 'PENDING', DATEADD(MINUTE, -56, GETDATE())),

    -- Normal priority (priority = 5)
    ('{"env":"sample","type":"account-statement","accountId":"A101","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -50, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A102","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -49, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A103","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -48, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A104","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -47, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A105","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -46, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A106","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -45, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A107","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -44, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A108","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -43, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A109","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -42, GETDATE())),
    ('{"env":"sample","type":"account-statement","accountId":"A110","month":"2026-01"}',        5, 'PENDING', DATEADD(MINUTE, -41, GETDATE())),

    -- Low priority (priority = 1)
    ('{"env":"sample","type":"data-export","reportId":"RPT-201","format":"pdf"}',               1, 'PENDING', DATEADD(MINUTE, -30, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-202","format":"pdf"}',               1, 'PENDING', DATEADD(MINUTE, -29, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-203","format":"excel"}',             1, 'PENDING', DATEADD(MINUTE, -28, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-204","format":"excel"}',             1, 'PENDING', DATEADD(MINUTE, -27, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-205","format":"csv"}',               1, 'PENDING', DATEADD(MINUTE, -26, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-206","format":"csv"}',               1, 'PENDING', DATEADD(MINUTE, -25, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-207","format":"pdf"}',               1, 'PENDING', DATEADD(MINUTE, -24, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-208","format":"pdf"}',               1, 'PENDING', DATEADD(MINUTE, -23, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-209","format":"excel"}',             1, 'PENDING', DATEADD(MINUTE, -22, GETDATE())),
    ('{"env":"sample","type":"data-export","reportId":"RPT-210","format":"csv"}',               1, 'PENDING', DATEADD(MINUTE, -21, GETDATE())),

    -- Edge cases: deliberately bad/empty payload to exercise skip logic
    ('',                                                                                         0, 'PENDING', DATEADD(MINUTE,  -5, GETDATE())),  -- empty → PERMANENT skip
    ('{"env":"sample","type":"data-export","reportId":"RPT-BAD"}',                              0, 'PENDING', DATEADD(MINUTE,  -4, GETDATE()));   -- valid, low priority
GO

PRINT CONCAT('Inserted ', @@ROWCOUNT, ' rows into WORK_ITEM');
GO

-- ─────────────────────────────────────────────────────────────────────────────
-- TRANSACTION_STAGING — small seed set for manual testing
-- In normal operation these rows are loaded by file-to-db-job from a CSV.
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM dbo.TRANSACTION_STAGING WHERE transaction_id LIKE 'SAMPLE-%';
GO

INSERT INTO dbo.TRANSACTION_STAGING
    (transaction_id, account_number, amount, transaction_type, transaction_date, description)
VALUES
    ('SAMPLE-TXN-0001', 'ACCT00123456', 1500.00, 'CREDIT', '2026-01-15', 'Salary deposit'),
    ('SAMPLE-TXN-0002', 'ACCT00123456',   85.50, 'DEBIT',  '2026-01-16', 'Utility bill payment'),
    ('SAMPLE-TXN-0003', 'ACCT00789012',  250.00, 'DEBIT',  '2026-01-16', 'Online purchase'),
    ('SAMPLE-TXN-0004', 'ACCT00789012', 3000.00, 'CREDIT', '2026-01-17', 'Wire transfer in'),
    ('SAMPLE-TXN-0005', 'ACCT00345678',  120.75, 'DEBIT',  '2026-01-17', 'Grocery store'),
    ('SAMPLE-TXN-0006', 'ACCT00345678',  500.00, 'CREDIT', '2026-01-18', 'Refund');
GO

PRINT CONCAT('Inserted ', @@ROWCOUNT, ' rows into TRANSACTION_STAGING');
GO
