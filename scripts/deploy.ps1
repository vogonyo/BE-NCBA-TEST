# =============================================================================
# RDAS – Kubernetes Deployment Script (PowerShell)
# Usage:
#   .\scripts\deploy.ps1 [OPTIONS]
#
# Parameters:
#   -Registry    Container registry prefix  (default: localhost:5000/rdas)
#   -Tag         Image tag                  (default: git short SHA)
#   -Namespace   Kubernetes namespace       (default: rdas)
#   -Kubeconfig  Path to kubeconfig file    (default: ~/.kube/config)
#   -SkipBuild   Skip Maven + Docker build
#   -SkipPush    Skip docker push
#   -DryRun      Print kubectl commands without applying
#
# Examples:
#   # Full build + deploy
#   .\scripts\deploy.ps1 -Registry "myregistry.io/rdas" -Tag "1.2.0"
#
#   # CI pipeline (image already built)
#   .\scripts\deploy.ps1 -Registry "myregistry.io/rdas" -Tag $env:CI_SHA -SkipBuild
# =============================================================================
[CmdletBinding()]
param(
    [string]$Registry   = "localhost:5000/rdas",
    [string]$Tag        = "",
    [string]$Namespace  = "rdas",
    [string]$Kubeconfig = "$env:USERPROFILE\.kube\config",
    [switch]$SkipBuild,
    [switch]$SkipPush,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Resolve paths ─────────────────────────────────────────────────────────────
$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$K8sDir      = Join-Path $ProjectRoot "k8s"

# ── Resolve default tag (git short SHA) ───────────────────────────────────────
if (-not $Tag) {
    $Tag = (git rev-parse --short HEAD 2>$null) ?? "latest"
}
$Image = "${Registry}:${Tag}"

# ── Colour helpers ────────────────────────────────────────────────────────────
function Write-Info    { param($m) Write-Host "[INFO]  $m" -ForegroundColor Cyan }
function Write-OK      { param($m) Write-Host "[OK]    $m" -ForegroundColor Green }
function Write-Warn    { param($m) Write-Host "[WARN]  $m" -ForegroundColor Yellow }
function Write-Fail    { param($m) Write-Host "[ERROR] $m" -ForegroundColor Red; exit 1 }

# ── kubectl wrapper (honours --dry-run) ───────────────────────────────────────
function Invoke-Kubectl {
    param([string[]]$Args)
    $base = @("--kubeconfig=$Kubeconfig")
    if ($DryRun) { $base += "--dry-run=client" }
    kubectl @base @Args
    if ($LASTEXITCODE -ne 0) { Write-Fail "kubectl command failed: kubectl $Args" }
}

# ── Step 1 – Prerequisites ────────────────────────────────────────────────────
Write-Info "Checking prerequisites..."
foreach ($cmd in @("kubectl", "docker")) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Fail "'$cmd' is not installed or not in PATH"
    }
}
kubectl version --client --kubeconfig=$Kubeconfig | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Fail "kubectl cannot connect – check KUBECONFIG" }
Write-OK "Prerequisites OK"

# ── Step 2 – Maven build ──────────────────────────────────────────────────────
if (-not $SkipBuild) {
    Write-Info "Building application with Maven (tests skipped)..."
    Push-Location $ProjectRoot
    .\mvnw.cmd package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Fail "Maven build failed" }
    Pop-Location
    Write-OK "Maven build complete"
}

# ── Step 3 – Docker build ─────────────────────────────────────────────────────
if (-not $SkipBuild) {
    Write-Info "Building Docker image: $Image"
    $buildDate  = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
    $gitCommit  = (git rev-parse HEAD 2>$null) ?? "unknown"
    docker build `
        --build-arg BUILD_DATE="$buildDate" `
        --build-arg GIT_COMMIT="$gitCommit" `
        -t "$Image" `
        -t "${Registry}:latest" `
        $ProjectRoot
    if ($LASTEXITCODE -ne 0) { Write-Fail "Docker build failed" }
    Write-OK "Docker image built: $Image"
}

# ── Step 4 – Docker push ──────────────────────────────────────────────────────
if (-not $SkipPush) {
    Write-Info "Pushing image to registry..."
    docker push "$Image"
    if ($LASTEXITCODE -ne 0) { Write-Fail "docker push failed for $Image" }
    docker push "${Registry}:latest"
    Write-OK "Image pushed: $Image"
}

# ── Step 5 – Patch image in deployment manifest ───────────────────────────────
Write-Info "Patching deployment image → $Image"
$deployFile = Join-Path $K8sDir "deployment.yaml"
(Get-Content $deployFile) -replace 'image: .*rdas.*', "image: $Image" |
    Set-Content $deployFile
Write-OK "deployment.yaml patched"

# ── Step 6 – Apply manifests in dependency order ─────────────────────────────
Write-Info "Applying Kubernetes manifests (namespace: $Namespace)..."
$manifests = @(
    "namespace.yaml",
    "configmap.yaml",
    "deployment.yaml",
    "service.yaml",
    "ingress.yaml",
    "hpa.yaml"
)
foreach ($file in $manifests) {
    $path = Join-Path $K8sDir $file
    Write-Info "  Applying $file..."
    Invoke-Kubectl @("apply", "-f", $path)
}
Write-OK "All manifests applied"

# ── Step 7 – Wait for rollout ─────────────────────────────────────────────────
if (-not $DryRun) {
    Write-Info "Waiting for rollout to complete (timeout: 5 min)..."
    kubectl --kubeconfig=$Kubeconfig rollout status deployment/rdas `
        -n $Namespace --timeout=300s
    if ($LASTEXITCODE -ne 0) { Write-Fail "Rollout did not complete within 5 minutes" }
    Write-OK "Rollout complete"

    # ── Step 8 – Smoke test ───────────────────────────────────────────────────
    Write-Info "Running smoke test against /actuator/health..."
    $pod = kubectl --kubeconfig=$Kubeconfig get pod -n $Namespace `
        -l app=rdas --field-selector=status.phase=Running `
        -o jsonpath='{.items[0].metadata.name}' 2>$null

    if ($pod) {
        $health = kubectl --kubeconfig=$Kubeconfig exec $pod -n $Namespace -- `
            wget -qO- http://localhost:8080/actuator/health 2>$null
        Write-Host "  Health response: $health"
        if ($health -match '"status":"UP"') {
            Write-OK "Smoke test PASSED – service is UP"
        } else {
            Write-Warn "Smoke test: service did not report UP – check logs"
        }
    } else {
        Write-Warn "No running pod found for smoke test"
    }

    # ── Step 9 – Summary ──────────────────────────────────────────────────────
    Write-Host ""
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Green
    Write-Host "  RDAS deployed successfully" -ForegroundColor Green
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Green
    Write-Host "  Image     : $Image"
    Write-Host "  Namespace : $Namespace"
    kubectl --kubeconfig=$Kubeconfig get pods -n $Namespace -l app=rdas
    Write-Host ""
}
