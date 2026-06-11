# RDAS – Kubernetes Deployment Guide (Part 6, Deliverable 5 & 6)

## Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| Docker | 20.x | Build and push image |
| kubectl | 1.27+ | Cluster management |
| Kubernetes cluster | 1.27+ | EKS / AKS / GKE / local (minikube, kind) |
| NGINX Ingress Controller | 1.9+ | For external access |
| Maven | 3.9 | Build JAR |
| Java | 21 | Compile source |

---

## Step 1 – Build the Application

```bash
# From project root
./mvnw package -DskipTests
```

Expected output: `target/BE-Demo-0.0.1-SNAPSHOT.jar`

---

## Step 2 – Build and Push the Docker Image

```bash
# Set your registry
export IMAGE_REGISTRY="your-registry.io/loopdfs"
export IMAGE_TAG="1.0.0"

# Build
docker build -t ${IMAGE_REGISTRY}/rdas:${IMAGE_TAG} .
docker tag ${IMAGE_REGISTRY}/rdas:${IMAGE_TAG} ${IMAGE_REGISTRY}/rdas:latest

# Push
docker push ${IMAGE_REGISTRY}/rdas:${IMAGE_TAG}
docker push ${IMAGE_REGISTRY}/rdas:latest
```

Then update `k8s/deployment.yaml` line:
```yaml
image: your-registry/rdas:latest   →   image: your-registry.io/loopdfs/rdas:1.0.0
```

---

## Step 3 – Deploy to Kubernetes

Apply all manifests in order:

```bash
# 1. Create namespace
kubectl apply -f k8s/namespace.yaml

# 2. ConfigMap (configuration)
kubectl apply -f k8s/configmap.yaml

# 3. Deployment + ServiceAccount
kubectl apply -f k8s/deployment.yaml

# 4. ClusterIP Service
kubectl apply -f k8s/service.yaml

# 5. Ingress (requires NGINX ingress controller)
kubectl apply -f k8s/ingress.yaml

# 6. Horizontal Pod Autoscaler
kubectl apply -f k8s/hpa.yaml
```

Or apply the entire directory at once:

```bash
kubectl apply -f k8s/
```

---

## Step 4 – Verify Deployment

```bash
# Watch pods come up
kubectl -n rdas get pods -w

# Expected output (after ~60 s):
# rdas-xxxxxxxxx-xxxxx   1/1   Running   0   2m
# rdas-xxxxxxxxx-yyyyy   1/1   Running   0   2m

# Check deployment status
kubectl -n rdas rollout status deployment/rdas

# Check service
kubectl -n rdas get svc rdas-svc

# Check HPA
kubectl -n rdas get hpa rdas-hpa
```

---

## Step 5 – Validate the Service

```bash
# Port-forward for local testing
kubectl -n rdas port-forward svc/rdas-svc 8080:80

# In another terminal:
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/cache/status
curl "http://localhost:8080/api/v1/countries?page=0&size=5"
curl http://localhost:8080/api/v1/countries/KE
```

---

## Environment-Specific Configuration

Override ConfigMap values per environment without changing manifests:

```bash
# Example: production with longer cache TTL
kubectl -n rdas create configmap rdas-config \
  --from-literal=RDAS_CACHE_REFRESH_INTERVAL_MS=7200000 \
  --from-literal=RDAS_SOAP_ENDPOINT=https://your-internal-soap-proxy/... \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart to pick up changes
kubectl -n rdas rollout restart deployment/rdas
```

---

## Rolling Update (Zero Downtime)

```bash
# Update image tag
kubectl -n rdas set image deployment/rdas rdas=your-registry.io/loopdfs/rdas:1.1.0

# Watch rollout
kubectl -n rdas rollout status deployment/rdas

# Rollback if needed
kubectl -n rdas rollout undo deployment/rdas
```

The deployment is configured with `maxUnavailable: 0` – old pods stay up until new ones pass readiness.

---

## Scaling

```bash
# Manual scale
kubectl -n rdas scale deployment/rdas --replicas=5

# HPA handles automatic scaling based on CPU/memory thresholds
# Min: 2 pods, Max: 10 pods (configurable in k8s/hpa.yaml)
```

---

## Resource Limits Summary

| Parameter | Request | Limit |
|-----------|---------|-------|
| CPU | 250m | 1000m |
| Memory | 512Mi | 1Gi |

Tune based on observed usage (see `kubectl top pod -n rdas`).
