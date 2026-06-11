# RDAS – API Documentation (Part 2)

Base URL: `http://localhost:8080`  
Interactive docs: `http://localhost:8080/swagger-ui.html`

All responses are wrapped in the standard envelope:

```json
{
  "success": true | false,
  "message": "OK",
  "data": <payload>,
  "timestamp": "2025-06-01T12:00:00Z"
}
```

Error responses use `ErrorResponse`:

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Country not found with ISO code: XX",
  "path": "/api/v1/countries/XX",
  "fieldErrors": null,
  "timestamp": "2025-06-01T12:00:00Z"
}
```

---

## Countries

### `GET /api/v1/countries`

Search, filter, sort, and paginate countries.

**Query parameters**

| Parameter | Type | Default | Constraints | Description |
|-----------|------|---------|-------------|-------------|
| `name` | string | — | — | Case-insensitive partial match on country name |
| `continent` | string | — | — | Continent code (AF) or name (Africa) |
| `currency` | string | — | ISO 4217 | Currency code (e.g. USD) |
| `language` | string | — | — | Language ISO code or partial name |
| `page` | integer | `0` | ≥ 0 | Zero-based page number |
| `size` | integer | `20` | 1–100 | Page size |
| `sort` | string | `name` | See valid fields | Sort field |
| `direction` | string | `ASC` | `ASC` / `DESC` | Sort direction |

**Valid `sort` values**: `name`, `isoCode`, `capitalCity`, `continentCode`, `continentName`, `currencyIsoCode`, `currencyName`, `phoneCode`

**Example request**

```
GET /api/v1/countries?continent=AF&currency=KES&page=0&size=5&sort=name&direction=ASC
```

**Example response – 200 OK**

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "content": [
      {
        "isoCode": "KE",
        "name": "Kenya",
        "capitalCity": "Nairobi",
        "phoneCode": "254",
        "continentCode": "AF",
        "continentName": "Africa",
        "currencyIsoCode": "KES",
        "currencyName": "Kenyan Shilling",
        "flagUrl": "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
        "languages": [
          { "isoCode": "SW", "name": "Swahili" },
          { "isoCode": "EN", "name": "English" }
        ]
      }
    ],
    "page": 0,
    "size": 5,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "timestamp": "2025-06-01T12:00:00Z"
}
```

---

### `GET /api/v1/countries/{isoCode}`

Returns full details for a single country.

**Path parameters**

| Parameter | Description |
|-----------|-------------|
| `isoCode` | ISO 3166-1 alpha-2 code (e.g. `KE`, `US`) |

**Responses**

| Status | Description |
|--------|-------------|
| 200 | Country found |
| 404 | Country not found |

---

### `GET /api/v1/countries/currency/{currencyCode}`

Returns all countries that use a given currency.

**Path parameters**

| Parameter | Description |
|-----------|-------------|
| `currencyCode` | ISO 4217 currency code (e.g. `USD`, `EUR`) |

**Responses**

| Status | Description |
|--------|-------------|
| 200 | List of countries (may be empty → 404) |
| 404 | No countries found for this currency |

---

## Reference Data

### `GET /api/v1/continents`

Returns all continents ordered by name.

```json
{
  "success": true,
  "message": "OK",
  "data": [
    { "code": "AF", "name": "Africa" },
    { "code": "AN", "name": "Antarctica" }
  ],
  "timestamp": "..."
}
```

---

### `GET /api/v1/currencies`

Returns all currencies ordered by name.

```json
{
  "success": true,
  "message": "OK",
  "data": [
    { "isoCode": "AED", "name": "Emirati Dirham" }
  ],
  "timestamp": "..."
}
```

---

### `GET /api/v1/languages`

Returns all languages (derived from country data) ordered by name.

```json
{
  "success": true,
  "message": "OK",
  "data": [
    { "isoCode": "EN", "name": "English" }
  ],
  "timestamp": "..."
}
```

---

## Cache Management

### `GET /api/v1/cache/status`

Returns cache metadata.

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "status": "HEALTHY",
    "lastUpdated": "2025-06-01T11:00:00Z",
    "cacheAgeMinutes": 30,
    "countries": 246,
    "continents": 7,
    "currencies": 139,
    "languages": 95
  },
  "timestamp": "..."
}
```

`status` values: `HEALTHY` | `STALE` | `EMPTY`

---

### `POST /api/v1/cache/refresh`

Forces an immediate SOAP fetch and cache reload.

**Responses**

| Status | Description |
|--------|-------------|
| 200 | Cache refreshed successfully |
| 503 | SOAP service unreachable (no cached data available) |

---

## Health & Observability

### `GET /actuator/health`

```json
{
  "status": "UP",
  "components": {
    "rdas": {
      "status": "UP",
      "details": {
        "countries": 246,
        "continents": 7,
        "currencies": 139,
        "languages": 95,
        "cacheAgeMinutes": 30,
        "lastUpdated": "2025-06-01T11:00:00Z"
      }
    }
  }
}
```

---

## HTTP Status Code Reference

| Code | Meaning |
|------|---------|
| 200 | Success |
| 400 | Bad request (validation error, invalid sort field) |
| 404 | Resource not found |
| 503 | SOAP upstream unavailable, no cache data |
| 500 | Unexpected server error |
