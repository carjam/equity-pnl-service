# Portfolio Demo Guide

**Audience:** Recruiters, interviewers, and reviewers evaluating this repo  
**Last updated:** June 20, 2026  
**Branch:** `main`

This service is a **demonstration** of enterprise-grade financial API engineering — not a live trading product. See [NOTICE.md](NOTICE.md) for licensing.

---

## Elevator pitch (30 seconds)

Spring Boot 3 / Java 21 REST API that calculates equity P&L with **real market data**, **JWT security**, **Resilience4j**, and **corporate actions** (splits, dividends, mergers, spinoffs). **257 automated tests**, CI with OWASP scanning, Docker images on GHCR, OpenAPI docs, and structured observability.

---

## What to highlight

| Theme | Where to point |
|-------|----------------|
| **Correct P&L math** | `PnLCalculationTest`, `PnLServiceTest` — long/short, transitions, average cost |
| **Corporate actions** | Fixtures: FOX→DIS, EBAY→PYPL, FB→META, TWTR — [corporate-actions/PROGRESS.md](corporate-actions/PROGRESS.md) |
| **Security** | JWT, validation, structured errors with correlation IDs — [PHASE1_AUDIT.md](PHASE1_AUDIT.md) |
| **Resilience** | Circuit breaker + retry on Finnhub (`FinhubRepository`) |
| **Observability** | Prometheus, JSON logging (staging/prod), `X-Correlation-Id` |
| **CI/CD** | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) — test, OWASP, Docker → GHCR |
| **API docs** | SpringDoc `/v3/api-docs`, Swagger UI in dev, [Postman collection](../postman/equity-pnl-service.postman_collection.json) |

---

## Demo path A — Tests only (no API keys, ~2 min)

Best when you can't run MySQL or Finnhub locally.

```powershell
cd equity-pnl-service
.\mvnw.cmd test
```

**Talking points while it runs:**
- 257 tests including end-to-end corporate-action scenarios
- H2 in-memory DB, fixture provider for M&A without live APIs
- Integration test proves OpenAPI spec is served at `/v3/api-docs`

**Optional deep dive:** open `RealWorldCorporateActionsPnLEndToEndTest` or `CorporateActionsPnLEndToEndTest`.

---

## Demo path B — Local API (dev profile, ~5 min)

**Prerequisites:** JDK 21, MySQL (or use existing dev DB from `.env`), `FINHUB_KEY`, `JWT_SECRET`.

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

1. **Swagger UI:** http://localhost:8080/swagger-ui.html  
2. **Login:** `POST /api/v1/auth/login` (use a user from your DB or seed data)  
3. **Authorize** in Swagger with `Bearer <token>`  
4. **P&L:** `GET /api/v1/pnl?from=2024-01-01&to=2024-12-31`  
5. **Corporate actions (fixtures on in dev):** `GET /api/v1/corporate-actions/mergers?symbol=FOX&from=2019-01-01&to=2019-12-31`  
6. **Health:** `GET /actuator/health` — note `X-Correlation-Id` response header

**Alternative:** Import `postman/equity-pnl-service.postman_collection.json` and set `baseUrl`.

---

## Demo path C — Docker staging stack (~10 min)

Proves containerization and observability. **Start Docker Desktop first.**

**Automated smoke (recommended):**

```powershell
.\scripts\staging-smoke.ps1 -BuildLocal
```

Builds `equity-pnl-service:local`, starts MySQL + Redis + app, checks health, OpenAPI, Prometheus, login (`carjam` / `password` from Flyway seed), and P&L.

**Manual / GHCR image:**

```powershell
$env:APP_IMAGE = "ghcr.io/carjam/equity-pnl-service:latest"
$env:JWT_SECRET = "<256-bit-secret>"
$env:FINHUB_KEY = "placeholder"
$env:DATABASE_PASSWORD = "changeme"
docker compose -f docker-compose.staging.yml up -d
```

> Staging uses database **`equity`** (matches Flyway migrations), not `equity_pnl`.

