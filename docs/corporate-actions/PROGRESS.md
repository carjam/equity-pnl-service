# Corporate Actions — Status

**Phase 0 complete** (June 2026). Integrated into P&L, REST API, and test suite.

| Capability | Source | Tests |
|------------|--------|-------|
| Splits, dividends | Finnhub + Caffeine cache | `CorporateActionsPnLEndToEndTest`, `SplitAdjustmentServiceTest` |
| Mergers, spinoffs, symbol changes, delistings | Logic + fixture provider | `RealWorldCorporateActionsPnLEndToEndTest`, `MergerServiceTest`, etc. |
| REST API | `CorporateActionController` | `CorporateActionControllerTest` |
| Total return | `/api/v1/pnl/total-return` | E2E tests |

**Architecture:** Stateless — no DB tables; fetch on demand, cache 24h–7d, apply in memory during P&L.

---

## Dev/test fixtures

Enable with `corporate-actions.fixture.enabled=true` (default in dev):

| Scenario | Symbols | Event |
|----------|---------|-------|
| Stock-for-stock merger | FOX → DIS | 2019 |
| Spinoff | EBAY → PYPL | 2015 |
| Symbol change | FB → META | 2022 |
| Cash merger | TWTR | 2022 |

Implementation: `FixtureCorporateActionProvider`.

---

## Production data (deferred)

Live M&A/spinoff feeds are **not** wired. When needed, see [PROVIDER_STRATEGY.md](PROVIDER_STRATEGY.md) (paid APIs or SEC EDGAR).

---

## References

- **Spec:** [spec/phase-0-corporate-actions/01-corporate-actions-support.md](../../spec/phase-0-corporate-actions/01-corporate-actions-support.md)
- **Original plan (historical):** [../archive/PLAN-corporate-actions.md](../archive/PLAN-corporate-actions.md)
- **Session notes:** [../archive/NIGHT_SESSION_SUMMARY.md](../archive/NIGHT_SESSION_SUMMARY.md)

---

*Last updated: June 20, 2026*
