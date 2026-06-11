# Reference Data Aggregation Service (RDAS)

**LOOP DFS – Digital Business Team**

Centralised REST/JSON API for country, currency, language, and geographical reference data.  
Internally consumes the CountryInfo SOAP service (webservices.oorsprong.org) and caches everything in-memory.

---

## Quick Start

### Prerequisites
- Java 21
- Maven 3.9+

```bash
# Clone and run
./mvnw spring-boot:run
```

The service starts on **port 8080**. Swagger UI is available at:  
`http://localhost:8080/swagger-ui.html`

---

## API Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/countries` | Search / filter / sort / paginate countries |
| GET | `/api/v1/countries/{isoCode}` | Get single country by ISO code |
| GET | `/api/v1/countries/currency/{code}` | Countries sharing a currency |
| GET | `/api/v1/continents` | List all continents |
| GET | `/api/v1/currencies` | List all currencies |
| GET | `/api/v1/languages` | List all languages |
| GET | `/api/v1/cache/status` | Cache health and metadata |
| POST | `/api/v1/cache/refresh` | Force cache refresh |
| GET | `/actuator/health` | Spring Boot health endpoint |

### Example: Paginated country search

```
GET /api/v1/countries?name=nig&continent=AF&page=0&size=10&sort=name&direction=ASC
```

### Response envelope

```json
{
  "success": true,
  "message": "OK",
  "data": { ... },
  "timestamp": "2025-01-01T12:00:00Z"
}
```

---

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.4.3 |
| Spring Cache + Caffeine | 3.1.x |
| SpringDoc OpenAPI | 2.8.3 |
| Maven | 3.9 |

---

## Configuration

All properties can be overridden via environment variables (Spring convention: replace `.` and `-` with `_`, upper-case).

| Property | Default | Description |
|----------|---------|-------------|
| `rdas.soap.endpoint` | oorsprong.org URL | SOAP service endpoint |
| `rdas.soap.connect-timeout-ms` | `5000` | SOAP connection timeout |
| `rdas.soap.read-timeout-ms` | `30000` | SOAP read timeout |
| `rdas.cache.refresh-interval-ms` | `3600000` | Cache refresh interval (1 h) |
| `server.port` | `8080` | HTTP port |

---

## Building

```bash
# Build JAR
./mvnw package -DskipTests

# Run tests
./mvnw test

# Build Docker image
docker build -t rdas:latest .
```

---

## Deployment

See [docs/KUBERNETES_DEPLOYMENT_GUIDE.md](docs/KUBERNETES_DEPLOYMENT_GUIDE.md) for full Kubernetes deployment instructions.  
See [docs/KUBERNETES_TROUBLESHOOTING_GUIDE.md](docs/KUBERNETES_TROUBLESHOOTING_GUIDE.md) for operations and debugging.

---

## Architecture & Design

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for system design, caching strategy, resilience model, and engineering discussion.

See [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md) for full API specification.

---

## Engineering Discussion (Part 6)

---

### Q1 – SOAP reduced to 10 requests per minute

#### Current baseline
The current design makes exactly **3 SOAP calls at startup** and **3 per hourly refresh cycle** = 72 calls/day. A 100 req/min quota = 144,000/day; we use < 0.05 % of it. Dropping to 10 req/min = 14,400/day; 72 calls/day is still only 0.5 % — the design already fits. However, the approach must become much more deliberate to stay safe under unexpected conditions (pod restarts, rolling deployments, burst refresh attempts).

#### Changes required

**1. Extend cache TTL to 6 hours and use a refresh-ahead strategy**

Instead of refreshing on a fixed 1-hour schedule, switch to **refresh-ahead**: trigger a background refresh when the cache age reaches 80 % of the TTL (i.e., at 4h 48m into a 6-hour window). This ensures data is always warm before expiry without over-fetching.

