#!/usr/bin/env bash
# =============================================================================
# RDAS – Kubernetes Rollback Script (Bash)
# Usage:
#   ./scripts/rollback.sh [OPTIONS]
#
# Options:
#   -n, --namespace   Kubernetes namespace  (default: rdas)
#   -k, --kubeconfig  Path to kubeconfig    (default: ~/.kube/config)
#   -r, --revision    Revision number to roll back to (default: previous)
#       --list        List available rollout history
#   -h, --help        Show this help
#
# Examples:
#   # Roll back to the previous revision
#   ./scripts/rollback.sh
#
#   # Roll back to a specific revision
#   ./scripts/rollback.sh --revision 3
#
#   # List rollout history first
#   ./scripts/rollback.sh --list
# =============================================================================
set -euo pipefail

NAMESPACE="rdas"
KUBECONFIG_PATH="${KUBECONFIG:-$HOME/.kube/config}"
REVISION=""
LIST_ONLY=false

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case $1 in
    -n|--namespace)  NAMESPACE="$2";       shift 2 ;;
    -k|--kubeconfig) KUBECONFIG_PATH="$2"; shift 2 ;;
    -r|--revision)   REVISION="$2";        shift 2 ;;
    --list)          LIST_ONLY=true;       shift   ;;
    -h|--help)
      sed -n '/^# Usage/,/^# =====/p' "$0" | grep -v '^# ====='
      exit 0 ;;
    *) error "Unknown option: $1" ;;
  esac
done

KUBECTL="kubectl --kubeconfig=$KUBECONFIG_PATH"

# ── Prerequisites ─────────────────────────────────────────────────────────────
command -v kubectl &>/dev/null || error "'kubectl' is not installed or not in PATH"

# ── List rollout history ──────────────────────────────────────────────────────
info "Rollout history for deployment/rdas (namespace: $NAMESPACE):"
$KUBECTL rollout history deployment/rdas -n "$NAMESPACE"

[[ "$LIST_ONLY" == "true" ]] && exit 0

# ── Capture current image before rollback (for audit log) ────────────────────
CURRENT_IMAGE=$($KUBECTL get deployment rdas -n "$NAMESPACE" \
  -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || echo "unknown")
info "Current image: $CURRENT_IMAGE"

# ── Confirm ───────────────────────────────────────────────────────────────────
if [[ -n "$REVISION" ]]; then
  warn "Rolling back to revision $REVISION..."
else
  warn "Rolling back to previous revision..."
fi

read -r -p "Are you sure? (yes/no): " CONFIRM
[[ "$CONFIRM" == "yes" ]] || { info "Rollback cancelled."; exit 0; }

# ── Execute rollback ──────────────────────────────────────────────────────────
if [[ -n "$REVISION" ]]; then
  $KUBECTL rollout undo deployment/rdas -n "$NAMESPACE" --to-revision="$REVISION"
else
  $KUBECTL rollout undo deployment/rdas -n "$NAMESPACE"
fi

# ── Wait for rollback to complete ─────────────────────────────────────────────
info "Waiting for rollback to complete (timeout: 5 min)..."
$KUBECTL rollout status deployment/rdas -n "$NAMESPACE" --timeout=300s \
  || error "Rollback did not complete within 5 minutes"

# ── Summary ───────────────────────────────────────────────────────────────────
NEW_IMAGE=$($KUBECTL get deployment rdas -n "$NAMESPACE" \
  -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || echo "unknown")

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  RDAS rollback complete${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo "  Previous image : $CURRENT_IMAGE"
echo "  Active image   : $NEW_IMAGE"
echo "  Namespace      : $NAMESPACE"
$KUBECTL get pods -n "$NAMESPACE" -l app=rdas \
  --no-headers -o custom-columns="NAME:.metadata.name,STATUS:.status.phase,READY:.status.containerStatuses[0].ready"
echo ""
success "Done"
