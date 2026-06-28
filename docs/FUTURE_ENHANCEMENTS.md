# Future Enhancements

Work deferred until a concrete trigger appears. Not required for the portfolio demo.

**Current status:** [PROJECT_STATUS.md](PROJECT_STATUS.md) · **Demo scope:** [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)

---

## What's shipped

| Area | Detail |
|------|--------|
| P&L engine | Long/short, AVCO, realized/unrealized with explicit methodology disclosure |
| Fractional shares | `BigDecimal(20,8)` throughout; 3:2 splits, stock dividends, fractional purchases preserved |
| Corporate actions | Splits, cash/stock dividends, mergers, spinoffs, symbol changes, delistings (including cash consideration) |
| Return of capital | Basis reduction with excess ROC recognised as realized gain |
| Qualified dividends | `Boolean qualified` flag on `Dividend`; income split into qualified/ordinary |
| Dividend ex-date semantics | CFA holder-of-record quantity at each ex-date (not period-end quantity) |
| FIFO tax-lot reporting | `GET /pnl/tax-lots` — closed lots, STCG/LTCG (IRC §1222), wash-sale flags (IRC §1091) |
| HPR & annualized return | `GET /pnl/total-return` — holding period return, geometric annualized return, period metadata |
| Security & resilience | JWT, per-user data isolation, validation, Resilience4j, structured errors |
| Ops baseline | CI (test + OWASP + Docker), Prometheus, JSON logging, correlation IDs |
| Tests | 307 automated tests, all passing |

---

## Deferred enhancements

| Item | Add when… |
|------|-----------|
| **Live M&A data provider** | Real merger/spinoff P&L is wrong in prod — see [corporate-actions/PROVIDER_STRATEGY.md](corporate-actions/PROVIDER_STRATEGY.md) |
| **DB-backed corporate action cache** | Finnhub rate limits (429), API cost, or outages block P&L |
| **Daily sync job** | Same trigger as DB cache — batch-fetch symbols once per day |
| **LIFO lot tracking** | FIFO is implemented; add LIFO as an electable method when brokerage reconciliation requires it |
| **Wash-sale basis carry-forward** | Disallowed losses are flagged but not automatically added to replacement-lot cost basis |
| **Load tests & p95 SLAs** | Before high-traffic production |
| **Grafana + alerts** | Ops team needs dashboards on `/actuator/prometheus` |
| **Prod deploy runbook** | Moving beyond demo/staging to a live environment |
| **UAT vs brokerage statements** | Product claim of >99% accuracy needs manual validation |
| **Event-driven ingestion** | Real-time transaction processing via message queue (Kafka/SQS) |
| **ITD/YTD/MTD snapshots** | Persisted position snapshots to avoid full replay on every request |
| **Transaction costs** | Commission and fee fields on `Transaction` to improve net-of-cost P&L |
| **Attribution analysis** | Beta/alpha relative to a benchmark index |
| **Margin calculations** | Leverage, margin interest, maintenance requirement |

---

## Corporate actions: stay stateless vs add DB

**Today:** Fetch on demand, cache 24h (recent) / 7d (historical), apply in memory. No `corporate_action` table.

**Consider DB + nightly sync when:**
- Many concurrent users × symbols exhausts Finnhub free tier (60 calls/min)
- You need a fallback when the provider is down
- Compliance requires an audit trail of which actions were applied when

Rough effort: 2–3 hours (one table, repository, scheduled job, fallback in provider layer).

---

*Last updated: June 27, 2026*
