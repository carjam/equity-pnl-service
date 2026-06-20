# Corporate Actions — Data Provider Strategy

Guidance on primary and secondary data sources for corporate actions. Written to support **no recurring cost today**, with a clear path when M&A and spinoff coverage becomes necessary.

---

## Current setup (recommended for now)

| Layer | Provider | Cost | Coverage |
|-------|----------|------|----------|
| **Primary** | Finnhub (via `FinnhubCorporateActionProvider`) | Free tier | Splits, cash/stock dividends |
| **Secondary** | Disabled (`corporate-actions.secondary.enabled=false`) | $0 | Mergers, spinoffs, symbol changes, delistings |

Phase 2 **processing logic is implemented** in the service layer (`MergerService`, `SpinoffService`, etc.). What is missing without a secondary source is **data**, not code. An empty secondary provider is harmless: P&L still works; rare complex events simply are not applied until data exists.

**Recommendation:** Stay Finnhub-only until you hit a concrete gap (wrong P&L on a merger, spinoff, or ticker change) or until paying users require that coverage.

---

## What Finnhub does and does not cover

Finnhub handles the majority of day-to-day corporate-action impact for retail portfolios:

- Forward/reverse splits  
- Cash and stock dividends  

It does **not** reliably expose (via our integration):

- Stock-for-stock or cash mergers  
- Spinoffs and cost-basis allocation inputs  
- Ticker symbol changes (e.g. FB → META)  
- Delistings  

Those map to Phase 2 event types. Finnhub remains the right **free** primary for Phase 1.

---

## Secondary provider options (when you choose to add one)

There is no strong **free API** with production-grade M&A/spinoff history comparable to paid vendors. Free tiers elsewhere (Polygon sandbox, Alpha Vantage, etc.) are rate-limited or thin on complex events.

### Paid APIs (fastest path to production data)

| Provider | Rough cost | Strengths | When to choose |
|----------|------------|-----------|----------------|
| **Polygon.io** | ~$99/mo starter | US equities, good developer experience | Cost-sensitive; mostly US symbols |
| **Databento** | ~$99–199/mo | Broad event types, institutional-style coverage | Spec default; need fuller event catalog |
| **QUODD / enterprise feeds** | $200+/mo typical | Global exchanges, identifiers (CUSIP/ISIN) | Scale, multi-market, compliance-heavy |

**Suggested order when spending:** Polygon starter first if US-only and budget-conscious; Databento if you need broader event types or international names early.

Wire the chosen vendor into `SecondaryCorporateActionProvider` (or a dedicated `PolygonCorporateActionProvider` / `DatabentoCorporateActionProvider` bean registered ahead of Finnhub in `CompositeCorporateActionProvider`).

Configuration (already in `application.properties`):

```properties
corporate-actions.secondary.enabled=true
corporate-actions.secondary.url=https://...
corporate-actions.secondary.api-key=...
```

---

## SEC EDGAR — worth keeping on the table ($0 recurring)

**SEC EDGAR** is free, authoritative, and contains merger, acquisition, spinoff, and symbol-change facts in filings (8-K, DEF 14A, S-4, etc.).

| Pros | Cons |
|------|------|
| No monthly API fees | Significant **one-time** engineering (parsers, mapping, tests) |
| Official source of truth for US issuers | Filing formats vary; edge cases need careful handling |
| Amortizes: build once, save $100–200+/mo indefinitely | Ongoing maintenance when filing formats change (lower than API cost) |

**Economic framing:** Several weeks of focused work to parse EDGAR correctly can justify avoiding paid data feeds for years. Treat this as a **capital expense (engineering time)** vs **operating expense (monthly API)** tradeoff—not “free because easy.”

### EDGAR integration sketch (future)

1. **Ingest:** SEC full-text search or structured submissions API (`data.sec.gov`) by CIK/ticker and date range.  
2. **Parse:** Map filing types to domain models (`Merger`, `Spinoff`, `SymbolChange`, `Delisting`).  
3. **Normalize:** Ticker + effective date + ratios/cash terms → existing Phase 2 models.  
4. **Cache:** Same Caffeine pattern as Finnhub (historical filings are immutable).  
5. **Implement as:** `EdgarCorporateActionProvider` registered in `CompositeCorporateActionProvider` **instead of or before** a paid secondary—no `api-key` required.

Leave this option open in planning; do not block current shipping on it.

---

## Dev/test without any paid API

For validation and demos without subscribing to a vendor:

- **Stub provider in tests** — already used in `CorporateActionsPnLEndToEndTest` (mock `CorporateActionProvider`).  
- **Fixture provider** — `FixtureCorporateActionProvider` with FOX→DIS, EBAY→PYPL, FB→META, TWTR (dev profile only; tests construct provider directly).  
- **Manual JSON/CSV seed** — load known events for a symbol/date range in local/dev only.

These do not replace production data; they prove P&L math and REST APIs.

---

## Decision timeline

```
Now
  └─ Finnhub primary, secondary disabled
  └─ Phase 2 code + REST ready; complex events no-op without data

Trigger to act
  └─ User/report shows wrong P&L on merger, spinoff, or ticker change
  └─ Or product requires “full corporate actions” for paid tier

Then (pick one)
  ├─ Short term: Paid API (Polygon or Databento) → wire SecondaryCorporateActionProvider
  └─ Long term / cost-conscious: SEC EDGAR provider → one-time build, $0/month
```

---

## Related code

| Component | Role |
|-----------|------|
| `FinnhubCorporateActionProvider` | Primary: dividends, splits |
| `SecondaryCorporateActionProvider` | Placeholder for paid API; disabled by default |
| `CompositeCorporateActionProvider` | Merges providers; secondary first when enabled |
| `CorporateActionProviderFactory` | Exposes active provider names (`GET .../providers`) |

See also: [PROGRESS.md](PROGRESS.md), [PLAN.md](PLAN.md), [spec/phase-0-corporate-actions/01-corporate-actions-support.md](../../spec/phase-0-corporate-actions/01-corporate-actions-support.md).
