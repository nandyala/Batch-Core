# Spring Batch Framework

A production-ready Spring Batch 5 framework for building and running enterprise batch jobs against SQL Server. Includes a full infrastructure layer (connection pools, transaction managers, email reporting, statistics, error collection) and eight ready-to-run jobs covering every common batch pattern.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Configuration](#configuration)
4. [Running Jobs](#running-jobs)
5. [Available Jobs](#available-jobs)
   - [invoice-job](#1-invoice-job)
   - [sample-job](#2-sample-job)
   - [db-to-file-job](#3-db-to-file-job)
   - [file-to-db-job](#4-file-to-db-job)
   - [multithreaded-job](#5-multithreaded-job)
   - [mt-db-to-file-job](#6-mt-db-to-file-job)
   - [mt-file-to-db-job](#7-mt-file-to-db-job)
   - [partitioned-db-to-file-job](#8-partitioned-db-to-file-job)
6. [Multi-threaded vs Partitioned](#multi-threaded-vs-partitioned)
7. [Multiple Datasources](#multiple-datasources)
8. [Adding a New Job](#adding-a-new-job)
9. [Deployment & Validation](#deployment--validation)
10. [Database Schema](#database-schema)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                   SpringBatchApplication                    │
│                                                             │
│  SHARED_XML (loaded for every job)                          │
│    batch-infrastructure.xml  ← datasources, tx managers     │
│    batch-mail.xml            ← JavaMailSender               │
│    batch-listeners.xml       ← statistics, error collector  │
│    batch-step-defaults.xml   ← abstract thread pool bean    │
│                                                             │
│  JOB_XML (one XML per job, loaded on demand)                │
│    invoice-job.xml, sample-job.xml, db-to-file-job.xml, … │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────┐   ┌──────────────────────────┐
│  PRIMARY DataSource         │   │  SECONDARY DataSource     │
│  batchdb (SQL Server)       │   │  reportingdb (SQL Server) │
│  - Spring Batch metadata    │   │  - CUSTOMER_REPORT        │
│  - TRANSACTION_STAGING      │   │  - (read-only reporting)  │
│  - WORK_ITEM                │   └──────────────────────────┘
└─────────────────────────────┘
```

### Key Infrastructure Components

| Component | Class | Purpose |
|---|---|---|
| `jobStatisticsListener` | `JobStatisticsListener` | Records job start/end/status to `BATCH_JOB_RUN_STATS` |
| `stepStatisticsListener` | `StepStatisticsListener` | Records step read/write/skip counts to `BATCH_STEP_RUN_STATS` |
| `stepErrorCollector` | `StepErrorCollector` | Accumulates skipped-item errors; included in email report |
| `statisticsAndEmailTasklet` | `StatisticsAndEmailTasklet` | Builds the final HTML report and sends it via SMTP |
| `abstractStepTaskExecutor` | `ThreadPoolTaskExecutor` | Abstract parent for all thread pools: `queueCapacity=0` + `CallerRunsPolicy` |

### How a Job Runs

```
java -jar app.jar <job-name> [key=value ...]
         │
         ▼
   SpringBatchApplication
     1. Loads SHARED_XML         — infrastructure beans
     2. Loads JOB_XML            — reader / writer / processor / job definition
     3. Resolves job parameters  — CLI args + defaults from application.properties
     4. Runs the job
     5. statisticsAndEmailTasklet sends report email (if enabled)
     6. JVM exits with job exit code
```

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/batch/
│   │   ├── SpringBatchApplication.java        ← entry point + dry-run support
│   │   ├── support/
│   │   │   ├── BatchJobParameterValidator.java ← validates required job params
│   │   │   └── ColumnRangePartitioner.java     ← splits tables by id range
│   │   ├── tasklet/
│   │   │   └── FileRenameTasklet.java          ← renames .csv.tmp → .csv atomically
│   │   ├── dbtofile/
│   │   │   ├── CustomerReportRecord.java
│   │   │   ├── CustomerReportRowMapper.java
│   │   │   ├── CustomerReportProcessor.java
│   │   │   └── CustomerReportHeaderCallback.java
│   │   ├── filetodb/
│   │   │   ├── TransactionRecord.java
│   │   │   ├── TransactionFieldSetMapper.java
│   │   │   └── TransactionProcessor.java
│   │   └── multithreaded/
│   │       ├── WorkItem.java
│   │       ├── WorkItemRowMapper.java
│   │       └── WorkItemProcessor.java
│   └── resources/
│       ├── application.properties             ← base configuration
│       ├── application-dev.properties         ← developer overrides
│       ├── spring/
│       │   ├── batch-infrastructure.xml       ← datasources, HikariCP, tx managers
│       │   ├── batch-mail.xml                 ← SMTP / JavaMailSender
│       │   ├── batch-listeners.xml            ← statistics + error collector beans
│       │   ├── batch-step-defaults.xml        ← abstractStepTaskExecutor
│       │   └── jobs/
│       │       ├── _TEMPLATE-job.xml          ← developer template (copy to start)
│       │       ├── invoice-job.xml
│       │       ├── sample-job.xml
│       │       ├── db-to-file-job.xml
│       │       ├── file-to-db-job.xml
│       │       ├── multithreaded-job.xml
│       │       ├── mt-db-to-file-job.xml
│       │       ├── mt-file-to-db-job.xml
│       │       └── partitioned-db-to-file-job.xml
│       └── sql/
│           ├── schema-primary.sql             ← TRANSACTION_STAGING + WORK_ITEM
│           ├── schema-secondary.sql           ← CUSTOMER_REPORT
│           ├── sample-data-primary.sql
│           ├── sample-data-secondary.sql
│           └── sample-transactions.csv
└── main/scripts/
    ├── run-batch.sh                           ← generic launcher (all jobs use this)
    ├── validate-config.sh                     ← dry-run all jobs post-deploy
    ├── invoice-job.sh
    ├── sample-job.sh
    ├── db-to-file-job.sh
    ├── file-to-db-job.sh
    ├── multithreaded-job.sh
    ├── mt-db-to-file-job.sh
    ├── mt-file-to-db-job.sh
    └── partitioned-db-to-file-job.sh
```

---

## Configuration

All configuration lives in `application.properties`. Override values using (highest to lowest priority):

| Priority | Source | How to activate |
|---|---|---|
| 1 (highest) | CLI job parameters | `java -jar app.jar job-name key=value` |
| 2 | JVM system properties | `java -Dkey=value -jar app.jar` |
| 3 | OS environment variables | `export KEY=value` |
| 4 | External config file | `-Dapp.config.file=/etc/batch/prod.properties` |
| 5 | Profile file | `-Dapp.profile=prod` → loads `application-prod.properties` |
| 6 (lowest) | `application.properties` | always loaded |

### Key Properties

```properties
# Primary datasource (Spring Batch metadata + business tables)
datasource.url=jdbc:sqlserver://localhost:1433;databaseName=batchdb;...
datasource.username=sa
datasource.password=${DB_PASSWORD}

# Secondary datasource (reporting DB)
datasource.secondary.url=jdbc:sqlserver://localhost:1433;databaseName=reportingdb;...

# Email
mail.smtp.host=smtp.example.com
batch.report.email=ops-team@example.com
batch.email.enabled=false        # set true to send emails

# Per-job thread/chunk tuning
batch.multithreaded.threadCount=4
batch.mtdbtofile.threadCount=4
batch.partitioned.gridSize=4
```

> **Security**: never commit passwords to source control. Use OS environment variables:
> `datasource.password=${DB_PASSWORD}`

---

## Running Jobs

### Shell scripts (recommended for Linux deployments)

```bash
# Make scripts executable once
chmod +x src/main/scripts/*.sh

# Run any job — batchDate defaults to today
./db-to-file-job.sh
./db-to-file-job.sh batchDate=2026-01-15

# Override JVM memory for large jobs
JVM_OPTS="-Xms512m -Xmx4g" ./mt-db-to-file-job.sh batchDate=2026-01-15
```

### Direct java invocation

```bash
java -jar app.jar <job-name> [key=value ...]

# Examples
java -jar app.jar invoice-job            batchDate=2026-01-15
java -jar app.jar db-to-file-job         batchDate=2026-01-15
java -jar app.jar file-to-db-job         batchDate=2026-01-15
java -jar app.jar multithreaded-job
java -jar app.jar mt-db-to-file-job      batchDate=2026-01-15
java -jar app.jar mt-file-to-db-job      batchDate=2026-01-15
java -jar app.jar partitioned-db-to-file-job batchDate=2026-01-15
```

### Dry-run mode (validate configuration without processing data)

```bash
java -jar app.jar --dry-run invoice-job batchDate=2026-01-15
```

The dry-run loads the full Spring context, validates all `${placeholder}` properties resolve, verifies the job bean and its parameter validator, then prints the step graph — without reading or writing any data.

---

## Available Jobs

### 1. invoice-job

**Purpose**: Processes invoices from the primary database, applies business rules, and updates invoice status.

**Pattern**: Single-threaded chunk step (DB → DB)

**Parameters**:

| Parameter | Required | Description |
|---|---|---|
| `batchDate` | Yes | Processing date (`YYYY-MM-DD`) |

**Flow**:
```
invoiceProcessingStep  ──►  statisticsAndEmailStep
```

**Run**:
```bash
./invoice-job.sh                            # today
./invoice-job.sh batchDate=2026-01-15
```

---

### 2. sample-job

**Purpose**: Demonstration job showing framework conventions. No business logic.

**Pattern**: Single-threaded tasklet

**Parameters**: none required

**Flow**:
```
sampleStep  ──►  statisticsAndEmailStep
```

**Run**:
```bash
./sample-job.sh
```

---

### 3. db-to-file-job

**Purpose**: Reads `CUSTOMER_REPORT` rows from the **secondary (reporting) database** for a given `batchDate` and writes them to a CSV file.

**Pattern**: Single-threaded chunk step (DB → File)

**Reader**: `JdbcCursorItemReader` — opens a single forward-only JDBC cursor. Fast and memory-efficient; not suitable for multi-threading (use `mt-db-to-file-job` or `partitioned-db-to-file-job` for parallel execution).

**Writer**: `FlatFileItemWriter` — writes comma-separated lines with a header row.

**Processor**: `CustomerReportProcessor` — filters zero-order customers, title-cases names, rounds amounts to 2 decimal places.

**Parameters**:

| Parameter | Required | Description |
|---|---|---|
| `batchDate` | Yes | Report date to export (`YYYY-MM-DD`) |

**Output**:
```
${batch.dbtofile.outputDir}/customer-report-{batchDate}.csv
```

**Flow**:
```
exportCustomerReportStep  ──►  statisticsAndEmailStep
```

**Skip behaviour**: Permanent exceptions (bad data) are skipped up to `skip-limit=50`. No retry (cursor cannot be safely retried).

**Run**:
```bash
./db-to-file-job.sh
./db-to-file-job.sh batchDate=2026-01-15
```

**Relevant properties**:
```properties
batch.dbtofile.commitInterval=100
batch.dbtofile.outputDir=/data/batch/output
```

---

### 4. file-to-db-job

**Purpose**: Reads a CSV transaction file and upserts each row into `TRANSACTION_STAGING` on the primary database using a MERGE statement.

**Pattern**: Single-threaded chunk step (File → DB)

**Reader**: `FlatFileItemReader` — reads comma-separated rows, maps them to `TransactionRecord` via `TransactionFieldSetMapper`.

**Writer**: `JdbcBatchItemWriter` — executes a SQL Server `MERGE` (upsert) keyed on `transaction_id`. Idempotent: restarting the job re-reads the file from the beginning but does not create duplicate rows.

**Processor**: `TransactionProcessor` — validates `CREDIT`/`DEBIT` type, positive amount, account number format (`[A-Z0-9]{8,16}`). Returns `null` to filter zero-amount rows.

**Input file path** (in order of preference):
1. `inputFile=<absolute path>` job parameter
2. `${batch.filetodb.inputDir}/{batchDate}/transactions.csv`

**Parameters**:

| Parameter | Required | Description |
|---|---|---|
| `batchDate` | Yes | Used to build the default input path |
| `inputFile` | No | Absolute path to override the default input path |

**Flow**:
```
importTransactionsStep  ──►  statisticsAndEmailStep
```

**Skip/Retry**: Bad CSV rows and invalid data are permanently skipped. Transient DB errors are retried up to 3 times.

**Run**:
```bash
./file-to-db-job.sh
./file-to-db-job.sh batchDate=2026-01-15
./file-to-db-job.sh batchDate=2026-01-15 inputFile=/data/custom/feed.csv
```

**Relevant properties**:
```properties
batch.filetodb.commitInterval=200
batch.filetodb.inputDir=/data/batch/input
```

---

### 5. multithreaded-job

**Purpose**: Processes `WORK_ITEM` rows in parallel, updating each row's status to `PROCESSED`.

**Pattern**: Multi-threaded chunk step (DB → DB)

**Reader**: `JdbcPagingItemReader` — fetches independent pages using `OFFSET/FETCH` (SQL Server). Thread-safe: multiple threads each fetch their own page without sharing state.

**Writer**: `JdbcBatchItemWriter` — updates `WORK_ITEM.status = 'PROCESSED'` in batches. Thread-safe: each chunk runs in its own transaction on its own pooled connection.

**Processor**: `WorkItemProcessor` — stateless; sets `status = PROCESSED`, `processedAt = now()`.

**Concurrency**: Controlled by `batch.multithreaded.threadCount` (default: 4). Each thread processes one chunk (page) at a time.

**Parameters**: none required

**Flow**:
```
processWorkItemsStep  ──►  statisticsAndEmailStep
  (4 threads)
```

**Run**:
```bash
./multithreaded-job.sh
```

**Relevant properties**:
```properties
batch.multithreaded.commitInterval=50
batch.multithreaded.threadCount=4
batch.multithreaded.pageSize=50
```

---

### 6. mt-db-to-file-job

**Purpose**: Multi-threaded variant of `db-to-file-job`. Reads `CUSTOMER_REPORT` in parallel pages and writes to a single CSV file.

**Pattern**: Multi-threaded chunk step (DB → File), with `.tmp` → atomic rename pattern

**Reader**: `JdbcPagingItemReader` — thread-safe paging reader. `saveState="false"` disables ChunkMonitor registration (prevents "opened in a different thread" warnings). On restart, re-queries from page 1.

**Writer**: `SynchronizedItemStreamWriter` wrapping `FlatFileItemWriter` — serialises all `write()` calls to prevent threads from interleaving output lines. `saveState="false"` on the delegate. **Note**: row order in the output file is non-deterministic.

**Atomic output**: The writer writes to `*.csv.tmp`. `FileRenameTasklet` atomically renames to `*.csv` after the chunk step completes. Downstream consumers polling the directory only ever see a complete file.

**Parameters**:

| Parameter | Required | Description |
|---|---|---|
| `batchDate` | Yes | Report date to export (`YYYY-MM-DD`) |

**Output**:
```
${batch.dbtofile.outputDir}/mt-customer-report-{batchDate}.csv
```

**Flow**:
```
mtExportCustomerReportStep  ──►  renameOutputFileStep  ──►  statisticsAndEmailStep
   (N threads, writes .tmp)         (atomic rename)
```

**Run**:
```bash
./mt-db-to-file-job.sh
./mt-db-to-file-job.sh batchDate=2026-01-15
```

**Relevant properties**:
```properties
batch.mtdbtofile.commitInterval=100
batch.mtdbtofile.threadCount=4
batch.mtdbtofile.pageSize=100
```

---

### 7. mt-file-to-db-job

**Purpose**: Multi-threaded variant of `file-to-db-job`. Reads a CSV transaction file in parallel and upserts rows into `TRANSACTION_STAGING`.

**Pattern**: Multi-threaded chunk step (File → DB)

**Reader**: `SynchronizedItemStreamReader` wrapping `FlatFileItemReader` — serialises `read()` calls so each thread receives exactly one unique line (no line is skipped or double-read). `saveState="false"` on the delegate.

**Writer**: `JdbcBatchItemWriter` with MERGE upsert — inherently thread-safe (each chunk uses its own DB connection and transaction). Idempotent on restart.

**Parameters**:

| Parameter | Required | Description |
|---|---|---|
| `batchDate` | Yes | Used to build the default input path |
| `inputFile` | No | Absolute path override |

**Flow**:
```
mtImportTransactionsStep  ──►  statisticsAndEmailStep
   (N threads)
```

**Run**:
```bash
./mt-file-to-db-job.sh
./mt-file-to-db-job.sh batchDate=2026-01-15
```

**Relevant properties**:
```properties
batch.mtfiletodb.commitInterval=200
batch.mtfiletodb.threadCount=4
```

---

### 8. partitioned-db-to-file-job

**Purpose**: Partitioned variant of `db-to-file-job`. Splits `CUSTOMER_REPORT` into N independent ID-range partitions and processes each in its own worker step.

**Pattern**: Partitioned step (DB → File, one output file per partition)

**Partitioner**: `ColumnRangePartitioner` — queries `MIN(id)` and `MAX(id)` on `CUSTOMER_REPORT` (filtered by `batchDate`), divides the range into `gridSize` equal buckets. Each partition's `ExecutionContext` contains `minId`, `maxId`, and `partitionNumber`.

**Reader (worker)**: `JdbcCursorItemReader` — each worker step runs in its own thread with its own step-scoped bean instance and its own JDBC cursor over its ID range. No synchronisation wrapper needed.

**Writer (worker)**: `FlatFileItemWriter` — each partition writes to its own numbered output file. No synchronisation wrapper needed.

**Restart safety**: Only failed or stopped partitions are re-executed on restart; completed partitions are skipped automatically.

**Parameters**:

| Parameter | Required | Description |
|---|---|---|
| `batchDate` | Yes | Report date to export (`YYYY-MM-DD`) |

**Output** (one file per partition):
```
${batch.partitioned.outputDir}/partitioned-customer-report-{batchDate}-p0.csv
${batch.partitioned.outputDir}/partitioned-customer-report-{batchDate}-p1.csv
...
```

**Flow**:
```
partitionManagerStep  ──►  statisticsAndEmailStep
  ├── partition-worker-0  (reads rows 1–250,  writes -p0.csv)
  ├── partition-worker-1  (reads rows 251–500, writes -p1.csv)
  ├── partition-worker-2  (reads rows 501–750, writes -p2.csv)
  └── partition-worker-3  (reads rows 751–1000, writes -p3.csv)
```

**Run**:
```bash
./partitioned-db-to-file-job.sh
./partitioned-db-to-file-job.sh batchDate=2026-01-15
```

**Relevant properties**:
```properties
batch.partitioned.commitInterval=100
batch.partitioned.gridSize=4
batch.partitioned.outputDir=/data/batch/output/partitioned
```

---

## Multi-threaded vs Partitioned

| Concern | Multi-threaded chunk | Partitioned step |
|---|---|---|
| **Reader safety** | Reader must be thread-safe (`JdbcPagingItemReader` or `SynchronizedItemStreamReader`) | Each partition gets its own reader instance — any reader is safe |
| **Writer safety** | Writer must be thread-safe (`JdbcBatchItemWriter` or `SynchronizedItemStreamWriter`) | Each partition gets its own writer instance — any writer is safe |
| **Output file order** | Non-deterministic (threads interleave) | Deterministic within each partition file |
| **Restart granularity** | Entire step re-runs from the beginning | Only failed partitions re-run |
| **Spring Batch metadata** | One StepExecution | One StepExecution per partition |
| **Best for** | High-volume DB→DB with paging reader; file reads | High-volume DB reads; best restart isolation |

**Rule of thumb**:
- Have a `JdbcCursorItemReader`? → Use **partitioning** (cursor is not thread-safe)
- Have a `FlatFileItemReader`? → Use **multi-threaded** with `SynchronizedItemStreamReader`
- Need to write to a single file? → Use **multi-threaded** with `SynchronizedItemStreamWriter`
- Need per-partition output files and best restart? → Use **partitioning**

---

## Multiple Datasources

Two datasources are configured in `batch-infrastructure.xml`:

| Bean | Database | Used by |
|---|---|---|
| `dataSource` + `transactionManager` | `batchdb` (primary) | Spring Batch metadata, `TRANSACTION_STAGING`, `WORK_ITEM` |
| `secondaryDataSource` + `secondaryTransactionManager` | `reportingdb` (secondary) | `CUSTOMER_REPORT` reads in db-to-file jobs |

Use the appropriate `ref` in your job XML:
```xml
<!-- Reading from the reporting database -->
<property name="dataSource" ref="secondaryDataSource"/>

<!-- Writing to the primary business database -->
<property name="dataSource" ref="dataSource"/>

<!-- Step transaction manager (always use primary for batch metadata consistency) -->
<batch:tasklet transaction-manager="transactionManager">
```

If you only have one database, set `datasource.secondary.*` to the same values as `datasource.*`.

---

## Adding a New Job

### 1. Copy the template

```bash
cp src/main/resources/spring/jobs/_TEMPLATE-job.xml \
   src/main/resources/spring/jobs/my-new-job.xml
```

### 2. Choose a pattern and implement reader/writer/processor

Edit `my-new-job.xml`. Follow the `TODO` comments. Pick one of:
- **Variant A** — single-threaded chunk (default, safest)
- **Variant B** — multi-threaded chunk (for high-volume parallel processing)
- **Variant C** — partitioned step (for DB reads with best restart isolation)

### 3. Create Java classes

Add your classes under `src/main/java/com/example/batch/yourpackage/`:

```
YourItemRecord.java          ← domain POJO
YourFieldSetMapper.java      ← (if reading from CSV)
YourRowMapper.java           ← (if reading from DB)
YourItemProcessor.java       ← business logic (must be stateless)
```

### 4. No registration needed — convention over configuration

The framework automatically resolves the job XML from the slug you pass on the command line:

```
my-new-job  →  spring/jobs/my-new-job.xml  (classpath)
              →  bean: myNewJob             (kebab → camelCase)
```

As long as your XML file is in `src/main/resources/spring/jobs/` it will be found at runtime. There is no list of jobs to update in `SpringBatchApplication.java`.

The job bean inside the XML is also auto-detected: if your XML defines exactly one `Job` bean the framework finds it automatically. If you define multiple jobs in one XML, pass the bean name explicitly:

```bash
java -jar app.jar spring/jobs/my-new-job.xml myNewJobBeanName batchDate=2026-01-15
```

### 5. Add configuration properties

In `application.properties`:

```properties
# my-new-job
batch.mynewjob.commitInterval=100
batch.mynewjob.threadCount=4
batch.mynewjob.inputDir=/data/batch/input/mynewjob
```

### 6. Create the launch script

```bash
cp src/main/scripts/db-to-file-job.sh \
   src/main/scripts/my-new-job.sh
```

Edit the script: change `db-to-file-job` to `my-new-job` and adjust `EXTRA_PARAMS` as needed.

### 7. Register in validate-config.sh

In `validate-config.sh`, add to the `JOB_PARAMS` map:

```bash
declare -A JOB_PARAMS=(
    # ... existing jobs ...
    ["my-new-job"]="batchDate=$(date '+%Y-%m-%d')"
)
```

### 8. Validate

```bash
./validate-config.sh
```

---

## Deployment & Validation

### Post-deploy smoke test

After deploying a new build, run the validation script to confirm all jobs are correctly configured before any live runs:

```bash
./validate-config.sh
```

This performs a `--dry-run` for every registered job. The script:
- Loads the full Spring context (confirms all `${placeholder}` properties resolve)
- Validates job parameter requirements
- Prints the step graph
- Reports `[PASS]` / `[FAIL]` per job
- Exits with code `0` (all pass) or `1` (any fail)

### Environment variables used by run-batch.sh

| Variable | Default | Description |
|---|---|---|
| `APP_HOME` | directory of `run-batch.sh` | Working directory and `app.jar` location |
| `APP_JAR` | `$APP_HOME/app.jar` | Path to the executable jar |
| `JAVA_HOME` | (system) | JDK home; uses `java` from PATH if not set |
| `APP_PROFILE` | (none) | Loads `application-{profile}.properties` |
| `APP_CONFIG_FILE` | (none) | Path to an external properties file |
| `JVM_OPTS` | (none) | Extra JVM flags, e.g. `-Xmx4g -Dmy.prop=value` |

---

## Database Schema

### Primary database (`batchdb`)

```sql
-- Spring Batch metadata (auto-created on first run)
BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, BATCH_STEP_EXECUTION, ...

-- Custom run statistics (auto-created)
BATCH_JOB_RUN_STATS, BATCH_STEP_RUN_STATS

-- Business tables
TRANSACTION_STAGING  -- upsert target for file-to-db jobs (keyed on transaction_id)
WORK_ITEM            -- processing queue for multithreaded-job
```

### Secondary database (`reportingdb`)

```sql
CUSTOMER_REPORT      -- source for db-to-file jobs (keyed on customer_id + report_date)
```

### Sample data

Run the SQL files under `src/main/resources/sql/` to initialise tables and load test data:

```sql
-- Primary DB
src/main/resources/sql/schema-primary.sql
src/main/resources/sql/sample-data-primary.sql

-- Secondary DB
src/main/resources/sql/schema-secondary.sql
src/main/resources/sql/sample-data-secondary.sql
```

The sample CSV for `file-to-db-job` and `mt-file-to-db-job` is at:
```
src/main/resources/sql/sample-transactions.csv
```
It contains 20 rows including 4 intentionally bad rows to exercise skip behaviour.
