#!/usr/bin/env bash
# =============================================================================
# mt-file-to-db-job.sh — Multi-threaded import: CSV → DB
# =============================================================================
#
# Usage:
#   mt-file-to-db-job.sh [batchDate=YYYY-MM-DD] [inputFile=/path/to/file.csv]
#
# batchDate defaults to today if not supplied.
# When inputFile is not given, the job uses:
#   ${batch.filetodb.inputDir}/{batchDate}/transactions.csv
#
# Thread count is controlled by batch.mtfiletodb.threadCount (default: 4).
# The writer is JdbcBatchItemWriter — ensure the DB connection pool
# (datasource.pool.maxSize) is >= threadCount to avoid connection starvation.
#
# All environment variables from run-batch.sh are honoured.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="$SCRIPT_DIR/run-batch.sh"

HAS_BATCH_DATE=false
for arg in "$@"; do
    if [[ "$arg" == batchDate=* ]]; then
        HAS_BATCH_DATE=true
        break
    fi
done

EXTRA_PARAMS=()
if [[ "$HAS_BATCH_DATE" == false ]]; then
    EXTRA_PARAMS+=("batchDate=$(date '+%Y-%m-%d')")
fi

exec "$LAUNCHER" mt-file-to-db-job "${EXTRA_PARAMS[@]+"${EXTRA_PARAMS[@]}"}" "$@"
