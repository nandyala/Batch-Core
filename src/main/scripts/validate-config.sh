#!/usr/bin/env bash
# =============================================================================
# validate-config.sh — Post-deploy smoke test: dry-run every known job
# =============================================================================
#
# Runs --dry-run for each registered job to verify:
#   - Spring context loads and all ${placeholders} resolve
#   - Job bean is found
#   - JobParametersValidator passes with typical parameters
#   - Step graph is non-empty
#
# Designed to be run immediately after a deployment to confirm the new
# build and its configuration are consistent before any live job executes.
#
# Usage:
#   validate-config.sh
#
# Environment variables from run-batch.sh are all honoured
# (APP_HOME, APP_JAR, JAVA_HOME, APP_PROFILE, APP_CONFIG_FILE, JVM_OPTS).
#
# Exit codes:
#   0 — all jobs passed validation
#   1 — one or more jobs failed validation
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAUNCHER="$SCRIPT_DIR/run-batch.sh"

# ── Jobs to validate ──────────────────────────────────────────────────────────
# Add new job slugs here as new jobs are onboarded.
# Provide representative parameters each job's validator requires.
declare -A JOB_PARAMS=(
    ["invoice-job"]="batchDate=$(date '+%Y-%m-%d')"
    ["sample-job"]=""
    ["db-to-file-job"]="batchDate=$(date '+%Y-%m-%d')"
    ["file-to-db-job"]="batchDate=$(date '+%Y-%m-%d')"
    ["multithreaded-job"]=""
    ["mt-db-to-file-job"]="batchDate=$(date '+%Y-%m-%d')"
    ["mt-file-to-db-job"]="batchDate=$(date '+%Y-%m-%d')"
    ["partitioned-db-to-file-job"]="batchDate=$(date '+%Y-%m-%d')"
)

# ── Run dry-run for each job ──────────────────────────────────────────────────
PASSED=()
FAILED=()

SEP="================================================================="
echo ""
echo "$SEP"
echo "  DEPLOYMENT VALIDATION — $(date '+%Y-%m-%d %H:%M:%S')"
echo "$SEP"
echo ""

for job in "${!JOB_PARAMS[@]}"; do
    params="${JOB_PARAMS[$job]}"
    echo "--- Validating: $job ---"

    # Build argument list; skip empty params string
    ARGS=("--dry-run" "$job")
    [[ -n "$params" ]] && ARGS+=($params)   # word-split intentional for key=value pairs

    set +e
    "$LAUNCHER" "${ARGS[@]}"
    EXIT_CODE=$?
    set -e

    if [[ $EXIT_CODE -eq 0 ]]; then
        echo "[PASS] $job"
        PASSED+=("$job")
    else
        echo "[FAIL] $job  (exit $EXIT_CODE)"
        FAILED+=("$job")
    fi
    echo ""
done

# ── Summary ───────────────────────────────────────────────────────────────────
echo "$SEP"
echo "  VALIDATION SUMMARY"
echo "$SEP"
echo "  Passed : ${#PASSED[@]}  — ${PASSED[*]:-none}"
echo "  Failed : ${#FAILED[@]}  — ${FAILED[*]:-none}"
echo "$SEP"
echo ""

if [[ ${#FAILED[@]} -gt 0 ]]; then
    echo "DEPLOYMENT VALIDATION FAILED. Fix the issues above before running live jobs." >&2
    exit 1
fi

echo "DEPLOYMENT VALIDATION PASSED. All jobs are configured correctly."
exit 0
