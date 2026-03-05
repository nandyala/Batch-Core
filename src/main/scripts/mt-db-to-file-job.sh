#!/usr/bin/env bash
# =============================================================================
# mt-db-to-file-job.sh — Multi-threaded export: DB → CSV
# =============================================================================
#
# Usage:
#   mt-db-to-file-job.sh [batchDate=YYYY-MM-DD]
#
# batchDate defaults to today if not supplied.
# Output: ${batch.dbtofile.outputDir}/mt-customer-report-{batchDate}.csv
#
# Thread count is controlled by batch.mtdbtofile.threadCount (default: 4).
# Increase heap for high thread counts:
#   JVM_OPTS="-Xms512m -Xmx2g" ./mt-db-to-file-job.sh
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

exec "$LAUNCHER" mt-db-to-file-job "${EXTRA_PARAMS[@]+"${EXTRA_PARAMS[@]}"}" "$@"
