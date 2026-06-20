# Corporate Actions — Data Provider Strategy

When to add live M&A/spinoff data. **Not required for the portfolio demo.**

---

## Today

| Layer | Provider | Coverage |
|-------|----------|----------|
| Primary | Finnhub | Splits, dividends |
| Secondary | Disabled | Mergers, spinoffs, symbol changes, delistings |

Phase 2 **logic is implemented**; without a secondary source, complex events are no-ops unless dev **fixtures** are enabled (FOX, EBAY, FB, TWTR).

---

## When to add a secondary source

- Wrong P&L on a merger, spinoff, or ticker change in production
- Paid tier requires full corporate-action coverage

**Options (pick one when triggered):**

| Path | Cost | Notes |
|------|------|-------|
| Paid API (Polygon ~$99/mo, Databento ~$99–199/mo) | Monthly | Fastest; wire `SecondaryCorporateActionProvider` |
| SEC EDGAR parser | Engineering time, $0/mo | Authoritative for US; one-time build |
| DB + nightly sync | Engineering time | See [FUTURE_ENHANCEMENTS.md](../FUTURE_ENHANCEMENTS.md) |

---

## Related

- [PROGRESS.md](PROGRESS.md) — what shipped
- [spec/phase-0-corporate-actions/](../../spec/phase-0-corporate-actions/01-corporate-actions-support.md) — original spec
