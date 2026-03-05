#!/usr/bin/env bash
# =============================================================================
# multithreaded-job.sh — Process WORK_ITEM table in parallel
# =============================================================================
#
# Usage:
#   multithreaded-job.sh [key=value ...]
#
# Thread count is controlled by batch.multithreaded.threadCount in
# application.properties (default: 4). Override per-run:
#   multithreaded-job.sh batch.multithreaded.threadCount=8
#
# Environment variables from run-batch.sh are all honoured.
# JVM_OPTS is especially relevant here — increase heap for high thread counts:
#   JVM_OPTS="-Xms512m -Xmx2g" ./multithreaded-job.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="$SCRIPT_DIR/run-batch.sh"

exec "$LAUNCHER" multithreaded-job "$@"
