-- =============================================================================
-- sample-data-secondary.sql
-- SECONDARY (REPORTING) DATABASE — sample data for local development / testing
-- =============================================================================
-- Run against: the database pointed to by datasource.secondary.url
-- CUSTOMER_REPORT rows: exercises db-to-file-job
--
-- After inserting, run:
--   ./db-to-file-job.sh batchDate=2026-01-15
-- and check ${batch.dbtofile.outputDir}/customer-report-2026-01-15.csv
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- CUSTOMER_REPORT — 20 rows across 4 regions for two report dates
-- Includes zero-order rows to exercise CustomerReportProcessor filter logic.
-- Re-runnable: delete sample rows and re-insert.
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM dbo.CUSTOMER_REPORT WHERE customer_id LIKE 'SAMPLE-%';
GO

INSERT INTO dbo.CUSTOMER_REPORT
    (customer_id, customer_name, region, total_orders, total_amount, report_date)
VALUES
    -- ── NORTH region ─────────────────────────────────────────────────────────
    ('SAMPLE-C001', 'ACME CORPORATION',      'NORTH',  42, 128450.75, '2026-01-15'),
    ('SAMPLE-C002', 'BRIGHTSIDE RETAIL LTD', 'NORTH',   8,   9870.00, '2026-01-15'),
    ('SAMPLE-C003', 'CLEARVIEW TECH',        'NORTH',  15,  33200.50, '2026-01-15'),
    ('SAMPLE-C004', 'DUNE SUPPLIES INC',     'NORTH',   0,      0.00, '2026-01-15'),  -- filtered (0 orders)

    -- ── SOUTH region ─────────────────────────────────────────────────────────
    ('SAMPLE-C005', 'EASTLAND FOODS',        'SOUTH',  27,  54300.25, '2026-01-15'),
    ('SAMPLE-C006', 'FENWAY LOGISTICS',      'SOUTH',   3,   2150.00, '2026-01-15'),
    ('SAMPLE-C007', 'GROVE PHARMACEUTICALS', 'SOUTH',  61, 310750.00, '2026-01-15'),
    ('SAMPLE-C008', 'HIGHLAND MOTORS',       'SOUTH',   0,      0.00, '2026-01-15'),  -- filtered (0 orders)

    -- ── EAST region ──────────────────────────────────────────────────────────
    ('SAMPLE-C009', 'IRONCLAD SECURITY',     'EAST',   19,  44800.90, '2026-01-15'),
    ('SAMPLE-C010', 'JUNCTION MEDIA',        'EAST',    5,   7200.00, '2026-01-15'),
    ('SAMPLE-C011', 'KEYSTONE CONSULTING',   'EAST',   33,  89100.00, '2026-01-15'),
    ('SAMPLE-C012', 'LIMESTONE PARTNERS',    'EAST',   11,  18600.50, '2026-01-15'),

    -- ── WEST region ──────────────────────────────────────────────────────────
    ('SAMPLE-C013', 'METRO DIGITAL',         'WEST',   48, 175220.00, '2026-01-15'),
    ('SAMPLE-C014', 'NORTHGATE EXPORTS',     'WEST',    2,   1050.00, '2026-01-15'),
    ('SAMPLE-C015', 'OCEAN FREIGHT CO',      'WEST',   22,  62300.75, '2026-01-15'),
    ('SAMPLE-C016', 'PACIFIC VENTURES',      'WEST',    0,      0.00, '2026-01-15'),  -- filtered (0 orders)

    -- ── Second report date (for testing multiple runs) ─────────────────────
    ('SAMPLE-C001', 'ACME CORPORATION',      'NORTH',  38, 119800.00, '2026-01-14'),
    ('SAMPLE-C005', 'EASTLAND FOODS',        'SOUTH',  24,  48900.50, '2026-01-14'),
    ('SAMPLE-C007', 'GROVE PHARMACEUTICALS', 'SOUTH',  57, 295400.00, '2026-01-14'),
    ('SAMPLE-C013', 'METRO DIGITAL',         'WEST',   44, 162500.00, '2026-01-14');
GO

PRINT CONCAT('Inserted ', @@ROWCOUNT, ' rows into CUSTOMER_REPORT');
GO
