# Project Status

**Last updated:** June 20, 2026 · **Branch:** `main`

---

## Portfolio demo readiness

| Item | Status |
|------|--------|
| 258 tests (`.\mvnw.cmd test`) | ✅ |
| CI: Maven Test + Docker → GHCR | ✅ |
| CI: OWASP (dependency-check 12.2.2) | 🔄 NVD cache warming (first run slow) |
| Staging smoke (`scripts/staging-smoke.ps1`) | ✅ Verified locally |
| OpenAPI + Postman | ✅ |
| Phase 1 security audit | ✅ [PHASE1_AUDIT.md](PHASE1_AUDIT.md) |
| GitHub About / topics | ⬜ [GITHUB_REPO_SETUP.md](GITHUB_REPO_SETUP.md) |
| Screen recording | ⬜ Optional — [script](PORTFOLIO_DEMO.md#screen-recording-script-23-min) |

---

## What's shipped

| Area | Notes |
|------|-------|
| P&L engine | Long/short, average cost, realized/unrealized — `PnLCalculationTest` |
| Corporate actions | Splits, dividends, merger/spinoff/symbol/delisting logic + fixtures |
| Security | JWT, validation, structured errors + correlation IDs |
| Resilience | Circuit breaker + retry on Finnhub |
| Observability | Prometheus, JSON logging (staging/prod), optional Redis cache |
| Containerization | JDK 21 Dockerfile, `docker-compose.staging.yml`, GHCR CI |

---

## OWASP / NVD (in progress)

- Plugin **12.2.2** (NIST API requires 12.1+)
- Shared cache key: `owasp-nvd-Linux-dc12`
- **First sync:** run [OWASP NVD Cache Refresh](../.github/workflows/owasp-nvd-cache.yml) (workflow_dispatch) or let CI cold-path complete — NIST often returns 503/524; retry when healthy
- **After cache exists:** OWASP job uses cached H2 DB, no NVD API (~2–5 min)

Secret: `NVD_API_KEY` (repository secret) — already configured.

---

## Recommended next steps

1. **Wait for OWASP NVD Cache Refresh or CI OWASP job** to finish green
2. **GitHub repo polish** — About text + topics per [GITHUB_REPO_SETUP.md](GITHUB_REPO_SETUP.md)
3. **Optional:** 2–3 min screen capture per [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)

---

## Deferred (not portfolio blockers)

See [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md): live M&A feed, FIFO/LIFO, load tests, Grafana, prod deploy.

---

## Quick commands

```powershell
.\mvnw.cmd test
.\scripts\staging-smoke.ps1 -BuildLocal   # Docker Desktop required
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```
