#!/usr/bin/env bash
# ============================================================
# smoke-test.sh — End-to-end smoke test for the Job Scheduler
# Run AFTER ./start-all.sh (services must be UP)
# Exit 0 = all tests passed, Exit 1 = failure
# ============================================================

set -euo pipefail
# Disable exit-on-error for the test body — we track failures ourselves
set +e

SUB="http://localhost:8081"
WRK="http://localhost:8082"
PASS=0
FAIL=0

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'

ok()   { echo -e "${GREEN}  ✅  PASS${NC} — $*"; PASS=$((PASS + 1)); }
fail() { echo -e "${RED}  ❌  FAIL${NC} — $*"; FAIL=$((FAIL + 1)); }
info() { echo -e "${YELLOW}──${NC} $*"; }

check_status() {
    local url=$1; local expected=$2; local label=$3
    actual=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    if [[ "$actual" == "$expected" ]]; then ok "$label (HTTP $actual)";
    else fail "$label — expected HTTP $expected, got $actual"; fi
}

echo ""
info "Smoke Test — Distributed Job Scheduler"
info "========================================"

# ── Health checks ───────────────────────────────────────────
info "1. Health endpoints"
check_status "$SUB/actuator/health" "200" "submission-service /actuator/health"
check_status "$WRK/actuator/health" "200" "worker-service /actuator/health"

# ── Swagger / OpenAPI ───────────────────────────────────────
info "2. Swagger / OpenAPI"
SWAGGER_SUB=$(curl -s -L -o /dev/null -w "%{http_code}" "$SUB/swagger-ui.html" 2>/dev/null || echo "000")
SWAGGER_WRK=$(curl -s -L -o /dev/null -w "%{http_code}" "$WRK/swagger-ui.html" 2>/dev/null || echo "000")
[[ "$SWAGGER_SUB" == "200" ]] && ok "submission-service Swagger UI (HTTP $SWAGGER_SUB)" || fail "submission-service Swagger UI — expected 200, got $SWAGGER_SUB"
check_status "$SUB/api-docs"         "200" "submission-service API docs JSON"
[[ "$SWAGGER_WRK" == "200" ]] && ok "worker-service Swagger UI (HTTP $SWAGGER_WRK)" || fail "worker-service Swagger UI — expected 200, got $SWAGGER_WRK"

# ── Home page ───────────────────────────────────────────────
info "3. Home pages"
check_status "$SUB/"  "200" "submission-service home"
check_status "$WRK/"  "200" "worker-service home"

# ── Prometheus metrics ──────────────────────────────────────
info "4. Prometheus metrics"
check_status "$SUB/actuator/prometheus" "200" "submission-service /actuator/prometheus"
check_status "$WRK/actuator/prometheus" "200" "worker-service /actuator/prometheus"

# ── Ping ────────────────────────────────────────────────────
info "5. Ping endpoints"
check_status "$SUB/api/v1/jobs/ping"   "200" "submission-service ping"
check_status "$WRK/api/v1/worker/ping" "200" "worker-service ping"

# ── Job submission ───────────────────────────────────────────
info "6. Job submission"
IDEMPOTENCY_KEY="smoke-test-$(date +%s)"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$SUB/api/v1/jobs" \
    -H "Content-Type: application/json" \
    -d "{\"type\":\"test\",\"payload\":{\"source\":\"smoke-test\"},\"priority\":1,\"idempotencyKey\":\"$IDEMPOTENCY_KEY\"}")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -1)

if [[ "$HTTP_CODE" == "202" ]]; then
    ok "POST /api/v1/jobs returned 202 Accepted"
    JOB_ID=$(echo "$BODY" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
    echo "     → jobId: $JOB_ID"
else
    fail "POST /api/v1/jobs — expected 202, got $HTTP_CODE"
    echo "     Response: $BODY"
    JOB_ID=""
fi

# ── Job status ───────────────────────────────────────────────
info "7. Job status lookup"
if [[ -n "$JOB_ID" ]]; then
    check_status "$SUB/api/v1/jobs/$JOB_ID" "200" "GET /api/v1/jobs/$JOB_ID"
else
    fail "Skipped — no jobId from previous step"
fi

# ── Duplicate submission (idempotency) ───────────────────────
info "8. Idempotency — duplicate key"
DUP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$SUB/api/v1/jobs" \
    -H "Content-Type: application/json" \
    -d "{\"type\":\"test\",\"payload\":{},\"idempotencyKey\":\"$IDEMPOTENCY_KEY\"}" 2>/dev/null || echo "000")
if [[ "$DUP_CODE" == "409" ]]; then
    ok "Duplicate key returns 409 Conflict"
else
    fail "Duplicate key — expected 409, got $DUP_CODE"
fi

# ── Validation ───────────────────────────────────────────────
info "9. Validation — missing type"
VAL_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$SUB/api/v1/jobs" \
    -H "Content-Type: application/json" \
    -d '{"payload":{"a":1}}' 2>/dev/null || echo "000")
if [[ "$VAL_CODE" == "400" ]]; then
    ok "Missing 'type' field returns 400 Bad Request"
else
    fail "Validation — expected 400, got $VAL_CODE"
fi

# ── Worker status ────────────────────────────────────────────
info "10. Worker status"
check_status "$WRK/api/v1/worker/status" "200" "worker-service /api/v1/worker/status"

# ── Summary ─────────────────────────────────────────────────
echo ""
echo -e "${YELLOW}════════════════════════════════${NC}"
echo -e "  PASSED: ${GREEN}$PASS${NC}  |  FAILED: ${RED}$FAIL${NC}"
echo -e "${YELLOW}════════════════════════════════${NC}"
echo ""

[[ $FAIL -eq 0 ]] && exit 0 || exit 1
