# Corporate Actions Implementation Plan

## Executive Summary

**Created:** June 19, 2026  
**Status:** Ready for Implementation  
**Priority:** 🔴 CRITICAL - BLOCKING PRODUCTION  
**Estimated Effort:** 17 days (~3.5 weeks)  
**Architecture:** Stateless with aggressive caching (no database)

---

## The Problem

The equity P&L service is **fundamentally broken** without corporate actions support. Here's why:

### Example 1: Stock Split (Most Common)
```
Scenario: You buy 100 AAPL @ $200
          AAPL does 4:1 split
          
WITHOUT ADJUSTMENT:
  System shows: 100 shares @ $50 market price = $5,000 value
  Cost basis: $20,000
  Calculated P&L: -$15,000 (75% LOSS) ❌ WRONG!
  
WITH ADJUSTMENT:
  System shows: 400 shares @ $50 market price = $20,000 value
  Cost basis: $20,000 (adjusted to $50/share)
  Calculated P&L: $0 (break-even) ✅ CORRECT!
```

### Example 2: Merger
```
Scenario: You own 100 XYZ @ $50 = $5,000
          XYZ merges into ABC at 0.5:1 ratio
          
WITHOUT ADJUSTMENT:
  System shows: 100 XYZ (delisted, no price)
  Appears as: 100% loss ❌ WRONG!
  
WITH ADJUSTMENT:
  System shows: 50 ABC shares
  Cost basis: $5,000 transferred
  P&L: Calculated correctly ✅ CORRECT!
```

### Example 3: Dividends
```
Scenario: You own 100 KO @ $60 = $6,000
          KO pays $1/share in dividends over the year
          
WITHOUT ADJUSTMENT:
  Capital return only: Shows only price appreciation
  Missing: $100 in dividend income ❌ INCOMPLETE!
  
WITH ADJUSTMENT:
  Capital gain: $300 (price went to $63)
  Dividend income: $100
  Total return: $400 (6.67%) ✅ CORRECT!
```

---

## The Solution

### Architecture: Stateless Design

**Key Principle:** Keep it simple - no database tables, no sync jobs

```
Request Flow:
  User Request 
    → Fetch Transactions (DB - existing)
    → Fetch Corporate Actions (API, cached 7 days)
    → Apply Adjustments (in-memory)
    → Calculate P&L
    → Return
```

**Benefits:**
- ✅ Simple (no new database tables)
- ✅ Always fresh data
- ✅ Consistent with current stateless architecture
- ✅ Easy to maintain

**When to add database:** Rate limits, provider outages, audit requirements (see Future Enhancements)

### Two-Phase Implementation

#### Phase 1: Dividends & Splits (12 days)
**Provider:** Finnhub (already integrated, FREE tier)  
**Covers:** 80% of corporate action scenarios  
**Events:**
- Cash dividends
- Stock dividends
- Forward splits (2:1, 4:1, etc.)
- Reverse splits (1:10, etc.)
- Fractional splits (3:2, etc.)

#### Phase 2: M&A & Complex Events (5 days)
**Provider:** Databento or QUODD ($99-200/month)  
**Covers:** Remaining 20%, but critical for accuracy  
**Events:**
- Mergers (stock-for-stock)
- Acquisitions (cash-for-stock)
- Mixed consideration deals
- Spinoffs
- Symbol/ticker changes
- Delistings

---

## What's Been Created

### 1. Comprehensive Specification Document
**Location:** `spec/phase-0-corporate-actions/01-corporate-actions-support.md`

**Contents:**
- Stateless architecture design (no database tables)
- Aggressive caching strategy (7 day TTL)
- Detailed API design (4 new endpoints)
- Service layer architecture
- P&L adjustment algorithms with formulas
- Data provider integration patterns
- Testing strategy (50+ test cases)
- Future enhancements (when to add database)
- Acceptance criteria

### 2. Updated Project Roadmap
**Files Modified:**
- `spec/README.md` - Added Phase 0 as blocking prerequisite
- `spec/CHECKLIST.md` - Added 80+ implementation tasks
- `README.md` - Prominently displays critical issue

### 3. Detailed Implementation Checklist

**Week 1:** Domain models + Finnhub integration + caching  
**Week 2:** Split adjustment + dividend logic + P&L integration  
**Week 3:** REST APIs + testing (first 2 days) + Secondary provider (3 days)  
**Week 4:** Complex event processing (2 days only) + deployment

---

## Technical Details

### Domain Models (In-Memory, No Database)

1. **CorporateAction** (interface) - Base for all events
2. **Dividend** - amount, dates, type (cash/stock)
3. **StockSplit** - fromFactor, toFactor, splitRatio
4. **Merger** - acquirer, target, exchange ratio (Phase 2)
5. **Spinoff** - parent, spunoff, distribution ratio (Phase 2)
6. **SymbolChange** - old → new symbol mapping (Phase 2)

**No database tables!** Fetch from API, cache aggressively, process in-memory.

### Key Algorithms

**Stock Split Adjustment:**
```java
New Quantity = Old Quantity × Split Ratio
New Cost Basis per Share = Old Cost Basis ÷ Split Ratio
Total Basis = Unchanged (verified)
```

**Dividend Income:**
```java
Capital Gain = (Current Price × Quantity) - Cost Basis
Dividend Income = Σ (Dividend × Shares Held on Record Date)
Total Return = Capital Gain + Dividend Income
```

