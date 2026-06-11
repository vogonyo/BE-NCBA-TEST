#!/usr/bin/env bash
# =============================================================================
# RDAS – Kubernetes Health Check Script (Bash)
# Usage:
#   ./scripts/health-check.sh [OPTIONS]
#
# Options:
#   -n, --namespace   Kubernetes namespace  (default: rdas)
#   -k, --kubeconfig  Path to kubeconfig    (default: ~/.kube/config)
#   -w, --watch       Repeat every N seconds (e.g. --watch 10)
#   -h, --help        Show this help
# =============================================================================
set -euo pipefail

NAMESPACE="rdas"
KUBECONFIG_PATH="${KUBECONFIG:-$HOME/.kube/config}"
WATCH_INTERVAL=0

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[FAIL]${NC}  $*"; }

while [[ $# -gt 0 ]]; do
  case $1 in
    -n|--namespace)  NAMESPACE="$2";        shift 2 ;;
    -k|--kubeconfig) KUBECONFIG_PATH="$2";  shift 2 ;;
    -w|--watch)      WATCH_INTERVAL="$2";   shift 2 ;;
    -h|--help)
      sed -n '/^# Usage/,/^# =====/p' "$0" | grep -v '^# ====='
      exit 0 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

KUBECTL="kubectl --kubeconfig=$KUBECONFIG_PATH"

run_checks() {
  echo ""
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BOLD}  RDAS Health Check  –  $(date '+%Y-%m-%d %H:%M:%S')${NC}"
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

  # ── 1. Pod status ───────────────────────────────────────────────────────────
  echo -e "\n${BOLD}[1] Pod Status${NC}"
  $KUBECTL get pods -n "$NAMESPACE" -l app=rdas \
    -o custom-columns="NAME:.metadata.name,STATUS:.status.phase,READY:.status.containerStatuses[0].ready,RESTARTS:.status.containerStatuses[0].restartCount,AGE:.metadata.creationTimestamp"

  READY_COUNT=$($KUBECTL get pods -n "$NAMESPACE" -l app=rdas \
    --field-selector=status.phase=Running \
    -o jsonpath='{.items[*].status.containerStatuses[0].ready}' 2>/dev/null \
    | tr ' ' '\n' | grep -c "true" || echo 0)
  TOTAL_COUNT=$($KUBECTL get pods -n "$NAMESPACE" -l app=rdas \
    --no-headers 2>/dev/null | wc -l | tr -d ' ' || echo 0)
  echo "  Ready pods: $READY_COUNT / $TOTAL_COUNT"

  # ── 2. Deployment status ────────────────────────────────────────────────────
  echo -e "\n${BOLD}[2] Deployment${NC}"
  $KUBECTL get deployment rdas -n "$NAMESPACE" \
    -o custom-columns="NAME:.metadata.name,DESIRED:.spec.replicas,READY:.status.readyReplicas,UPDATED:.status.updatedReplicas,AVAILABLE:.status.availableReplicas"

  # ── 3. HPA status ───────────────────────────────────────────────────────────
  echo -e "\n${BOLD}[3] Horizontal Pod Autoscaler${NC}"
  $KUBECTL get hpa -n "$NAMESPACE" 2>/dev/null || warn "No HPA found"

  # ── 4. Service endpoints ────────────────────────────────────────────────────
  echo -e "\n${BOLD}[4] Service & Endpoints${NC}"
  $KUBECTL get svc -n "$NAMESPACE" -l app=rdas
  $KUBECTL get endpoints -n "$NAMESPACE" -l app=rdas 2>/dev/null || true

  # ── 5. Actuator health via kubectl exec ────────────────────────────────────
  echo -e "\n${BOLD}[5] Actuator Health (via pod exec)${NC}"
  POD=$($KUBECTL get pod -n "$NAMESPACE" -l app=rdas \
    --field-selector=status.phase=Running \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)

  if [[ -n "$POD" ]]; then
    for path in "/actuator/health" "/actuator/health/liveness" "/actuator/health/readiness"; do
      RESP=$($KUBECTL exec "$POD" -n "$NAMESPACE" -- \
        wget -qO- "http://localhost:8080${path}" 2>/dev/null || echo '{"status":"UNREACHABLE"}')
      STATUS=$(echo "$RESP" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
      if [[ "$STATUS" == "UP" ]]; then
        success "$path → $STATUS"
      else
        error "$path → ${STATUS:-UNKNOWN}"
      fi
    done

    # Cache status
    echo -e "\n  Cache status:"
    $KUBECTL exec "$POD" -n "$NAMESPACE" -- \
      wget -qO- "http://localhost:8080/api/v1/cache/status" 2>/dev/null \
      | python3 -m json.tool 2>/dev/null || echo "  (could not fetch cache status)"
  else
    warn "No running pod found – skipping actuator checks"
  fi

  # ── 6. Recent events ────────────────────────────────────────────────────────
  echo -e "\n${BOLD}[6] Recent Kubernetes Events (last 10)${NC}"
  $KUBECTL get events -n "$NAMESPACE" \
    --sort-by='.lastTimestamp' \
    --field-selector=involvedObject.name=rdas \
    2>/dev/null | tail -10 || warn "No events found"

  # ── 7. Recent logs ──────────────────────────────────────────────────────────
  echo -e "\n${BOLD}[7] Recent Logs (last 20 lines from first pod)${NC}"
  if [[ -n "$POD" ]]; then
    $KUBECTL logs "$POD" -n "$NAMESPACE" --tail=20 2>/dev/null || warn "Could not fetch logs"
  else
    warn "No running pod found"
  fi

  echo ""
}

if [[ "$WATCH_INTERVAL" -gt 0 ]]; then
  while true; do
    run_checks
    echo "  Next check in ${WATCH_INTERVAL}s... (Ctrl+C to stop)"
    sleep "$WATCH_INTERVAL"
  done
else
  run_checks
fi
