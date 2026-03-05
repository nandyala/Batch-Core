#!/usr/bin/env bash
# =============================================================================
# partitioned-db-to-file-job.sh — Partitioned export: DB → CSV (one file per partition)
# =============================================================================
#
# Usage:
#   partitioned-db-to-file-job.sh [batchDate=YYYY-MM-DD]
#
# batchDate defaults to today if not supplied.
# Output: ${batch.partitioned.outputDir}/partitioned-customer-report-{batchDate}-p{N}.csv
#
# Grid size (number of partitions) is controlled by batch.partitioned.gridSize (default: 4).
# Each partition runs in its own thread and writes to its own numbered output file.
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

exec "$LAUNCHER" partitioned-db-to-file-job "${EXTRA_PARAMS[@]+${EXTRA_PARAMS[@]}}" "$@"
