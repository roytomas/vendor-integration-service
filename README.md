# Vendor Integration Service

A standalone, **reactive** Spring Boot (WebFlux) service that integrates with a
third-party vendor system: it pulls vendor records through a resilient HTTP
client, maps them onto our own domain model, persists them, and exposes a
reactive REST API to read them back.

This project is self-contained: it ships a **mock vendor API** so the whole
integration flow (fetch → map → persist → serve) runs out of the box with no
external dependencies.

## Architecture

```
┌──────────────────┐   WebClient (timeout)   ┌───────────────────────┐
│  VendorApiClient  │ ───────────────────────▶│ External Vendor API   │
│  @Retry           │                         │ (or /mock-vendor/*    │
│  @CircuitBreaker   │◀─────────────────────── │  in this project)     │
│  @RateLimiter      │                         └───────────────────────┘
└─────────┬─────────┘
          │ VendorApiResponse (vendor's wire format)
          ▼
┌──────────────────┐
│   VendorMapper    │  maps vendor fields -> domain model
└─────────┬─────────┘
          ▼
┌──────────────────┐        ┌────────────────────┐
│ VendorIntegration  │ ───▶ │ VendorRepository    │ ── R2DBC ──▶ H2 (vendor table)
│ Service            │       │ (reactive upsert)   │
└─────────┬──────────┘       └────────────────────┘
          ▲
          │ triggered by
┌─────────┴──────────┐        ┌────────────────────┐
│ VendorSyncScheduler │        │  VendorController   │  GET  /api/vendors
│ (@Scheduled)        │        │                      │  POST /api/vendors/sync
└─────────────────────┘        └────────────────────┘
```

### Package layout

| Package | Responsibility |
|---|---|
| `client` | `VendorApiClient` - resilient WebClient integration with the vendor system |
| `client.dto` | Raw vendor wire-format payloads |
| `mapper` | `VendorMapper` - translates vendor payloads ↔ domain model ↔ API DTOs |
| `domain` | `Vendor` R2DBC entity and `VendorStatus` |
| `repository` | `VendorRepository` - reactive persistence gateway |
| `service` | `VendorIntegrationService` (orchestration) and `VendorSyncScheduler` (background sync) |
| `web` | `VendorController` and response/error DTOs |
| `exception` | `VendorApiException` and the centralized `GlobalExceptionHandler` |
| `mockvendor` | A stand-in vendor API (`/mock-vendor/**`) for running/testing without a real vendor |
| `config` | `VendorProperties` and the `WebClient` bean (connect/response timeouts) |

## Requirements covered

1. **Integration layer** - `VendorApiClient` encapsulates all vendor API access behind a resilient, reactive interface.
2. **Data model mapping** - `VendorMapper` explicitly translates the vendor's wire format (`VendorApiResponse`) into our domain model (`Vendor`) and our own API DTO (`VendorResponseDTO`), so vendor-specific field names/conventions never leak into the rest of the app.
3. **Persistence** - vendors are upserted (insert-or-update by `vendorId`) into an H2 database via reactive Spring Data R2DBC.
4. **Timeout / rate limit / error handling / retry** - implemented with Resilience4j:
   - **Timeout**: enforced via `.timeout(Duration)` on every call, driven by `vendor.api.response-timeout-millis`.
   - **Retry**: `@Retry` retries transient failures with exponential backoff.
   - **Circuit breaker**: `@CircuitBreaker` stops calling a consistently-failing vendor and recovers automatically.
   - **Rate limiting**: `@RateLimiter` caps the outbound call rate to the vendor.
   - **Error handling**: a central `GlobalExceptionHandler` maps vendor/resilience failures to clear HTTP statuses (502/503/504/429) instead of leaking internals.
5. **Reactive REST endpoint** - `GET /api/vendors` returns all persisted vendor details as a `Flux<VendorResponseDTO>`.
6. **Reactive Spring Boot framework** - built entirely on Spring WebFlux + Spring Data R2DBC (no blocking servlet/JPA stack anywhere in the request path).

## Running

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with an in-memory H2 database and
with `vendor.api.base-url` pointing at its own built-in mock vendor
(`/mock-vendor/vendors`), so it is runnable immediately.

A background job also runs automatically 5s after startup (and every 5
minutes thereafter) to keep the database in sync; disable it with
`vendor.sync.enabled=false` if you only want on-demand syncs.

### Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/vendors` | All vendor details currently persisted in the database (reactive stream) |
| POST | `/api/vendors/sync` | Triggers an on-demand sync from the vendor system into the database |
| GET | `/mock-vendor/vendors` | The built-in mock vendor API. Supports `?delayMs=` and `?failureRate=` to simulate a slow/unreliable vendor and exercise timeout/retry/circuit-breaker behaviour |
| GET | `/actuator/health` | Health, including circuit breaker / rate limiter state |

Example:

```bash
curl -X POST http://localhost:8080/api/vendors/sync
curl http://localhost:8080/api/vendors

# simulate a flaky vendor and watch retries/circuit breaker kick in via logs/actuator
curl "http://localhost:8080/mock-vendor/vendors?failureRate=0.7"
curl http://localhost:8080/actuator/health
```

### Pointing at a real vendor

Set the following properties (e.g. via environment variables) to target a
real vendor system instead of the built-in mock:

```yaml
vendor:
  api:
    base-url: https://api.realvendor.example.com
    api-key: ${VENDOR_API_KEY}
```

The real vendor is expected to expose a `GET /vendors` endpoint returning a
JSON array shaped like `VendorApiResponse` (`vendorCode`, `vendorName`,
`contactEmail`, `contactPhone`, `addressLine`, `vendorStatus`,
`lastUpdated`); adjust `VendorApiClient`/`VendorMapper` if the real contract
differs.

## Resilience configuration

All resilience behaviour is externalized in `application.yml` under
`resilience4j.*` and `vendor.api.*` - see that file for the full set of
tunables (retry attempts/backoff, circuit breaker thresholds, rate limiter
throughput, connect/response timeouts).

## Testing

```bash
mvn test
```

- `VendorMapperTest` - unit tests for the vendor ↔ domain mapping, including status normalization and malformed-timestamp handling.
- `VendorIntegrationServiceTest` - unit tests (Mockito) for the sync/upsert orchestration, including persistence-failure isolation.
- `VendorControllerTest` - `@WebFluxTest` slice test for the REST endpoints.
- `VendorApiClientResilienceTest` - exercises the real Resilience4j-proxied `VendorApiClient` against a WireMock stub, proving retries, timeout-triggered fallback, and circuit breaker recovery all work end-to-end (not just that the annotations are present).
