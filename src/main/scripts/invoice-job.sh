#!/usr/bin/env bash
# =============================================================================
# invoice-job.sh — Convenience wrapper for the invoice batch job
# =============================================================================
#
# Usage:
#   invoice-job.sh [batchDate=YYYY-MM-DD] [key=value ...]
#
# batchDate defaults to today if not supplied.
#
# Examples:
#   invoice-job.sh                          # process today's invoices
#   invoice-job.sh batchDate=2026-01-15     # reprocess a specific date
#   invoice-job.sh batchDate=2026-01-15 retryMax=5
#
# All other environment variables from run-batch.sh are honoured:
#   APP_HOME, APP_JAR, JAVA_HOME, APP_PROFILE, APP_CONFIG_FILE, JVM_OPTS
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="$SCRIPT_DIR/run-batch.sh"

# ── Default batchDate to today ────────────────────────────────────────────────
# Inject only if the caller has not already supplied batchDate=...
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

# ── Delegate to generic launcher ──────────────────────────────────────────────
exec "$LAUNCHER" invoice-job "${EXTRA_PARAMS[@]+"${EXTRA_PARAMS[@]}"}" "$@"
