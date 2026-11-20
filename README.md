# Vendor Integration Service

[![CI](https://github.com/roytomas/vendor-integration-service/actions/workflows/ci.yml/badge.svg)](https://github.com/roytomas/vendor-integration-service/actions/workflows/ci.yml)
[![CodeQL](https://github.com/roytomas/vendor-integration-service/actions/workflows/codeql.yml/badge.svg)](https://github.com/roytomas/vendor-integration-service/actions/workflows/codeql.yml)

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

## Observability (Datadog)

The service ships with [dd-trace-java](https://github.com/DataDog/dd-trace-java)
and Micrometer/DogStatsD wired in, all configured through environment
variables so the image works standalone (no Datadog Agent reachable) and
"just works" once one is:

- **APM distributed tracing** - `dd-java-agent.jar` is baked into the Docker
  image and always attached (`-javaagent:/app/dd-java-agent.jar`). It
  auto-instruments WebFlux, WebClient, R2DBC and Kafka. If no agent is
  reachable at `DD_AGENT_HOST`, trace export is simply a no-op.
- **Metrics** - app/JVM metrics (HTTP, R2DBC pool, Kafka consumer,
  Resilience4j circuit breakers/retries/rate limiters) are pushed to the
  Datadog Agent's DogStatsD listener via Micrometer
  (`management.statsd.metrics.export.*` in `application.yml`, flavor
  `datadog`). Disabled by default (`DD_METRICS_ENABLED=false`) to keep the
  service dependency-free out of the box; set `DD_METRICS_ENABLED=true` once
  an agent is reachable.
- **Log-trace correlation** - `DD_LOGS_INJECTION=true` (the Docker image
  default) makes the tracer populate `dd.trace_id`/`dd.span_id` via MDC;
  `logging.pattern.level` in `application.yml` renders them into every log
  line so logs and traces correlate in the Datadog UI.
- **Local testing** - `docker-compose.yml` runs the service alongside a real
  Datadog Agent container:

  ```bash
  DD_API_KEY=<your-api-key> docker compose up --build
  ```

### Key environment variables

| Variable | Default | Purpose |
|---|---|---|
| `DD_AGENT_HOST` | `localhost` | Host/service name of the Datadog Agent (APM + DogStatsD) |
| `DD_TRACE_AGENT_PORT` | `8126` | Agent APM trace intake port |
| `DD_DOGSTATSD_PORT` | `8125` | Agent DogStatsD port |
| `DD_METRICS_ENABLED` | `false` | Enables the Micrometer → DogStatsD metrics export |
| `DD_SERVICE` / `DD_ENV` / `DD_VERSION` | `vendor-integration-service` / `dev` / `0.0.1-SNAPSHOT` | Unified service tagging across traces, metrics and logs |
| `DD_LOGS_INJECTION` | `true` | Injects `dd.trace_id`/`dd.span_id` into logs for correlation |
| `DD_RUNTIME_METRICS_ENABLED` | `true` | JVM runtime metrics (GC, heap, threads) via the tracer |

### Kubernetes DaemonSet deployments

This repo has no Kubernetes manifests yet, but when deploying behind a
DaemonSet-mode Datadog Agent (one per node), point `DD_AGENT_HOST` at the
node's IP via the downward API rather than a fixed hostname:

```yaml
env:
  - name: DD_AGENT_HOST
    valueFrom:
      fieldRef:
        fieldPath: status.hostIP
```

## Testing

```bash
mvn test
```

- `VendorMapperTest` - unit tests for the vendor ↔ domain mapping, including status normalization and malformed-timestamp handling.
- `VendorIntegrationServiceTest` - unit tests (Mockito) for the sync/upsert orchestration, including persistence-failure isolation.
- `VendorControllerTest` - `@WebFluxTest` slice test for the REST endpoints.
- `VendorApiClientResilienceTest` - exercises the real Resilience4j-proxied `VendorApiClient` against a WireMock stub, proving retries, timeout-triggered fallback, and circuit breaker recovery all work end-to-end (not just that the annotations are present).

## CI/CD

Two GitHub Actions workflows run on every push/PR to `main` (`.github/workflows/`):

- **`ci.yml`**
  1. Builds and runs the full test suite on JDK 21 (`mvn verify`), including JaCoCo coverage (`target/site/jacoco`).
  2. Publishes test results inline on the PR and uploads the coverage report as a build artifact.
  3. Runs Checkstyle (`google_checks.xml`) and SpotBugs as non-blocking checks - findings are uploaded as artifacts (`target/checkstyle-result.xml`, `target/spotbugsXml.xml`) without failing the build, since the codebase doesn't have a clean baseline yet. Tighten (`failOnViolation`/`failOnError`) once it does.
  4. Packages the Spring Boot JAR and uploads it as a build artifact.
  5. On pushes to `main` only, builds the `Dockerfile` image and pushes it to `ghcr.io/roytomas/vendor-integration-service` tagged `latest` and with the commit SHA.
- **`codeql.yml`** - weekly + on push/PR, static security analysis (CodeQL) for Java, with results in the repo's Security tab.

**`dependabot.yml`** opens weekly PRs for outdated Maven dependencies (Spring Boot patches grouped together) and GitHub Actions versions.

Branch protection on `main` requires the `build-and-test` check to pass and a pull request before merging.

No cloud deployment step exists yet. The pipeline publishes a container image to GHCR as the hand-off point for a future CD workflow (e.g. deploying that image to Azure Container Apps).
