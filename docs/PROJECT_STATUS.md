# Project Status - Equity P&L Service

**Last Updated:** June 20, 2026  
**Branch:** `feature/bug-fixes-and-retry-strategy`  
**Status:** Production-ready core + corporate actions complete (Phase 0)

---

## Current State

| Area | Status |
|------|--------|
| P&L calculation engine | ✅ Verified correct (170+ tests) |
| Security (JWT, validation, exceptions) | ✅ Complete |
| Resilience (circuit breaker, retry, bulkhead) | ✅ Complete |
| Docker / containerization | ✅ Complete |
| **Corporate actions (Phase 0)** | ✅ **Complete** — splits, dividends, Phase 2 logic, REST API, fixtures |
| Phase 2 production data (M&A feed) | ⏸ Deferred — optional paid API or SEC EDGAR |
| CI/CD pipeline | ⬜ Not started |
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

**Status:** ✅ All **255 tests** passing (June 20, 2026)

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
2. **Bug fixes** — Controller type mismatches, timezone, validation, exceptions (see [BUG_REPORT.md](BUG_REPORT.md))
3. **Corporate actions** — Full Phase 0 per spec
4. **Test infrastructure** — `@ActiveProfiles("test")`, H2 test profile, WebMvcTest security mocks, Finhub retry tests with MockWebServer
5. **Resilience fix** — Circuit breaker no longer bypasses retry fallback on first failure (`FinhubRepository`)

---

## Recommended Next Steps

### Before merge
1. ~~Run `.\mvnw.cmd test` — all tests green~~ ✅ Done (255/255)
2. Open PR from `feature/bug-fixes-and-retry-strategy` → `main`

### After merge
1. **CI/CD** — GitHub Actions (`mvn test` on PR)
2. **Documentation sync** — Keep [spec/CHECKLIST.md](../spec/CHECKLIST.md) aligned with shipped work
3. **Production Phase 2 data** — Only when a user hits wrong P&L on M&A, or paid tier requires it

### Future enhancements
- FIFO/LIFO lot tracking
- Database-backed corporate action cache (if rate limits bite) — [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md)
- Monitoring (Prometheus/Grafana)

---

## Quick Start

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

See [RUNNING_TESTS.md](RUNNING_TESTS.md) and the main [README.md](../README.md).

---

**Confidence:** High for splits/dividends and P&L core. Phase 2 complex events apply correctly when fixture or secondary data is present.