```
6h TTL → 4 refreshes/day → 12 SOAP calls/day (3 per refresh)
Safety headroom: 14,400 - 12 = 14,388 calls/day remaining
```

**2. Distributed refresh lock via Redis (SETNX)**

When running multiple pods, without coordination every pod independently refreshes — multiplying SOAP calls by the number of replicas. With a Redis distributed lock:

```
Only 1 pod holds the lock → 12 SOAP calls/day regardless of replica count
Other pods read from the shared Redis cache immediately
```

This follows the core principle of stateless services: *"servers should be stateless — sessions/shared state in a centralized store"*.

**3. Adopt AP (Availability over Consistency) from the CAP theorem**

RDAS reference data changes at most once a day in practice. This makes it a textbook **AP system** — we deliberately choose **eventual consistency** (stale data up to 6 hours old is acceptable) over strong consistency (forcing a fresh SOAP call on every request). The trade-off: consumers may see yesterday's flag URL; the benefit: zero dependency on SOAP for live traffic.

**4. Exponential back-off on SOAP failures**

If the 10 req/min limit is hit or the SOAP service errors, retrying immediately burns remaining quota. Use **exponential back-off with jitter**:

```
Attempt 1: wait 1 min
Attempt 2: wait 2 min
Attempt 3: wait 4 min  ← jitter ±30 s to prevent thundering herd
Attempt 4: wait 8 min
```

This prevents a cascade of failed retries consuming the entire 10 req/min quota in seconds.

**5. ETag / conditional SOAP requests**

If the SOAP provider supports `If-None-Match` or `Last-Modified` headers, send them on each refresh. A `304 Not Modified` response does not count as a data transfer and may not count against rate limits, effectively making many refreshes free.

**6. Admin-only force-refresh with rate-gate**

The `POST /api/v1/cache/refresh` endpoint must be protected behind an admin role and rate-limited to a maximum of 3 calls per hour to prevent accidental quota exhaustion.

---

### Q2 – Scaling to 20 million requests per day

#### Back-of-envelope calculation

```
20,000,000 requests / 86,400 seconds = ~231 req/s average
Peak (3× average)                    = ~700 req/s
Peak (5× average, flash traffic)     = ~1,150 req/s
```

Using latency numbers every programmer should know:
- In-process Caffeine cache read: **~100 ns** (main memory reference)
- Redis cache read (same DC):     **~500 µs** (0.5 ms round-trip)
- SOAP call to upstream:          **~500 ms** (network + processing)

Conclusion: serving from Caffeine is **5,000× faster** than Redis and **5,000,000× faster** than SOAP. The in-process cache is the correct primary serving layer for this read-heavy, rarely-changing dataset.

#### Scaling architecture

| Layer | Component | Purpose |
|-------|-----------|---------|
| **L1 – Pull CDN** | Cloudflare / Akamai | `Cache-Control: public, max-age=3600` on all `GET` endpoints. Pull CDNs work best for heavy-traffic sites — content is cached on first request and spread evenly. Absorbs **80–90 %** of all requests without reaching RDAS. |
| **L2 – DNS load balancing** | Route 53 latency-based routing | Routes consumers to the nearest regional deployment. Reduces cross-region latency by 100–150 ms per request. |
| **L3 – Layer 7 load balancer** | NGINX / AWS ALB | Content-aware routing; SSL termination; `gzip` compression (country list: ~80 KB → ~12 KB). Single reverse proxy in front of N pods. |
| **L4 – Horizontal scaling** | Kubernetes HPA (2–20 pods) | Stateless pods — no session state, no affinity required. HPA scales on CPU ≥ 70 %. At 1,150 req/s and ~1 ms avg latency per pod: `ceil(1150 × 0.001 / 0.7) ≈ 2 pods` needed — HPA min of 2 already covers it; scale to 20 for peak safety. |
| **L5 – In-process cache** | Caffeine (per pod) | Serves 100 % of read traffic at ~100 ns. Country list fits in < 10 MB heap. Zero network hops. |
| **L6 – Distributed cache** | Redis Cluster | Shared cross-pod state; distributed refresh lock; fallback if Caffeine entry expired. |
| **L7 – Back pressure** | Spring `@RateLimiter` / NGINX `limit_req` | When downstream is overloaded, return `HTTP 503` with `Retry-After` header. Clients use exponential back-off. Prevents queue runaway — *"once the queue fills up, clients get a server busy or HTTP 503 status code to try again later"*. |

