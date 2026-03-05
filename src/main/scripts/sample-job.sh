#!/usr/bin/env bash
# =============================================================================
# sample-job.sh — Convenience wrapper for the sample batch job
# =============================================================================
#
# Usage:
#   sample-job.sh [key=value ...]
#
# Examples:
#   sample-job.sh
#   sample-job.sh batchDate=2026-01-15
#
# All environment variables from run-batch.sh are honoured:
#   APP_HOME, APP_JAR, JAVA_HOME, APP_PROFILE, APP_CONFIG_FILE, JVM_OPTS
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="$SCRIPT_DIR/run-batch.sh"

exec "$LAUNCHER" sample-job "$@"
