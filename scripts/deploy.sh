#!/usr/bin/env bash
# =============================================================================
# RDAS – Kubernetes Deployment Script (Bash)
# Usage:
#   ./scripts/deploy.sh [OPTIONS]
#
# Options:
#   -r, --registry    Container registry prefix  (default: localhost:5000)
#   -t, --tag         Image tag                  (default: git short SHA)
#   -n, --namespace   Kubernetes namespace       (default: rdas)
#   -k, --kubeconfig  Path to kubeconfig file    (default: ~/.kube/config)
#       --skip-build  Skip Maven + Docker build steps
#       --skip-push   Skip docker push step
#       --dry-run     Print kubectl commands without applying them
#   -h, --help        Show this help
#
# Examples:
#   # Full build + deploy using a local registry
#   ./scripts/deploy.sh --registry myregistry.io/rdas --tag 1.2.0
#
#   # CI pipeline (image already built and pushed)
#   ./scripts/deploy.sh --registry myregistry.io/rdas --tag $CI_SHA --skip-build
# =============================================================================
set -euo pipefail

# ── Defaults ─────────────────────────────────────────────────────────────────
REGISTRY="localhost:5000/rdas"
TAG=$(git rev-parse --short HEAD 2>/dev/null || echo "latest")
NAMESPACE="rdas"
KUBECONFIG_PATH="${KUBECONFIG:-$HOME/.kube/config}"
SKIP_BUILD=false
SKIP_PUSH=false
DRY_RUN=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
K8S_DIR="$PROJECT_ROOT/k8s"

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case $1 in
    -r|--registry)   REGISTRY="$2";        shift 2 ;;
    -t|--tag)        TAG="$2";             shift 2 ;;
    -n|--namespace)  NAMESPACE="$2";       shift 2 ;;
    -k|--kubeconfig) KUBECONFIG_PATH="$2"; shift 2 ;;
    --skip-build)    SKIP_BUILD=true;      shift   ;;
    --skip-push)     SKIP_PUSH=true;       shift   ;;
    --dry-run)       DRY_RUN=true;         shift   ;;
    -h|--help)
      sed -n '/^# Usage/,/^# =====/p' "$0" | grep -v '^# ====='
      exit 0 ;;
    *) error "Unknown option: $1" ;;
  esac
done

IMAGE="${REGISTRY}:${TAG}"
KUBECTL="kubectl --kubeconfig=$KUBECONFIG_PATH"
[[ "$DRY_RUN" == "true" ]] && KUBECTL="$KUBECTL --dry-run=client"

# ── Step 1 – Prerequisites ────────────────────────────────────────────────────
info "Checking prerequisites..."
for cmd in kubectl docker; do
  command -v "$cmd" &>/dev/null || error "'$cmd' is not installed or not in PATH"
done
$KUBECTL version --client &>/dev/null || error "kubectl cannot connect – check KUBECONFIG"
success "Prerequisites OK"

# ── Step 2 – Maven build ──────────────────────────────────────────────────────
if [[ "$SKIP_BUILD" == "false" ]]; then
  info "Building application with Maven (tests skipped)..."
  cd "$PROJECT_ROOT"
  ./mvnw package -DskipTests -q || error "Maven build failed"
  success "Maven build complete"
fi

# ── Step 3 – Docker build ─────────────────────────────────────────────────────
if [[ "$SKIP_BUILD" == "false" ]]; then
  info "Building Docker image: $IMAGE"
  docker build \
    --build-arg BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --build-arg GIT_COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)" \
    -t "$IMAGE" \
    -t "${REGISTRY}:latest" \
    "$PROJECT_ROOT" || error "Docker build failed"
  success "Docker image built: $IMAGE"
fi

# ── Step 4 – Docker push ──────────────────────────────────────────────────────
if [[ "$SKIP_PUSH" == "false" ]]; then
  info "Pushing image to registry..."
  docker push "$IMAGE"       || error "Docker push failed for $IMAGE"
  docker push "${REGISTRY}:latest" || warn "Could not push :latest tag"
  success "Image pushed: $IMAGE"
fi

# ── Step 5 – Update image reference in deployment manifest ───────────────────
info "Patching deployment image → $IMAGE"
sed -i.bak "s|image: .*rdas.*|image: $IMAGE|g" "$K8S_DIR/deployment.yaml"
success "deployment.yaml patched"

# ── Step 6 – Apply manifests in dependency order ─────────────────────────────
info "Applying Kubernetes manifests (namespace: $NAMESPACE)..."

apply() {
  local file="$1"
  info "  Applying $file..."
  $KUBECTL apply -f "$file"
}

apply "$K8S_DIR/namespace.yaml"
apply "$K8S_DIR/configmap.yaml"
apply "$K8S_DIR/deployment.yaml"
apply "$K8S_DIR/service.yaml"
apply "$K8S_DIR/ingress.yaml"
apply "$K8S_DIR/hpa.yaml"

success "All manifests applied"

# ── Step 7 – Wait for rollout ─────────────────────────────────────────────────
if [[ "$DRY_RUN" == "false" ]]; then
  info "Waiting for rollout to complete (timeout: 5 min)..."
  $KUBECTL rollout status deployment/rdas \
    -n "$NAMESPACE" \
    --timeout=300s || error "Rollout did not complete within 5 minutes"
  success "Rollout complete"

  # ── Step 8 – Smoke test ───────────────────────────────────────────────────
  info "Running smoke test against /actuator/health..."
  POD=$($KUBECTL get pod -n "$NAMESPACE" \
    -l app=rdas --field-selector=status.phase=Running \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)

  if [[ -n "$POD" ]]; then
    HEALTH=$($KUBECTL exec "$POD" -n "$NAMESPACE" -- \
      wget -qO- http://localhost:8080/actuator/health 2>/dev/null || echo "{}")
    echo "  Health response: $HEALTH"
    if echo "$HEALTH" | grep -q '"status":"UP"'; then
      success "Smoke test PASSED – service is UP"
    else
      warn "Smoke test: service did not report UP status – check logs"
    fi
  else
    warn "No running pod found for smoke test"
  fi

  # ── Step 9 – Summary ──────────────────────────────────────────────────────
  echo ""
  echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${GREEN}  RDAS deployed successfully${NC}"
  echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo "  Image     : $IMAGE"
  echo "  Namespace : $NAMESPACE"
  $KUBECTL get pods -n "$NAMESPACE" -l app=rdas \
    --no-headers -o custom-columns="NAME:.metadata.name,STATUS:.status.phase,READY:.status.containerStatuses[0].ready"
  echo ""
fi