#### Availability calculation

With 2 pods in parallel, availability improves dramatically:

```
Single pod availability:  99.9%  (three 9s → 8h 45m downtime/year)
Two pods in parallel:     1 - (1 - 0.999)² = 99.9999%  (six 9s → 32s downtime/year)
```

This is the **availability-in-parallel** principle.

#### Multi-region active-active

Deploy to 2+ regions (e.g., AWS eu-west-1 + af-south-1) behind a global load balancer. Each region maintains its own cache populated independently. Reads are served locally; there is no cross-region dependency because RDAS data is read-only and eventually consistent.

---

### Q3 – Additional improvements with one more week

The improvements are prioritised by impact-to-effort ratio.

| # | Improvement | System Design Concept Applied | Impact |
|---|------------|-------------------------------|--------|
| 1 | **Redis distributed cache + refresh lock** | Application caching at object level; centralized stateless store; consistent hashing for Redis cluster nodes | Eliminates per-pod SOAP warm-up; enables true horizontal scaling |
| 2 | **Resilience4j circuit breaker + exponential back-off** | Availability patterns – fail-over; back pressure | Fail fast after N SOAP errors; half-open for controlled recovery; protects rate quota |
| 3 | **Pull CDN (Cloudflare)** | CDN caching; pull CDNs for heavy traffic | Removes 80–90 % of origin requests; free global edge network |
| 4 | **JWT / OAuth2 security on write endpoints** | Security – principle of least privilege; OWASP API Security Checklist | Prevents unauthorized `POST /cache/refresh` calls that waste SOAP quota |
| 5 | **PostgreSQL snapshot persistence** | Availability – active-passive fail-over; data durability | Survives full pod restarts while SOAP is down; cold-start resilience |
| 6 | **Kafka event-driven cache invalidation** | Asynchronism – message queues; task queues | Any authorised system publishes a `cache.invalidate` event; RDAS subscribes and refreshes once, without polling |
| 7 | **Prometheus + Grafana + alerting** | Observability | Cache age gauge, SOAP error rate counter, p50/p99 request latency histograms, 503 rate alerts |
| 8 | **Distributed tracing (OpenTelemetry + Zipkin)** | Real-world architecture (Dapper) | Trace a single request across load balancer → pod → SOAP call; identify latency bottlenecks |
| 9 | **Response compression (gzip/Brotli)** | Communication – HTTP; performance vs scalability | Country list ~80 KB → ~12 KB; 85 % bandwidth reduction; critical for mobile/partner consumers |
| 10 | **Elasticsearch full-text search** | NoSQL – document store; flexible schema | Typo-tolerant country search; fuzzy matching on name, capital, language; partial-word autocomplete |
| 11 | **API rate limiting at gateway** | Back pressure; security | Per-consumer token-bucket limiting prevents one channel (e.g., a runaway partner API) from starving others |
| 12 | **CI/CD pipeline (GitHub Actions)** | Operational maturity | Build → test → Docker build → push → `kubectl rollout` on merge to `main`; enforces quality gate before every deployment |
| 13 | **API versioning (`/api/v2`)** | Availability – non-breaking evolution | Allows breaking schema changes without impacting existing consumers; path-prefix strategy (not header-based) for CDN cacheability |
| 14 | **Audit log** | Security – accountability | Record consumer identity, endpoint, timestamp, filters; satisfies the auditability requirement from the original brief |
