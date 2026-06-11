# =============================================================================
# RDAS – Kubernetes Health Check Script (PowerShell)
# Usage:
#   .\scripts\health-check.ps1 [OPTIONS]
#
# Parameters:
#   -Namespace   Kubernetes namespace  (default: rdas)
#   -Kubeconfig  Path to kubeconfig    (default: ~/.kube/config)
#   -Watch       Repeat every N seconds (0 = run once)
# =============================================================================
[CmdletBinding()]
param(
    [string]$Namespace  = "rdas",
    [string]$Kubeconfig = "$env:USERPROFILE\.kube\config",
    [int]$Watch         = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "SilentlyContinue"

function Write-Header { param($m) Write-Host $m -ForegroundColor White }
function Write-OK     { param($m) Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Warn   { param($m) Write-Host "[WARN] $m" -ForegroundColor Yellow }
function Write-Fail   { param($m) Write-Host "[FAIL] $m" -ForegroundColor Red }
function Write-Info   { param($m) Write-Host "[INFO] $m" -ForegroundColor Cyan }

function Invoke-K {
    param([string[]]$Args)
    kubectl --kubeconfig=$Kubeconfig @Args
}

function Run-Checks {
    Write-Host ""
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor White
    Write-Host "  RDAS Health Check  –  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor White
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor White

    # ── 1. Pod status ─────────────────────────────────────────────────────────
    Write-Host "`n[1] Pod Status" -ForegroundColor Cyan
    Invoke-K @("get", "pods", "-n", $Namespace, "-l", "app=rdas",
        "-o", "custom-columns=NAME:.metadata.name,STATUS:.status.phase,READY:.status.containerStatuses[0].ready,RESTARTS:.status.containerStatuses[0].restartCount")

    # ── 2. Deployment status ──────────────────────────────────────────────────
    Write-Host "`n[2] Deployment" -ForegroundColor Cyan
    Invoke-K @("get", "deployment", "rdas", "-n", $Namespace,
        "-o", "custom-columns=NAME:.metadata.name,DESIRED:.spec.replicas,READY:.status.readyReplicas,UPDATED:.status.updatedReplicas,AVAILABLE:.status.availableReplicas")

    # ── 3. HPA status ─────────────────────────────────────────────────────────
    Write-Host "`n[3] Horizontal Pod Autoscaler" -ForegroundColor Cyan
    Invoke-K @("get", "hpa", "-n", $Namespace)

    # ── 4. Service & Endpoints ────────────────────────────────────────────────
    Write-Host "`n[4] Service and Endpoints" -ForegroundColor Cyan
    Invoke-K @("get", "svc", "-n", $Namespace, "-l", "app=rdas")
    Invoke-K @("get", "endpoints", "-n", $Namespace, "-l", "app=rdas")

    # ── 5. Actuator health ────────────────────────────────────────────────────
    Write-Host "`n[5] Actuator Health (via pod exec)" -ForegroundColor Cyan
    $pod = Invoke-K @("get", "pod", "-n", $Namespace, "-l", "app=rdas",
        "--field-selector=status.phase=Running",
        "-o", "jsonpath={.items[0].metadata.name}") 2>$null

    if ($pod) {
        foreach ($path in @("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            $resp = Invoke-K @("exec", $pod, "-n", $Namespace, "--",
                "wget", "-qO-", "http://localhost:8080$path") 2>$null
            if ($resp -match '"status":"UP"') {
                Write-OK "$path -> UP"
            } elseif ($resp) {
                Write-Fail "$path -> $resp"
            } else {
                Write-Warn "$path -> UNREACHABLE"
            }
        }

        Write-Host "`n  Cache status:" -ForegroundColor Cyan
        Invoke-K @("exec", $pod, "-n", $Namespace, "--",
            "wget", "-qO-", "http://localhost:8080/api/v1/cache/status") 2>$null
    } else {
        Write-Warn "No running pod found – skipping actuator checks"
    }

    # ── 6. Recent events ──────────────────────────────────────────────────────
    Write-Host "`n[6] Recent Kubernetes Events" -ForegroundColor Cyan
    Invoke-K @("get", "events", "-n", $Namespace,
        "--sort-by=.lastTimestamp",
        "--field-selector=involvedObject.name=rdas") 2>$null |
        Select-Object -Last 10

    # ── 7. Recent logs ────────────────────────────────────────────────────────
    Write-Host "`n[7] Recent Logs (last 20 lines)" -ForegroundColor Cyan
    if ($pod) {
        Invoke-K @("logs", $pod, "-n", $Namespace, "--tail=20") 2>$null
    } else {
        Write-Warn "No running pod found"
    }

    Write-Host ""
}

if ($Watch -gt 0) {
    while ($true) {
        Run-Checks
        Write-Info "Next check in ${Watch}s... (Ctrl+C to stop)"
        Start-Sleep -Seconds $Watch
    }
} else {
    Run-Checks
}