**Spinoff Cost Basis Allocation:**
```java
Parent Allocation = Total Basis × (Parent MV / Total MV)
Spinoff Allocation = Total Basis × (Spinoff MV / Total MV)
```

### API Endpoints (New)

```
GET  /api/v1/corporate-actions                 # List all actions
GET  /api/v1/corporate-actions/dividends       # Get dividends
GET  /api/v1/corporate-actions/splits          # Get splits
GET  /api/v1/pnl/total-return                  # P&L with dividends (enhanced)
```

**Note:** No sync endpoint needed (stateless design)

---

## Data Sources

### Phase 1: Finnhub (Free Tier)
- **Cost:** $0 (already using for market data)
- **Coverage:** US equities, 30 years history
- **Rate Limit:** 60 calls/minute
- **APIs:** `/stock/dividend`, `/stock/split`
- **Quality:** Reliable, well-documented

### Phase 2: Databento (Recommended)
- **Cost:** $99-199/month
- **Coverage:** 60+ event types, global markets
- **Quality:** Institutional-grade, point-in-time accuracy
- **Updates:** 4x daily
- **API:** Modern REST with Java/Python clients

**Alternative:** QUODD ($200-500/month) or Polygon.io ($99/month)

---

## Risk Mitigation

### High-Risk Items

1. **Incorrect Split Ratios**
   - **Mitigation:** 15+ test scenarios, cross-validate with historical data

2. **Missing Corporate Actions**
   - **Mitigation:** Phase 2 adds secondary provider for redundancy

3. **Historical Data Migration**
   - **Mitigation:** Test in dev/staging first, validate against brokerage statements

4. **Performance Impact**
   - **Mitigation:** Aggressive caching, indexed queries, benchmark before/after

5. **API Rate Limits**
   - **Mitigation:** Throttle daily sync job (55 calls/min), cache aggressively

---

## Success Metrics

**Accuracy:**
- >99% agreement with brokerage statements
- Zero critical bugs in first month production
- <1% of users report P&L discrepancies

**Performance:**
- P&L calculation time increase <20%
- Corporate action queries <500ms p95
- Daily sync job completes in <30 minutes for 100 symbols

**Coverage:**
- 100% of stock splits handled correctly
- 100% of dividends tracked
- >95% of M&A events processed correctly

---

## Timeline (Simplified - Stateless)

```
Week 1: Models + API + Caching
  ├─ Day 1-2: Domain models (Dividend, StockSplit, etc.)
  ├─ Day 3: Finnhub API client (dividends, splits)
  ├─ Day 4: Caching layer (Caffeine, 7 day TTL)
  └─ Day 5: Provider interface

Week 2: Business Logic
  ├─ Day 6-7: Split adjustment algorithms
  ├─ Day 8: Dividend tracking service
  ├─ Day 9: Corporate action service
  └─ Day 10: P&L integration

Week 3: API + Testing + Phase 2 Prep
  ├─ Day 11: REST endpoints
  ├─ Day 12: Unit tests (40+) + Integration tests
  ├─ Day 13: Provider evaluation + signup (Databento/QUODD)
  ├─ Day 14-15: Multi-provider implementation

Week 4: Complex Events (Phase 2)
  ├─ Day 16: Merger models + processing
  ├─ Day 17: Spinoff processing + symbol changes
  └─ DONE! (5 days saved vs database approach)
```

---

## Next Steps

### Immediate Actions

1. ✅ **Review specification** - Validate approach, identify gaps
2. ⬜ **Set up dev environment** - Ensure Finnhub API access
3. ⬜ **Create Phase 0 Git branch** - `feature/corporate-actions`
4. ⬜ **Begin Week 1 tasks** - Start with database schema
5. ⬜ **Daily standups** - Track progress, unblock issues

### Phase 2 Prep (During Week 3)

1. ⬜ **Evaluate providers** - Databento vs QUODD vs Polygon
2. ⬜ **Sign up for trial** - Test APIs and data quality
3. ⬜ **Budget approval** - Get $99-200/month approved
4. ⬜ **Select provider** - Make final decision by end of Week 3

---

## Documentation

### Key Documents

1. **Full Specification**  
   `spec/phase-0-corporate-actions/01-corporate-actions-support.md`  
   92 pages, covers everything in detail

2. **Implementation Checklist**  
   `spec/CHECKLIST.md`  
   80+ granular tasks, organized by week/day

3. **Project Roadmap**  
   `spec/README.md`  
   Updated with Phase 0 as critical prerequisite

4. **README**  
   `README.md`  
   Updated to highlight critical missing feature

---

## Questions?

**Technical Questions:** Review full specification document  
**Timeline Questions:** See detailed checklist with day-by-day tasks  
**Provider Questions:** Data sources section covers options  
**Testing Questions:** Testing strategy section has 70+ test scenarios

---

## Conclusion

This is **the most critical feature** for the equity P&L service. Without corporate actions support:
- P&L calculations are fundamentally wrong
- Stock splits show as massive losses
- Mergers appear as delistings
- Total return is understated (missing dividends)

**The service is not usable in production without this.**

**Status:** Ready to implement  
**Timeline:** 25 days (5 weeks)  
**Next Step:** Begin Week 1 - Database schema implementation

---

*Document created: June 19, 2026*  
*Specification location: `spec/phase-0-corporate-actions/01-corporate-actions-support.md`*
