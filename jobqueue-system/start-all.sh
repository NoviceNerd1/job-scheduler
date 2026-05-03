#!/usr/bin/env bash
# ============================================================
# start-all.sh — Start the full Distributed Job Scheduler stack
# Usage:
#   ./start-all.sh          → kill stale + start fresh
#   ./start-all.sh --stop   → stop everything
#   ./start-all.sh --status → health check
# ============================================================

set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_FILE="$ROOT_DIR/.running_pids"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

log()  { echo -e "${GREEN}[jobqueue]${NC} $*"; }
warn() { echo -e "${YELLOW}[jobqueue]${NC} $*"; }
err()  { echo -e "${RED}[jobqueue] ERROR:${NC} $*"; exit 1; }
info() { echo -e "${CYAN}[jobqueue]${NC} $*"; }

# ---------- Port cleanup helper ----------
kill_port() {
    local port="$1"
    local pids
    pids=$(lsof -ti:"$port" 2>/dev/null || true)
    if [[ -n "$pids" ]]; then
        echo "$pids" | xargs kill -9 2>/dev/null || true
        warn "  Killed stale process(es) on port $port"
    fi
}

# ---------- Stop mode ----------
stop_all() {
    log "Stopping all microservices..."
    # Kill by saved PID file
    if [[ -f "$PID_FILE" ]]; then
        while read -r pid; do
            kill "$pid" 2>/dev/null && log "  Killed PID $pid" || true
        done < "$PID_FILE"
        rm -f "$PID_FILE"
    fi
    # Also kill by port to be safe
    kill_port 8081
    kill_port 8082
    kill_port 35729   # DevTools LiveReload port
    log "All services stopped."
}

if [[ "$1" == "--stop" ]]; then
    stop_all
    exit 0
fi

# ---------- Status mode ----------
if [[ "$1" == "--status" ]]; then
    echo ""
    info "── Service Health ───────────────────────────────────"
    for svc in "submission-service:8081" "worker-service:8082"; do
        name="${svc%%:*}"; port="${svc##*:}"
        status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/actuator/health" 2>/dev/null || echo "000")
        if [[ "$status" == "200" ]]; then
            log "  ✅  $name  → http://localhost:$port (HTTP $status)"
        else
            warn "  ❌  $name  → http://localhost:$port (HTTP $status — DOWN)"
        fi
    done
    echo ""
    exit 0
fi

# ---------- Pre-flight checks ----------
command -v mvn  &>/dev/null || err "Maven (mvn) not found in PATH"
command -v curl &>/dev/null || err "curl not found in PATH"

mkdir -p "$LOG_DIR"

# ---------- Kill any stale processes first ----------
log "Clearing stale port bindings..."
kill_port 8081
kill_port 8082
kill_port 35729
sleep 1

# ---------- Build ----------
log "Building all modules (skipping tests)..."
cd "$ROOT_DIR"
mvn clean install -DskipTests -q || err "Build failed. Run 'mvn clean install' for details."

# ---------- Reset PID file ----------
> "$PID_FILE"

# ---------- Launch Submission Service ----------
log "Starting submission-service  →  http://localhost:8081"
(cd "$ROOT_DIR/submission-service" && \
    mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="-Dspring.jpa.open-in-view=false -Dspring.devtools.livereload.enabled=false" \
    > "$LOG_DIR/submission-service.log" 2>&1) &
echo "$!" >> "$PID_FILE"

# ---------- Launch Worker Service ----------
log "Starting worker-service      →  http://localhost:8082"
(cd "$ROOT_DIR/worker-service" && \
    mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="-Dspring.jpa.open-in-view=false -Dspring.devtools.livereload.enabled=false" \
    > "$LOG_DIR/worker-service.log" 2>&1) &
echo "$!" >> "$PID_FILE"

# ---------- Wait & Health check ----------
log "Waiting for services to start..."
for i in {1..30}; do
    sleep 2
    sub=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health 2>/dev/null || echo "000")
    wrk=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/health 2>/dev/null || echo "000")
    if [[ "$sub" == "200" && "$wrk" == "200" ]]; then
        break
    fi
    printf "."
done
echo ""

# ---------- Final status ----------
echo ""
log "══════════════════════════════════════════════════════"
for svc in "submission-service:8081" "worker-service:8082"; do
    name="${svc%%:*}"; port="${svc##*:}"
    status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/actuator/health" 2>/dev/null || echo "000")
    if [[ "$status" == "200" ]]; then
        log "  ✅  $name   UP  →  http://localhost:$port"
    else
        warn "  ⚠️   $name  NOT UP (check logs/$name.log)"
    fi
done
log "══════════════════════════════════════════════════════"
echo ""
log "Commands:"
info "  ./start-all.sh --stop     Stop all services"
info "  ./start-all.sh --status   Check health"
info "  tail -f logs/submission-service.log"
info "  tail -f logs/worker-service.log"
