# RDAS – Kubernetes Troubleshooting Guide (Part 7)

## Quick Reference Commands

```bash
# All RDAS resources
kubectl -n rdas get all

# Pod logs (last 100 lines)
kubectl -n rdas logs -l app=rdas --tail=100

# Describe a crashing pod
kubectl -n rdas describe pod <pod-name>

# Live resource usage
kubectl top pod -n rdas
kubectl top node
```

---

## Common Issues and Resolutions

### 1. Pods stuck in `Pending`

**Symptoms**: `kubectl get pods -n rdas` shows `STATUS: Pending`

**Causes and fixes**:

```bash
kubectl -n rdas describe pod <pod-name>
# Look at "Events:" section
```

| Event message | Cause | Fix |
|--------------|-------|-----|
| `Insufficient cpu` | Node has no capacity | Add nodes or reduce `resources.requests.cpu` |
| `Insufficient memory` | Node OOM | Add nodes or reduce `resources.requests.memory` |
| `Unschedulable: 0/N nodes available` | `topologySpreadConstraints` violation | Ensure pods can spread across nodes; check `kubectl get nodes` |
| `ImagePullBackOff` | Image not found or registry auth | See section 2 |

---

### 2. `ImagePullBackOff` / `ErrImagePull`

```bash
kubectl -n rdas describe pod <pod-name> | grep -A 10 "Events:"
```

**Fix**: Ensure credentials are configured for your registry.

```bash
# Create registry secret
kubectl -n rdas create secret docker-registry regcred \
  --docker-server=your-registry.io \
  --docker-username=<username> \
  --docker-password=<password>

# Reference in deployment.yaml
# spec.template.spec.imagePullSecrets:
# - name: regcred
```

---

### 3. Pod `CrashLoopBackOff`

```bash
# Read pod logs
kubectl -n rdas logs <pod-name> --previous

# Common root causes:
# - SOAP service unreachable at startup → application still starts (exception is caught)
# - JVM OOM → increase memory limit
# - Invalid config → fix ConfigMap values
```

The RDAS application is designed **not to crash** when the SOAP service is down at startup. If it does crash, suspect a configuration or OOM issue.

---

### 4. Service returns `503 Service Unavailable`

**Check cache status**:

```bash
kubectl -n rdas port-forward svc/rdas-svc 8080:80 &
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/cache/status
```

**If `status: EMPTY`** → Cache was never loaded (SOAP unreachable since startup):

```bash
# Check SOAP connectivity from within a pod
kubectl -n rdas exec -it <pod-name> -- \
  wget -O- "http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL"

# If connectivity is restored, force a refresh:
curl -X POST http://localhost:8080/api/v1/cache/refresh
```

**If `status: STALE`** → SOAP has been down > 2 h but stale data is being served:
```bash
# Trigger manual refresh once SOAP is back
curl -X POST http://localhost:8080/api/v1/cache/refresh
```

---

### 5. Pods not receiving traffic (readiness probe failing)

```bash
kubectl -n rdas describe pod <pod-name> | grep -A 5 "Readiness"
kubectl -n rdas get endpoints rdas-svc
```

If `Endpoints: <none>` → no pod has passed the readiness probe yet.

```bash
# Check readiness probe directly
kubectl -n rdas exec -it <pod-name> -- \
  wget -qO- http://localhost:8080/actuator/health/readiness
```

SOAP warm-up can take up to 30–60 s. `startupProbe.failureThreshold=12` allows up to 2 min. Increase if the SOAP service is very slow.

---

### 6. High memory usage / OOM Killed

```bash
# Check current usage
kubectl top pod -n rdas

# View OOM events
kubectl -n rdas describe pod <pod-name> | grep -i "oom\|killed\|exit"

# Check JVM heap
kubectl -n rdas exec -it <pod-name> -- \
  java -XX:+PrintFlagsFinal -version 2>&1 | grep -i MaxHeapSize
```

**Fix**: Increase memory limit in `deployment.yaml` or reduce `maximumSize` in Caffeine cache config.

---

### 7. HPA not scaling

```bash
kubectl -n rdas describe hpa rdas-hpa
```

**`<unknown>` for metrics** → `metrics-server` is not installed:

```bash
# Install metrics-server
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

---

### 8. Ingress returning 404

```bash
# Verify ingress
kubectl -n rdas describe ingress rdas-ingress

# Verify NGINX ingress controller is running
kubectl -n ingress-nginx get pods

# Check service selectors match pod labels
kubectl -n rdas get pods --show-labels
kubectl -n rdas get svc rdas-svc -o yaml | grep selector -A 5
```

---

## Log Analysis

```bash
# All pod logs streamed
kubectl -n rdas logs -l app=rdas -f --max-log-requests=10

# Filter for errors
kubectl -n rdas logs -l app=rdas --tail=500 | grep -i "error\|exception\|fatal"

# Filter for cache events
kubectl -n rdas logs -l app=rdas | grep -i "cache\|soap\|refresh"

# Filter for specific request
kubectl -n rdas logs -l app=rdas | grep "GET /api/v1/countries"
```

---

## Health Check Summary

| Endpoint | Expected value when healthy |
|----------|----------------------------|
| `/actuator/health` | `{ "status": "UP" }` |
| `/actuator/health/liveness` | `{ "status": "UP" }` |
| `/actuator/health/readiness` | `{ "status": "UP" }` |
| `/api/v1/cache/status` | `{ "status": "HEALTHY" }` |

---

## Useful One-Liners

```bash
# Restart deployment (picks up new ConfigMap)
kubectl -n rdas rollout restart deployment/rdas

# Force cache refresh across all pods (run per pod)
for pod in $(kubectl -n rdas get pods -o name); do
  kubectl -n rdas exec $pod -- \
    wget -qO- --method=POST http://localhost:8080/api/v1/cache/refresh
done

# Delete and recreate all resources (last resort)
kubectl delete -f k8s/ --ignore-not-found
kubectl apply -f k8s/

# Copy a thread dump for analysis
kubectl -n rdas exec <pod-name> -- kill -3 1 ; kubectl -n rdas logs <pod-name> | tail -200
```
