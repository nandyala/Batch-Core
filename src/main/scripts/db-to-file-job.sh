#!/usr/bin/env bash
# =============================================================================
# db-to-file-job.sh — Export customer report from DB to CSV
# =============================================================================
#
# Usage:
#   db-to-file-job.sh [batchDate=YYYY-MM-DD]
#
# batchDate defaults to today if not supplied.
# Output file: ${batch.dbtofile.outputDir}/customer-report-{batchDate}.csv
#
# Environment variables from run-batch.sh are all honoured.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="$SCRIPT_DIR/run-batch.sh"

# Default batchDate to today if not provided by the caller
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

exec "$LAUNCHER" db-to-file-job "${EXTRA_PARAMS[@]+"${EXTRA_PARAMS[@]}"}" "$@"
