#!/usr/bin/env bash
# =============================================================================
# file-to-db-job.sh — Import CSV transaction file into the database
# =============================================================================
#
# Usage:
#   file-to-db-job.sh [batchDate=YYYY-MM-DD] [inputFile=/path/to/file.csv]
#
# batchDate defaults to today if not supplied.
# When inputFile is not given, the job uses:
#   ${batch.filetodb.inputDir}/{batchDate}/transactions.csv
#
# Examples:
#   file-to-db-job.sh                                 # today's file, default path
#   file-to-db-job.sh batchDate=2026-01-15            # specific date, default path
#   file-to-db-job.sh inputFile=/mnt/sftp/txn.csv batchDate=2026-01-15
#
# Environment variables from run-batch.sh are all honoured.
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

exec "$LAUNCHER" file-to-db-job "${EXTRA_PARAMS[@]+"${EXTRA_PARAMS[@]}"}" "$@"
