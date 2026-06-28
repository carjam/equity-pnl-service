# Project Status

**Last updated:** June 27, 2026 · **Branch:** `main`

---

## Portfolio demo readiness

| Item | Status |
|------|--------|
| 307 tests (`.\mvnw.cmd test`) | ✅ |
| CI: Maven Test + Docker → GHCR | ✅ |
| CI: OWASP (dependency-check 12.2.2) | ✅ Green (June 21, 2026) |
| Staging smoke (`scripts/staging-smoke.ps1`) | ✅ Verified locally |
| OpenAPI + Postman | ✅ |
| Phase 1 security audit | ✅ [PHASE1_AUDIT.md](PHASE1_AUDIT.md) |
| GitHub About / topics | ⬜ [GITHUB_REPO_SETUP.md](GITHUB_REPO_SETUP.md) |
| Screen recording | ⬜ Optional — [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md#screen-recording-23-min) |

---

## What's shipped

| Area | Notes |
|------|--------|
| P&L engine | Long/short, AVCO, realized/unrealized with methodology disclosure — `PnLCalculationTest` |
| Corporate actions | Splits, dividends (cash/stock/ROC/qualified), mergers, spinoffs, symbol changes, delistings (incl. cash consideration) |
| Fractional shares | `BigDecimal(20,8)` throughout; Flyway V1.4 |
| Tax-lot reporting | FIFO, STCG/LTCG (IRC §1222), wash-sale detection (IRC §1091) — `GET /pnl/tax-lots` |
| HPR endpoint | Holding period return, annualized return, income breakdown — `GET /pnl/total-return` |
| Security | JWT, per-user data isolation, validation, structured errors + correlation IDs |
| Resilience | Circuit breaker + retry on Finnhub |
| Observability | Prometheus, JSON logging (staging/prod), optional Redis cache |
| Containerization | JDK 21 Dockerfile, `docker-compose.staging.yml`, GHCR CI |

**Stack:** Spring Boot **3.5.15**, Java 21, Log4j **2.25.4** (via BOM), springdoc **2.8.14**.

---

## OWASP / NVD (steady state)

- Plugin **12.2.2** · fail build on CVSS ≥ 7
- **NVD data:** NIST JSON 2.0 bulk feeds (`nvdDatafeedUrl` in `pom.xml`) — not API pagination
- **Cache key:** `owasp-nvd-Linux-dc12feed` (Actions cache)
- **Push workflow** ([`owasp.yml`](../.github/workflows/owasp.yml)): requires warm cache, scan only (~5 min)
- **Cache refresh** ([`owasp-nvd-cache.yml`](../.github/workflows/owasp-nvd-cache.yml)): manual or weekly cron (~15–45 min initial feed sync)
- **Suppressions:** one documented false positive — CVE-2026-42582 on Netty 4.1.135 (`dependency-check-suppressions.xml`)
- Secret: `NVD_API_KEY` (optional for feed-based sync; configured)

---

## Recommended next steps

1. **GitHub repo polish** — About text + topics per [GITHUB_REPO_SETUP.md](GITHUB_REPO_SETUP.md)
2. **Optional:** 2–3 min screen capture per [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)

---

## Deferred (not portfolio blockers)

See [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md): live M&A feed, LIFO, wash-sale basis carry-forward, event-driven ingestion, load tests, Grafana, prod deploy.

---

## Quick commands

```powershell
.\mvnw.cmd test
.\scripts\staging-smoke.ps1 -BuildLocal   # Docker Desktop required
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```
