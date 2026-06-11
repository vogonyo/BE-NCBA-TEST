# =============================================================================
# RDAS – Kubernetes Rollback Script (PowerShell)
# Usage:
#   .\scripts\rollback.ps1 [OPTIONS]
#
# Parameters:
#   -Namespace   Kubernetes namespace  (default: rdas)
#   -Kubeconfig  Path to kubeconfig    (default: ~/.kube/config)
#   -Revision    Revision to roll back to (default: previous)
#   -List        List rollout history only, then exit
#
# Examples:
#   # Roll back to the previous revision
#   .\scripts\rollback.ps1
#
#   # Roll back to a specific revision
#   .\scripts\rollback.ps1 -Revision 3
#
#   # List rollout history first
#   .\scripts\rollback.ps1 -List
# =============================================================================
[CmdletBinding()]
param(
    [string]$Namespace  = "rdas",
    [string]$Kubeconfig = "$env:USERPROFILE\.kube\config",
    [string]$Revision   = "",
    [switch]$List
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Info  { param($m) Write-Host "[INFO]  $m" -ForegroundColor Cyan }
function Write-OK    { param($m) Write-Host "[OK]    $m" -ForegroundColor Green }
function Write-Warn  { param($m) Write-Host "[WARN]  $m" -ForegroundColor Yellow }
function Write-Fail  { param($m) Write-Host "[ERROR] $m" -ForegroundColor Red; exit 1 }

# ── Prerequisites ─────────────────────────────────────────────────────────────
if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    Write-Fail "'kubectl' is not installed or not in PATH"
}

# ── List rollout history ──────────────────────────────────────────────────────
Write-Info "Rollout history for deployment/rdas (namespace: $Namespace):"
kubectl --kubeconfig=$Kubeconfig rollout history deployment/rdas -n $Namespace

if ($List) { exit 0 }

# ── Capture current image ─────────────────────────────────────────────────────
$currentImage = kubectl --kubeconfig=$Kubeconfig get deployment rdas -n $Namespace `
    -o jsonpath='{.spec.template.spec.containers[0].image}' 2>$null
Write-Info "Current image: $currentImage"

# ── Confirm ───────────────────────────────────────────────────────────────────
if ($Revision) {
    Write-Warn "About to roll back to revision $Revision..."
} else {
    Write-Warn "About to roll back to previous revision..."
}

$confirm = Read-Host "Are you sure? (yes/no)"
if ($confirm -ne "yes") { Write-Info "Rollback cancelled."; exit 0 }

# ── Execute rollback ──────────────────────────────────────────────────────────
if ($Revision) {
    kubectl --kubeconfig=$Kubeconfig rollout undo deployment/rdas `
        -n $Namespace --to-revision=$Revision
} else {
    kubectl --kubeconfig=$Kubeconfig rollout undo deployment/rdas -n $Namespace
}
if ($LASTEXITCODE -ne 0) { Write-Fail "kubectl rollout undo failed" }

# ── Wait for rollback to complete ─────────────────────────────────────────────
Write-Info "Waiting for rollback to complete (timeout: 5 min)..."
kubectl --kubeconfig=$Kubeconfig rollout status deployment/rdas `
    -n $Namespace --timeout=300s
if ($LASTEXITCODE -ne 0) { Write-Fail "Rollback did not complete within 5 minutes" }

# ── Summary ───────────────────────────────────────────────────────────────────
$newImage = kubectl --kubeconfig=$Kubeconfig get deployment rdas -n $Namespace `
    -o jsonpath='{.spec.template.spec.containers[0].image}' 2>$null

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Green
Write-Host "  RDAS rollback complete" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Green
Write-Host "  Previous image : $currentImage"
Write-Host "  Active image   : $newImage"
Write-Host "  Namespace      : $Namespace"
kubectl --kubeconfig=$Kubeconfig get pods -n $Namespace -l app=rdas
Write-Host ""
Write-OK "Done"
