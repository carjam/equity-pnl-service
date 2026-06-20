# Portfolio Demo Guide

**Audience:** Recruiters and reviewers · **Branch:** `main` · **License:** [NOTICE.md](NOTICE.md)

---

## Elevator pitch (30 seconds)

Spring Boot 3 / Java 21 REST API for equity P&L with **JWT security**, **Resilience4j**, **corporate actions**, and **258 automated tests**. CI runs tests, OWASP CVE scan, and Docker publish to GHCR. OpenAPI, Prometheus, Docker staging stack.

---

## What to highlight

| Theme | Where |
|-------|--------|
| P&L math | `PnLCalculationTest`, `PnLServiceTest` |
| Corporate actions | `RealWorldCorporateActionsPnLEndToEndTest` — FOX, EBAY, FB, TWTR fixtures |
| Security | [PHASE1_AUDIT.md](PHASE1_AUDIT.md) |
| Resilience | `FinhubRepository` + Resilience4j |
| Observability | `/actuator/prometheus`, JSON logs, `X-Correlation-Id` |
| CI/CD | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) |

---

## Path A — Tests only (~2 min)

```powershell
.\mvnw.cmd test
```

258 tests, H2 in-memory, fixture provider for M&A without live APIs.

---

## Path B — Local API (~5 min)

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger: http://localhost:8080/swagger-ui.html — login, Authorize with Bearer token, call P&L and corporate-actions endpoints.

Postman: `postman/equity-pnl-service.postman_collection.json`

---

## Path C — Docker staging (~10 min)

```powershell
.\scripts\staging-smoke.ps1 -BuildLocal
```

Starts MySQL + Redis + app; checks health, OpenAPI, Prometheus, login (`carjam` / `password`), P&L routing.

Manual: `docker compose -f docker-compose.staging.yml up -d` (DB name **`equity`**).

---

## CI workflows

| Workflow | Purpose |
|----------|---------|
| **CI** | Maven Test + Docker → GHCR on `main` |
| **OWASP Dependency Check** | CVE scan — separate workflow, not canceled by pushes |
| **OWASP NVD Cache Refresh** | Optional manual/weekly cache warm |

### OWASP / NVD

- **Plugin:** dependency-check **12.2.2** (requires 12.1+ for NIST API)
- **Secret:** `NVD_API_KEY` in repo Settings → Secrets
- **First run:** slow (60–90+ min) — runs in a **separate workflow** so new pushes do not cancel NVD download
- **Warm cache:** Actions → **OWASP NVD Cache Refresh** or let **OWASP Dependency Check** finish once
- **Steady state:** CI restores cache and skips NVD API (`autoUpdate=false`) — ~2–5 min

---

## Screen recording (~2–3 min)

| Time | Show | Say |
|------|------|-----|
| 0:00 | README + green CI (Test, Docker) | Spring Boot P&L API, full test suite on every push |
| 0:25 | `.\mvnw.cmd test` SUCCESS | Corporate-action scenarios included |
| 0:50 | `RealWorldCorporateActionsPnLEndToEndTest` | Tested fixtures, not just mocks |
| 1:10 | Swagger or staging smoke output | JWT, OpenAPI, Prometheus |
| 1:45 | `PHASE1_AUDIT.md` or Resilience4j | Security + circuit breaker |
| 2:15 | `docker-compose.staging.yml` | Containerized staging stack |

---

## Checklist

- [x] 258 tests green
- [x] Staging smoke script verified
- [x] CI Test + Docker
- [x] OpenAPI + Postman
- [x] Phase 1 audit doc
- [x] `NVD_API_KEY` secret
- [ ] OWASP job green (NVD cache warming — in progress)
- [ ] GitHub About + topics ([GITHUB_REPO_SETUP.md](GITHUB_REPO_SETUP.md))
- [ ] Screen recording (optional)

---

## Deferred if asked

Live M&A API, load tests, FIFO/LIFO, prod deploy — [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md).
