# Project Status - Equity P&L Service

**Last Updated:** June 20, 2026  
**Branch:** `main`  
**Status:** Production-ready core + corporate actions complete (Phase 0)

---

## Current State

| Area | Status |
|------|--------|
| P&L calculation engine | ✅ Verified correct (170+ tests) |
| Security (JWT, validation, exceptions) | ✅ Complete |
| Resilience (circuit breaker, retry, bulkhead) | ✅ Complete |
| Docker / containerization | ✅ JDK 21 multi-stage Dockerfile + GHCR CI build |
| **Corporate actions (Phase 0)** | ✅ **Complete** — splits, dividends, Phase 2 logic, REST API, fixtures |
| Phase 2 production data (M&A feed) | ⏸ Deferred — optional paid API or SEC EDGAR |
| CI/CD pipeline | ✅ GitHub Actions (tests + OWASP + Docker push to GHCR) |
| Observability (Phase 2) | ✅ Prometheus, JSON logging (staging/prod), correlation IDs, optional Redis cache |
| OpenAPI / Swagger | ✅ SpringDoc + `@Schema` examples; Postman collection in `postman/` |
| Staging deploy | ✅ `docker-compose.staging.yml` + manual `deploy-staging` workflow |
| Phase 1 security audit | ✅ [PHASE1_AUDIT.md](PHASE1_AUDIT.md) |
| FIFO/LIFO lot tracking | ⬜ Future |

---

## Corporate Actions (Phase 0) — Complete

Implemented with a **stateless architecture** (no DB tables; Finnhub + Caffeine cache):

- **Phase 1:** Stock splits, cash/stock dividends via Finnhub
- **Phase 2 logic:** Mergers, spinoffs, symbol changes, delistings
- **PnL integration:** Adjustments applied before unrealized P&L; dividend income in realized
- **REST API:** `/api/v1/corporate-actions/*`, `/api/v1/pnl/total-return`
- **Fixtures (dev/test):** FOX→DIS, EBAY→PYPL, FB→META, TWTR cash merger — see [corporate-actions/PROGRESS.md](corporate-actions/PROGRESS.md)

**Remaining (optional):** Wire a live secondary provider for production M&A data when needed. See [corporate-actions/PROVIDER_STRATEGY.md](corporate-actions/PROVIDER_STRATEGY.md).

---

## Test Suite

**Status:** ✅ All **257 tests** passing (June 20, 2026)

Run before merge:

```powershell
.\mvnw.cmd test
```

Key test groups:

| Suite | Purpose |
|-------|---------|
| `PnLCalculationTest` | Core P&L math (11 scenarios) |
| `PnLServiceCorporateActionsTest` | Corporate action integration |
| `CorporateActionsPnLEndToEndTest` | AAPL split, KO dividends, XYZ merger |
| `RealWorldCorporateActionsPnLEndToEndTest` | FOX, EBAY, FB, TWTR fixtures |
| `CorporateActionControllerTest` | REST endpoints |
| `TransactionControllerTest` | P&L and transaction endpoints |

---

## What's Complete

1. **Core P&L** — Long/short, transitions, average cost, realized/unrealized
2. **Bug fixes** — Controller type mismatches, timezone, validation, exceptions (all resolved; covered by `PnLServiceBugFixTest` and related tests)
3. **Corporate actions** — Full Phase 0 per spec
4. **Test infrastructure** — `@ActiveProfiles("test")`, H2 test profile, WebMvcTest security mocks, Finhub retry tests with MockWebServer
5. **Resilience fix** — Circuit breaker no longer bypasses retry fallback on first failure (`FinhubRepository`)

---

## Recommended Next Steps (portfolio demo)

See **[PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)** for the full demo script.

1. **OWASP CI** — Add repo secret `NVD_API_KEY` if the security job is slow or flaky
2. **Staging smoke** — Run `docker compose -f docker-compose.staging.yml up` once with the GHCR image
3. **Optional polish** — 2–3 min screen recording walking through tests + Swagger + corporate-action fixtures

### Deferred (not required for portfolio)
- FIFO/LIFO lot tracking
- Database-backed corporate action cache (if rate limits bite) — [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md)
- Grafana dashboards wired to `/actuator/prometheus`

---

## Quick Start

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

See [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md), [RUNNING_TESTS.md](RUNNING_TESTS.md), and the main [README.md](../README.md).

---

**Confidence:** High for splits/dividends and P&L core. Phase 2 complex events apply correctly when fixture or secondary data is present.
