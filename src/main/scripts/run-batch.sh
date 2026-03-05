#!/usr/bin/env bash
# =============================================================================
# run-batch.sh — Generic Spring Batch job launcher
# =============================================================================
#
# Usage:
#   run-batch.sh [--dry-run] <job-slug> [key=value ...]
#
# Examples:
#   run-batch.sh invoice-job  batchDate=2026-01-15
#   run-batch.sh --dry-run    invoice-job  batchDate=2026-01-15
#   run-batch.sh --validate   sample-job
#
# Environment variables (all optional):
#   APP_HOME        — deployment root; jar and logs/ live here
#                     (default: directory containing this script)
#   APP_JAR         — jar file name              (default: batch.jar)
#   JAVA_HOME       — JDK installation root      (default: system java)
#   APP_PROFILE     — Spring profile to activate (default: none)
#                     loads classpath:application-<profile>.properties
#   APP_CONFIG_FILE — path to an external properties override file
#                     (default: none)
#   JVM_OPTS        — extra JVM flags            (default: -Xms256m -Xmx512m)
#
# Exit codes (mirrors SpringBatchApplication):
#   0  — COMPLETED or DRY-RUN PASSED
#   1  — FAILED / STOPPED
#   2  — Bad arguments, startup error, or DRY-RUN FAILED
# =============================================================================

set -euo pipefail

# ── Resolve directories ───────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${APP_HOME:-$SCRIPT_DIR}"
APP_JAR="${APP_JAR:-batch.jar}"
JVM_OPTS="${JVM_OPTS:--Xms256m -Xmx512m}"

JAR_PATH="$APP_HOME/$APP_JAR"

# ── Validate arguments ────────────────────────────────────────────────────────
if [[ $# -eq 0 ]]; then
    echo "Usage: $(basename "$0") [--dry-run|--validate] <job-slug> [key=value ...]" >&2
    exit 2
fi

# ── Validate jar exists ───────────────────────────────────────────────────────
if [[ ! -f "$JAR_PATH" ]]; then
    echo "ERROR: Jar not found: $JAR_PATH" >&2
    echo "       Set APP_HOME or APP_JAR to the correct location." >&2
    exit 2
fi

# ── Java executable ───────────────────────────────────────────────────────────
if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD="java"
fi

if ! command -v "$JAVA_CMD" &>/dev/null; then
    echo "ERROR: Java not found. Set JAVA_HOME or add java to PATH." >&2
    exit 2
fi

# ── Log directory — logback writes logs/ relative to CWD ─────────────────────
# We cd to APP_HOME so all relative paths (logs/, etc.) resolve correctly.
mkdir -p "$APP_HOME/logs"
cd "$APP_HOME"

# ── Build JVM -D flags ────────────────────────────────────────────────────────
D_FLAGS=()
[[ -n "${APP_PROFILE:-}"     ]] && D_FLAGS+=("-Dapp.profile=$APP_PROFILE")
[[ -n "${APP_CONFIG_FILE:-}" ]] && D_FLAGS+=("-Dapp.config.file=$APP_CONFIG_FILE")

# ── Determine job slug for display (first non-flag arg) ───────────────────────
JOB_LABEL="batch"
for arg in "$@"; do
    if [[ "$arg" != --* ]]; then
        JOB_LABEL="$arg"
        break
    fi
done

# ── Launch ────────────────────────────────────────────────────────────────────
echo "$(date '+%Y-%m-%d %H:%M:%S') INFO  [$JOB_LABEL] Starting launcher"
echo "$(date '+%Y-%m-%d %H:%M:%S') INFO  [$JOB_LABEL] Jar     : $JAR_PATH"
echo "$(date '+%Y-%m-%d %H:%M:%S') INFO  [$JOB_LABEL] Java    : $($JAVA_CMD -version 2>&1 | head -1)"
echo "$(date '+%Y-%m-%d %H:%M:%S') INFO  [$JOB_LABEL] Args    : $*"
[[ ${#D_FLAGS[@]} -gt 0 ]] && \
    echo "$(date '+%Y-%m-%d %H:%M:%S') INFO  [$JOB_LABEL] JVM flags: ${D_FLAGS[*]}"

# Run the jar; capture exit code even through pipe
set +e
"$JAVA_CMD" \
    $JVM_OPTS \
    ${D_FLAGS[@]+"${D_FLAGS[@]}"} \
    -jar "$JAR_PATH" \
    "$@"
EXIT_CODE=$?
set -e

echo "$(date '+%Y-%m-%d %H:%M:%S') INFO  [$JOB_LABEL] Finished — exit code: $EXIT_CODE"

exit "$EXIT_CODE"
