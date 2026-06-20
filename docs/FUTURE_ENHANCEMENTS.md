# Future Enhancements

Work deferred until a concrete trigger appears. Not required for the portfolio demo.

**Current status:** [PROJECT_STATUS.md](PROJECT_STATUS.md) · **Demo scope:** [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)

---

## Already done (not future work)

| Area | Shipped |
|------|---------|
| Corporate actions | Stateless Finnhub + Caffeine; optional Redis in staging |
| Complex events | Merger/spinoff/symbol/delisting logic + dev fixtures (FOX, EBAY, FB, TWTR) |
| Security & resilience | JWT, validation, Resilience4j, structured errors |
| Ops baseline | CI (test + OWASP + Docker), Prometheus, JSON logging, correlation IDs |
| Tests | 257 automated tests |

---

## Deferred enhancements

| Item | Add when… |
|------|-----------|
| **Live M&A data provider** | Real merger/spinoff P&L is wrong in prod — see [corporate-actions/PROVIDER_STRATEGY.md](corporate-actions/PROVIDER_STRATEGY.md) |
| **DB-backed corporate action cache** | Finnhub rate limits (429), API cost, or outages block P&L |
| **Daily sync job** | Same trigger as DB cache — batch-fetch symbols once per day |
| **FIFO / LIFO lot tracking** | Tax or brokerage reconciliation needs lot-level cost basis |
| **Load tests & p95 SLAs** | Before high-traffic production |
| **Grafana + alerts** | Ops team needs dashboards on `/actuator/prometheus` |
| **Prod deploy runbook** | Moving beyond demo/staging to a live environment |
| **UAT vs brokerage statements** | Product claim of >99% accuracy needs manual validation |

---

## Corporate actions: stay stateless vs add DB

**Today:** Fetch on demand, cache 24h (recent) / 7d (historical), apply in memory. No `corporate_action` table.

**Consider DB + nightly sync when:**
- Many concurrent users × symbols exhausts Finnhub free tier (60 calls/min)
- You need a fallback when the provider is down
- Compliance requires an audit trail of which actions were applied when

Rough effort: 2–3 hours (one table, repository, scheduled job, fallback in provider layer).

---

*Last updated: June 20, 2026*