| URL | Purpose |
|-----|---------|
| http://localhost:8080/actuator/health | Liveness |
| http://localhost:8080/actuator/prometheus | Metrics (staging profile) |
| http://localhost:8080/swagger-ui.html | API explorer |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |

Teardown: `docker compose -f docker-compose.staging.yml down -v`

---

## CI / GitHub Actions

Every push to `main` runs:

| Job | Purpose |
|-----|---------|
| **Maven Test** | Full suite on JDK 21 |
| **OWASP Dependency Check** | CVE scan (CVSS ≥ 7 fails build) |
| **Docker Build** | Push to `ghcr.io/<repo>:latest` and `:sha` |

**Repo secret (recommended):** `NVD_API_KEY`

1. Request a free key: https://nvd.nist.gov/developers/request-an-api-key  
2. GitHub → **Settings → Secrets and variables → Actions → New repository secret**  
3. Name: `NVD_API_KEY` · Value: paste key (no quotes)  
4. Re-run the **OWASP Dependency Check** job

Without the key, the first run can take 30+ minutes; cached runs are faster.

**Optional remote staging:** Set `STAGING_HOST`, `STAGING_USER`, `STAGING_SSH_KEY` and run the **Deploy Staging** workflow manually.

---

## Screen recording script (~2–3 min)

Use for LinkedIn, resume link, or interview prep. Record **Path A** (tests) + **Path B or C** (API).

| Time | Show | Say (approx.) |
|------|------|----------------|
| 0:00 | GitHub repo README + Actions tab (green test/Docker jobs) | "Spring Boot P&L API — 257 tests, CI on every push." |
| 0:25 | Terminal: `.\mvnw.cmd test` finishing BUILD SUCCESS | "Full suite including corporate-action scenarios — FOX merger, AAPL split." |
| 0:50 | Open `PnLCalculationTest` or `RealWorldCorporateActionsPnLEndToEndTest` | "P&L math and M&A fixtures are tested, not just mocked." |
| 1:10 | Swagger UI or staging health + login → P&L | "JWT-secured REST API, OpenAPI docs, Prometheus metrics." |
| 1:45 | `docs/PHASE1_AUDIT.md` or Resilience4j in `FinhubRepository` | "Security audit, circuit breaker, structured errors with correlation IDs." |
| 2:15 | `docker-compose.staging.yml` or smoke script output | "Multi-stage Docker, staging compose with MySQL and Redis." |
| 2:30 | End card: repo URL + "Portfolio demo — code review only" | |

**Tips:** 1080p terminal font 14pt+, hide unrelated windows, no secrets on screen.

---

## Demo checklist (portfolio-ready)

- [x] Phase 0 corporate actions + fixtures
- [x] 257 tests green locally
- [x] CI: tests + OWASP + Docker on `main`
- [ ] OWASP job green (add `NVD_API_KEY` if slow/flaky)
- [ ] Staging smoke: `.\scripts\staging-smoke.ps1 -BuildLocal` (Docker Desktop running)
- [x] OpenAPI + Postman
- [x] Phase 1 audit documented
- [ ] Record a 2–3 min screen capture (optional polish)

---

## What we intentionally defer

Not needed for a portfolio demo — document as future work if asked:

- Live paid M&A data provider ([PROVIDER_STRATEGY.md](corporate-actions/PROVIDER_STRATEGY.md))
- Load testing / p95 SLAs
- FIFO/LIFO lot tracking
- Production deploy + Grafana dashboards

See [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md) and [PROJECT_STATUS.md](PROJECT_STATUS.md).

---

## Doc map

| Document | Use when |
|----------|----------|
| [PROJECT_STATUS.md](PROJECT_STATUS.md) | Current status snapshot |
| [PHASE1_AUDIT.md](PHASE1_AUDIT.md) | Security sign-off detail |
| [spec/CHECKLIST.md](../spec/CHECKLIST.md) | Full production-readiness matrix |
| [RUNNING_TESTS.md](RUNNING_TESTS.md) | Test commands and CI notes |
