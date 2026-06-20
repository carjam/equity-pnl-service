# Corporate Actions Support - CRITICAL PRIORITY (Stateless Design)

## Executive Summary

**Status:** 🔴 BLOCKING ISSUE  
**Priority:** CRITICAL - Must complete before production  
**Effort:** 12 days (Phase 1) + 5 days (Phase 2) = 17 days total  
**Architecture:** Stateless with aggressive caching

**Problem Statement:**  
The equity P&L service is fundamentally broken without corporate actions support. Stock splits, dividends, mergers, and other corporate events directly affect position quantities, cost basis, and P&L calculations. Without this functionality, the tool produces incorrect results and is unusable for real-world portfolios.

**Solution:**  
Implement corporate actions processing using a **stateless in-memory approach**:
- Fetch corporate actions from provider APIs on-demand
- Apply adjustments in-memory during P&L calculation
- Aggressive caching to minimize API calls
- No database persistence (keep architecture simple)
- **Phase 1:** Dividends and splits using Finnhub (free tier) - 12 days
- **Phase 2:** Mergers, acquisitions, spinoffs using Databento/QUODD - 5 days

---

## Table of Contents

1. [Corporate Actions Overview](#corporate-actions-overview)
2. [Architecture Principles](#architecture-principles)
3. [Phase 1: Dividends & Splits (Finnhub)](#phase-1-dividends--splits-finnhub)
4. [Phase 2: M&A & Complex Events](#phase-2-ma--complex-events)
5. [API Design](#api-design)
6. [Service Layer](#service-layer)
7. [P&L Adjustment Algorithms](#pnl-adjustment-algorithms)
8. [Caching Strategy](#caching-strategy)
9. [Data Provider Integration](#data-provider-integration)
10. [Testing Strategy](#testing-strategy)
11. [Future Enhancements](#future-enhancements)
12. [Acceptance Criteria](#acceptance-criteria)

---

## Corporate Actions Overview

### What Are Corporate Actions?

Corporate actions are events initiated by a publicly traded company that affect the securities issued by the company. These events directly impact:
- Share quantity
- Cost basis per share
- Symbol/ticker
- Total position value
- Realized/unrealized P&L

### Why This Is Critical

**Without corporate action processing:**

❌ **Stock Split Example:**  
- You buy 100 AAPL @ $200 = $20,000 cost basis
- AAPL does 4:1 split
- You now own 400 shares @ $50 market price
- **Without adjustment:** System shows 100 shares @ $50 = $5,000 (appears to be 75% loss!)
- **With adjustment:** System shows 400 shares @ $50 = $20,000 (correct, break-even)

❌ **Merger Example:**
- You own 100 shares of XYZ @ $50 = $5,000
- XYZ merges into ABC at 0.5:1 ratio
- You now own 50 ABC shares
- **Without adjustment:** System still shows XYZ position (delisted, no price), looks like 100% loss
- **With adjustment:** System shows 50 ABC shares with correct cost basis transfer

❌ **Dividend Example:**
- You own 100 shares of KO @ $60 = $6,000
- KO pays $1/share dividend = $100 cash
- **Without adjustment:** Total return calculation misses $100 in distributions
- **With adjustment:** System tracks $6,100 total return ($6,000 capital + $100 income)

### Event Types Covered

| Event Type | Phase | Provider | Impact | Priority |
|------------|-------|----------|--------|----------|
| **Cash Dividend** | 1 | Finnhub | Income distribution | CRITICAL |
| **Stock Dividend** | 1 | Finnhub | Quantity increase | CRITICAL |
| **Forward Split** | 1 | Finnhub | Quantity increase, price decrease | CRITICAL |
| **Reverse Split** | 1 | Finnhub | Quantity decrease, price increase | CRITICAL |
| **Merger** | 2 | Databento/QUODD | Symbol change, quantity change | HIGH |
| **Acquisition** | 2 | Databento/QUODD | Symbol change, cash + stock | HIGH |
| **Spinoff** | 2 | Databento/QUODD | New position created | HIGH |
| **Symbol Change** | 2 | Databento/QUODD | Ticker update | MEDIUM |
| **Delisting** | 2 | Databento/QUODD | Position closure | MEDIUM |

---

## Architecture Principles

### Design Philosophy: Keep It Stateless

**Current Architecture:**
```
User Request → Fetch Transactions (DB) → Fetch Market Prices (API) → Calculate P&L → Return
```

**With Corporate Actions (Stateless):**
```
User Request → Fetch Transactions (DB) 
            → Fetch Corporate Actions (API, cached)
            → Apply Adjustments (in-memory)
            → Fetch Market Prices (API)
            → Calculate P&L → Return
```

### Why Stateless?

**Benefits:**
- ✅ Simple architecture (no new database tables)
- ✅ No sync jobs to maintain
- ✅ Always fresh data from provider
- ✅ Consistent with current design
- ✅ Easy to reason about and debug

**Key Insight:**  
Corporate actions are **rare and immutable**:
- Most stocks split once every 5-10 years (if at all)
- Historical events never change (2020-08-31 AAPL split is permanent)
- Dividends are quarterly and predictable
- Cache them aggressively → No performance penalty

### When to Add Database (Future)

Add database persistence if you encounter:
1. **Rate limit issues** - Many concurrent users hitting provider API limits
2. **Provider outages** - Need fallback when external API is down
3. **Audit requirements** - Need to prove what adjustments were applied historically
4. **Cost optimization** - Provider charges per API call, bulk fetching is cheaper

See [Future Enhancements](#future-enhancements) for detailed tradeoffs.

---

## Phase 1: Dividends & Splits (Finnhub)

**Timeline:** 12 days  
**Cost:** $0 (uses existing Finnhub free tier)  
**Coverage:** US equities only  
**Impact:** Solves 80% of corporate action issues

### Objectives

1. ✅ Fetch dividend data from Finnhub API
2. ✅ Fetch stock split data from Finnhub API
3. ✅ Apply splits to positions in-memory
4. ✅ Calculate dividend income separately from capital gains
5. ✅ Adjust cost basis for stock dividends
6. ✅ Cache aggressively to minimize API calls
7. ✅ Provide API endpoints for corporate action queries

### Finnhub API Integration

#### Dividends Endpoint
```http
GET https://finnhub.io/api/v1/stock/dividend
  ?symbol={symbol}
  &from={yyyy-MM-dd}
  &to={yyyy-MM-dd}
  &token={apiKey}
```

**Response:**
```json
[
  {
    "date": "2024-08-12",
    "amount": 0.25,
    "adjustedAmount": 0.25,
    "currency": "USD",
    "declarationDate": "2024-07-30",
    "exDividendDate": "2024-08-09",
    "recordDate": "2024-08-10",
    "payDate": "2024-08-12",
    "frequency": 4,
    "symbol": "AAPL"
  }
]
```

#### Stock Splits Endpoint
```http
GET https://finnhub.io/api/v1/stock/split
  ?symbol={symbol}
  &from={yyyy-MM-dd}
  &to={yyyy-MM-dd}
  &token={apiKey}
```

**Response:**
```json
[
  {
    "symbol": "AAPL",
    "date": "2020-08-31",
    "fromFactor": 1,
    "toFactor": 4
  }
]
```

### Implementation Tasks - Phase 1

#### Week 1: API Integration & Models (5 days)

- [ ] **Day 1-2:** Domain models
  - [ ] Create `CorporateAction` interface
  - [ ] Create `Dividend` class (amount, dates, frequency, type)
  - [ ] Create `StockSplit` class (fromFactor, toFactor, splitRatio)
  - [ ] Create `CorporateActionType` enum (CASH_DIV, STOCK_DIV, FORWARD_SPLIT, REVERSE_SPLIT)
  - [ ] Create `DividendPayment` class (for user-specific income tracking)
  - [ ] Add validation (positive amounts, valid dates, etc.)

- [ ] **Day 3:** Finnhub API client
  - [ ] Add corporate actions methods to `FinhubRepository.java`
  - [ ] Implement `fetchDividends(symbol, from, to)`
  - [ ] Implement `fetchStockSplits(symbol, from, to)`
  - [ ] Add circuit breaker support (Resilience4j)
  - [ ] Handle API errors (429, 403, 500)
  - [ ] Create `FinnhubCorporateActionMapper` (DTO → domain model)
  - [ ] Add unit tests with mocked responses

- [ ] **Day 4:** Caching layer
  - [ ] Configure Caffeine cache for corporate actions
  - [ ] Historical events: 7 day TTL (immutable, cache longer)
  - [ ] Recent events: 24 hour TTL (may have updates)
  - [ ] Add cache configuration to `CacheConfig.java`
  - [ ] Add `@Cacheable` annotations with smart keys
  - [ ] Test cache hit/miss behavior

- [ ] **Day 5:** Provider interface
  - [ ] Create `CorporateActionProvider` interface
  - [ ] Implement `FinnhubCorporateActionProvider`
  - [ ] Add `getDividends(symbol, from, to)` method
  - [ ] Add `getStockSplits(symbol, from, to)` method
  - [ ] Return empty lists for unsupported event types
  - [ ] Add logging for API calls and cache hits

#### Week 2: Business Logic (5 days)

- [ ] **Day 6-7:** Split adjustment service
  - [ ] Create `SplitAdjustmentService.java`
  - [ ] Implement `applySplits(Position, List<StockSplit>)` method
  - [ ] Handle forward splits (e.g., 2:1, 4:1)
    - `newQuantity = oldQuantity × ratio`
    - `newAvgCost = oldAvgCost ÷ ratio`
  - [ ] Handle reverse splits (e.g., 1:10)
    - `ratio = toFactor / fromFactor = 0.1`
  - [ ] Handle fractional splits (e.g., 3:2)
    - `ratio = 1.5`
  - [ ] Verify total basis unchanged
  - [ ] Handle multiple consecutive splits
  - [ ] Write 15+ unit test scenarios

- [ ] **Day 8:** Dividend service
  - [ ] Create `DividendService.java`
  - [ ] Implement `calculateDividendIncome(symbol, shares, dateRange)`
  - [ ] Handle cash dividends (track as income)
  - [ ] Handle stock dividends (adjust quantity like small split)
  - [ ] Calculate shares held on record date (from transactions)
  - [ ] Aggregate dividend income by symbol
  - [ ] Calculate total return = capital gain + dividend income
  - [ ] Write 10+ unit test scenarios

- [ ] **Day 9:** Corporate action service
  - [ ] Create `CorporateActionService.java`
  - [ ] Implement `getCorporateActions(symbol, from, to)`
  - [ ] Implement `applyToPosition(Position, symbol, asOfDate)`
  - [ ] Fetch dividends and splits from provider
  - [ ] Apply splits to position
  - [ ] Calculate dividend income
  - [ ] Return adjusted position + dividend income
  - [ ] Add error handling for API failures

- [ ] **Day 10:** PnL integration
  - [ ] Update `PnLService.java` to use `CorporateActionService`
  - [ ] Apply corporate actions before P&L calculation
  - [ ] Include dividend income in total return
  - [ ] Handle edge case: same-day trade + corporate action
  - [ ] Update `PnLResponse` DTO to include:
    - `dividendIncome` field
    - `totalReturn` field
    - `corporateActionsApplied` list (for transparency)
  - [ ] Add backward compatibility (existing clients still work)

#### Week 3: API & Testing (2 days + buffer)

- [ ] **Day 11:** REST API endpoints
  - [ ] Create `CorporateActionController.java`
  - [ ] `GET /api/v1/corporate-actions?symbol={}&from={}&to={}` - List all actions
  - [ ] `GET /api/v1/corporate-actions/dividends?symbol={}` - Dividends only
  - [ ] `GET /api/v1/corporate-actions/splits?symbol={}` - Splits only
  - [ ] `GET /api/v1/pnl/total-return?symbol={}&from={}&to={}` - P&L with dividends
  - [ ] Add DTO classes for requests/responses
  - [ ] Add pagination support (optional for now)
  - [ ] Update Swagger/OpenAPI documentation

- [ ] **Day 12:** Comprehensive testing
  - [ ] Integration tests with real Finnhub API
  - [ ] End-to-end P&L calculation with splits
  - [ ] End-to-end P&L calculation with dividends
  - [ ] Test caching behavior
  - [ ] Test error handling (API failures)
  - [ ] Performance testing (ensure no degradation)
  - [ ] Documentation and examples

### Phase 1 Acceptance Criteria

✅ **Functional:**
- [ ] System correctly adjusts positions for all types of splits (forward, reverse, fractional)
- [ ] System tracks all dividend payments and includes them in total return
- [ ] Stock dividends correctly increase share quantity and adjust cost basis
- [ ] P&L calculations reflect corporate actions correctly
- [ ] API endpoints return accurate corporate action data

✅ **Performance:**
- [ ] Corporate action data fetched within 2 seconds for any symbol
- [ ] Cache hit rate >80% after initial load
- [ ] P&L calculation performance does not degrade by more than 10%

✅ **Data Quality:**
- [ ] >99% accuracy compared to brokerage statements
- [ ] No false positives (incorrect corporate actions)

---

## Phase 2: M&A & Complex Events

**Timeline:** 5 days  
**Cost:** ~$99-200/month (Databento Starter or QUODD)  
**Coverage:** US equities + major international  
**Impact:** Full corporate action coverage

### Objectives

1. ✅ Handle mergers (stock-for-stock, cash-for-stock, mixed)
2. ✅ Handle acquisitions (position closures, cash distributions)
3. ✅ Handle spinoffs (new positions created from existing)
4. ✅ Handle symbol/ticker changes
5. ✅ Handle delistings
6. ✅ Multi-provider support (Finnhub + Databento/QUODD)

### Data Provider Selection

**Recommended: Databento**
- Modern REST API with Python/Java clients
- 60+ event types including all M&A scenarios
- Point-in-time accuracy (critical for backtesting)
- $99-199/month for startup plans
- 4x daily updates

**Alternative: QUODD**
- Enterprise-grade, 190+ exchanges
- Specific endpoints for mergers, splits, distributions
- Supports CUSIP, ISIN, SEDOL identifiers
- Custom pricing (likely $200-500/month)

**Alternative: Polygon.io**
- Strong for US equities
- Good developer experience
- $99/month starter plan
- Less comprehensive international coverage

### Event Types - Phase 2

#### Mergers
**Type:** Stock-for-Stock  
**Example:** Company A merges into Company B at 0.8:1 ratio  
**Action:** 100 shares of A → 80 shares of B, cost basis transfers proportionally

**Type:** Cash-for-Stock  
**Example:** Company A acquired by Company B for $50/share cash  
**Action:** 100 shares of A → $5,000 cash, position closed, realized gain/loss

**Type:** Mixed Consideration  
**Example:** Company A acquired for $25 cash + 0.5 shares of B per share  
**Action:** 100 shares of A → $2,500 cash + 50 shares of B

#### Spinoffs
**Example:** Company A spins off subsidiary B at 1:10 ratio  
**Action:** 100 shares of A → remain 100 shares of A + 10 shares of B  
**Cost Basis:** Split based on market values on distribution date

#### Symbol Changes
**Example:** Facebook changes ticker from FB to META  
**Action:** Update symbol, preserve all position data, no P&L impact

#### Delistings
**Example:** Company A delisted due to bankruptcy  
**Action:** Mark position as closed, realize 100% loss

### Implementation Tasks - Phase 2

#### Week 1: Provider Integration (3 days)

- [ ] **Day 1:** Provider setup
  - [ ] Evaluate Databento vs QUODD vs Polygon.io
  - [ ] Sign up for trial/starter account
  - [ ] Test API endpoints and data quality
  - [ ] Make final selection
  - [ ] Add Maven dependency

- [ ] **Day 2:** Multi-provider architecture
  - [ ] Add M&A methods to `CorporateActionProvider` interface:
    - `getMergers(symbol, from, to)`
    - `getSpinoffs(symbol, from, to)`
    - `getSymbolChanges(symbol, from, to)`
  - [ ] Implement `DabentoProvider` or `QuoddProvider`
  - [ ] Create `CorporateActionProviderFactory`
  - [ ] Add provider priority logic (try Databento, fallback to Finnhub)
  - [ ] Add caching for new event types (7 day TTL)

- [ ] **Day 3:** Additional domain models
  - [ ] Create `Merger` class (acquirerSymbol, exchangeRatio, cashPerShare)
  - [ ] Create `Spinoff` class (parentSymbol, spunoffSymbol, distributionRatio)
  - [ ] Create `SymbolChange` class (oldSymbol, newSymbol, effectiveDate)
  - [ ] Add to `CorporateActionType` enum
  - [ ] Create mapper for provider-specific formats

#### Week 2: Complex Event Processing (2 days)

- [ ] **Day 4:** Merger & spinoff processing
  - [ ] Create `MergerService.java`
    - Stock-for-stock: transfer cost basis to new symbol
    - Cash-for-stock: close position, calculate realized P&L
    - Mixed: partial realization
  - [ ] Create `SpinoffService.java`
    - Calculate market value ratio on distribution date
    - Allocate cost basis proportionally
    - Return both parent and new spinoff position
  - [ ] Create `SymbolMappingService.java`
    - Handle ticker changes (update symbol in-memory)
    - Preserve historical data
  - [ ] Write 15+ unit tests for complex scenarios

- [ ] **Day 5:** Integration & deployment
  - [ ] Update `CorporateActionService` to use new providers
  - [ ] Update `PnLService` to handle mergers and spinoffs
  - [ ] Integration tests with real provider API
  - [ ] Test real-world examples (DIS/FOX merger, EBAY/PYPL spinoff)
  - [ ] Update API documentation
  - [ ] Deploy to staging
  - [ ] Deploy to production

### Phase 2 Acceptance Criteria

✅ **Functional:**
- [ ] System correctly processes stock-for-stock mergers
- [ ] System correctly processes cash acquisitions
- [ ] System creates new positions for spinoffs with correct cost basis
- [ ] System updates symbols automatically on ticker changes
- [ ] System handles delistings and realizes losses

✅ **Multi-Provider:**
- [ ] System successfully uses both Finnhub and secondary provider
- [ ] System falls back to secondary provider if primary fails
- [ ] No duplicate events from multiple sources

---

## API Design

### Corporate Actions Endpoints

#### 1. List Corporate Actions
```http
GET /api/v1/corporate-actions
  ?symbol={symbol}
  &from={yyyy-MM-dd}
  &to={yyyy-MM-dd}
  &type={DIVIDEND|SPLIT|MERGER|SPINOFF}
```

**Response:**
```json
{
  "symbol": "AAPL",
  "actions": [
    {
      "type": "FORWARD_SPLIT",
      "date": "2020-08-31",
      "details": {
        "fromFactor": 1,
        "toFactor": 4,
        "splitRatio": 4.0
      }
    },
    {
      "type": "CASH_DIVIDEND",
      "date": "2024-08-12",
      "details": {
        "amount": 0.25,
        "currency": "USD",
        "frequency": 4
      }
    }
  ]
}
```

#### 2. Get Dividends
```http
GET /api/v1/corporate-actions/dividends
  ?symbol={symbol}
  &from={yyyy-MM-dd}
  &to={yyyy-MM-dd}
```

**Response:**
```json
{
  "symbol": "AAPL",
  "dividends": [
    {
      "exDate": "2024-08-09",
      "payDate": "2024-08-12",
      "amount": 0.25,
      "currency": "USD",
      "type": "CASH"
    }
  ],
  "totalAmount": 1.00,
  "count": 4
}
```

#### 3. Get Stock Splits
```http
GET /api/v1/corporate-actions/splits
  ?symbol={symbol}
  &from={yyyy-MM-dd}
  &to={yyyy-MM-dd}
```

**Response:**
```json
{
  "symbol": "AAPL",
  "splits": [
    {
      "date": "2020-08-31",
      "fromFactor": 1,
      "toFactor": 4,
      "splitRatio": 4.0,
      "type": "FORWARD_SPLIT"
    }
  ]
}
```

#### 4. Get Total Return (with Dividends)
```http
GET /api/v1/pnl/total-return
  ?symbol={symbol}
  &from={yyyy-MM-dd}
  &to={yyyy-MM-dd}
```

**Response:**
```json
{
  "symbol": "AAPL",
  "capitalGain": 5000.00,
  "dividendIncome": 400.00,
  "totalReturn": 5400.00,
  "totalReturnPct": 27.0,
  "corporateActionsApplied": [
    {
      "type": "FORWARD_SPLIT",
      "date": "2020-08-31",
      "description": "4-for-1 stock split"
    },
    {
      "type": "CASH_DIVIDEND",
      "count": 16,
      "totalAmount": 400.00
    }
  ]
}
```

---

## Service Layer

### CorporateActionService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CorporateActionService {
    private final CorporateActionProviderFactory providerFactory;
    private final SplitAdjustmentService splitAdjustmentService;
    private final DividendService dividendService;
    
    /**
     * Fetch corporate actions from provider (cached)
     */
    @Cacheable(value = "corporate-actions", key = "#symbol + '-' + #from + '-' + #to")
    public CorporateActionsResult getCorporateActions(
        String symbol, 
        LocalDate from, 
        LocalDate to
    ) {
        CorporateActionProvider provider = providerFactory.getPrimaryProvider();
        
        List<Dividend> dividends = provider.getDividends(symbol, from, to);
        List<StockSplit> splits = provider.getStockSplits(symbol, from, to);
        List<Merger> mergers = provider.getMergers(symbol, from, to);
        List<Spinoff> spinoffs = provider.getSpinoffs(symbol, from, to);
        
        return new CorporateActionsResult(dividends, splits, mergers, spinoffs);
    }
    
    /**
     * Apply corporate actions to position IN MEMORY
     */
    public AdjustedPosition applyToPosition(
        Position position, 
        String symbol,
        LocalDate from,
        LocalDate asOfDate
    ) {
        // Fetch corporate actions (cached)
        CorporateActionsResult actions = getCorporateActions(symbol, from, asOfDate);
        
        // Apply splits (adjust quantity and cost basis)
        Position adjusted = splitAdjustmentService.applySplits(position, actions.getSplits());
        
        // Calculate dividend income
        BigDecimal dividendIncome = dividendService.calculateIncome(
            symbol, 
            position.getQuantity(), 
            actions.getDividends()
        );
        
        // Apply mergers/spinoffs (Phase 2)
        adjusted = applyMergers(adjusted, actions.getMergers());
        List<Position> spinoffPositions = applySpinoffs(adjusted, actions.getSpinoffs());
        
        return new AdjustedPosition(adjusted, dividendIncome, spinoffPositions);
    }
}
```

### SplitAdjustmentService

```java
@Service
@Slf4j
public class SplitAdjustmentService {
    
    /**
     * Apply stock splits to position
     */
    public Position applySplits(Position position, List<StockSplit> splits) {
        if (splits.isEmpty()) {
            return position;
        }
        
        Position adjusted = position.copy();
        
        for (StockSplit split : splits) {
            BigDecimal ratio = split.getSplitRatio(); // toFactor / fromFactor
            
            log.debug("Applying split {} to position {}", split, adjusted);
            
            // Adjust quantity: newQty = oldQty × ratio
            BigDecimal newQuantity = adjusted.getQuantity().multiply(ratio);
            
            // Total basis remains unchanged
            BigDecimal totalBasis = adjusted.getBasis();
            
            adjusted = new Position(
                adjusted.getSymbol(),
                newQuantity.toBigInteger(),
                totalBasis
            );
            
            log.debug("After split: {}", adjusted);
        }
        
        return adjusted;
    }
}
```

### DividendService

```java
@Service
@Slf4j
public class DividendService {
    
    /**
     * Calculate dividend income for a position
     */
    public BigDecimal calculateIncome(
        String symbol,
        BigInteger sharesHeld,
        List<Dividend> dividends
    ) {
        return dividends.stream()
            .filter(d -> d.getType() == DividendType.CASH)
            .map(d -> d.getAmount().multiply(new BigDecimal(sharesHeld)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Apply stock dividends (adjust quantity like small split)
     */
    public Position applyStockDividends(Position position, List<Dividend> dividends) {
        List<Dividend> stockDividends = dividends.stream()
            .filter(d -> d.getType() == DividendType.STOCK)
            .collect(Collectors.toList());
        
        if (stockDividends.isEmpty()) {
            return position;
        }
        
        Position adjusted = position.copy();
        
        for (Dividend div : stockDividends) {
            // Stock dividend is like a small split
            BigDecimal ratio = BigDecimal.ONE.add(div.getSharesPerShare());
            
            BigDecimal newQuantity = adjusted.getQuantity().multiply(ratio);
            
            adjusted = new Position(
                adjusted.getSymbol(),
                newQuantity.toBigInteger(),
                adjusted.getBasis() // Total basis unchanged
            );
        }
        
        return adjusted;
    }
}
```

### Updated PnLService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PnLService {
    private final TransactionRepository transactionRepository;
    private final FinhubRepository finhubRepository;
    private final CorporateActionService corporateActionService; // NEW
    
    public PnLResponse calculatePnL(String userId, LocalDate from, LocalDate to) {
        // 1. Fetch transactions (existing logic)
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        
        // 2. Calculate position (existing logic)
        Map<String, Position> positions = calculatePositions(transactions);
        
        // 3. NEW: Apply corporate actions to each position
        Map<String, AdjustedPosition> adjustedPositions = new HashMap<>();
        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            String symbol = entry.getKey();
            Position position = entry.getValue();
            
            // Apply splits, dividends, mergers, spinoffs
            AdjustedPosition adjusted = corporateActionService.applyToPosition(
                position,
                symbol,
                getEarliestTransactionDate(userId, symbol),
                to
            );
            
            adjustedPositions.put(symbol, adjusted);
        }
        
        // 4. Calculate P&L with adjusted positions
        BigDecimal totalCapitalGain = BigDecimal.ZERO;
        BigDecimal totalDividendIncome = BigDecimal.ZERO;
        
        for (AdjustedPosition adjusted : adjustedPositions.values()) {
            BigDecimal currentPrice = finhubRepository.getCurrentPrice(adjusted.getSymbol());
            
            BigDecimal capitalGain = calculateCapitalGain(adjusted.getPosition(), currentPrice);
            totalCapitalGain = totalCapitalGain.add(capitalGain);
            
            totalDividendIncome = totalDividendIncome.add(adjusted.getDividendIncome());
        }
        
        BigDecimal totalReturn = totalCapitalGain.add(totalDividendIncome);
        
        // 5. Return P&L with dividend income
        return new PnLResponse(
            totalCapitalGain,
            totalDividendIncome,
            totalReturn,
            adjustedPositions
        );
    }
}
```

---

## P&L Adjustment Algorithms

### 1. Stock Split Adjustment

**Formula:**
```
New Quantity = Old Quantity × Split Ratio
New Cost Basis per Share = Old Cost Basis ÷ Split Ratio
Total Basis = Unchanged
```

**Example - 4:1 Forward Split:**
```
Before: 100 shares @ $200 = $20,000 total basis
Split: 4:1 (toFactor=4, fromFactor=1, ratio=4.0)
After: 400 shares @ $50 = $20,000 total basis ✓
```

**Example - 1:10 Reverse Split:**
```
Before: 1000 shares @ $5 = $5,000 total basis
Split: 1:10 (toFactor=1, fromFactor=10, ratio=0.1)
After: 100 shares @ $50 = $5,000 total basis ✓
```

**Example - Fractional Split (3:2):**
```
Before: 100 shares @ $30 = $3,000 total basis
Split: 3:2 (toFactor=3, fromFactor=2, ratio=1.5)
After: 150 shares @ $20 = $3,000 total basis ✓
```

### 2. Cash Dividend Impact

**No position adjustment, tracked separately for total return:**
```
Capital Gain = (Current Price × Quantity) - Cost Basis
Dividend Income = Σ (Dividend per Share × Shares Held)
Total Return = Capital Gain + Dividend Income
```

**Example:**
```
Position: 100 shares @ $50 = $5,000 cost
Current Price: $60
Dividends: 4 quarters × $0.25 × 100 shares = $100

Capital Gain = ($60 × 100) - $5,000 = $1,000
Dividend Income = $100
Total Return = $1,100 (22% return vs 20% capital-only)
```

### 3. Stock Dividend Adjustment

**Stock dividends increase share count like a small split:**
```
New Quantity = Old Quantity × (1 + Dividend Rate)
New Cost Basis per Share = Old Cost Basis ÷ (1 + Dividend Rate)
```

**Example - 5% Stock Dividend:**
```
Before: 100 shares @ $100 = $10,000 basis
Dividend: 5% stock dividend (5 shares distributed)
After: 105 shares @ $95.24 = $10,000 basis ✓
```

### 4. Merger/Acquisition Adjustment (Phase 2)

**Stock-for-Stock:**
```
New Quantity = Old Quantity × Exchange Ratio
Symbol = Acquirer Symbol
Cost Basis = Transferred (no taxable event)
```

**Cash-for-Stock:**
```
Position Closed
Realized Gain = (Cash Received) - (Cost Basis)
```

**Mixed Consideration:**
```
Cash Portion: Realize gain/loss immediately
Stock Portion: Transfer basis, continue holding
```

### 5. Spinoff Cost Basis Allocation (Phase 2)

**Based on market values on distribution date:**
```
Total Original Basis = B
Parent Market Value on Distribution = P
Spunoff Market Value on Distribution = S
Total Market Value = P + S

Parent Allocation = B × (P / (P + S))
Spunoff Allocation = B × (S / (P + S))
```

**Example:**
```
Original: 100 shares Parent @ $100 = $10,000 basis
Spinoff: 1:10 ratio (receive 10 Spunoff per 100 Parent)

Distribution Date Values:
  Parent: $90 × 100 = $9,000
  Spunoff: $10 × 10 = $100
  Total: $9,100

Cost Basis Allocation:
  Parent: $10,000 × ($9,000 / $9,100) = $9,890.11
  Spunoff: $10,000 × ($100 / $9,100) = $109.89
```

---

## Caching Strategy

### Cache Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(7, TimeUnit.DAYS)  // Historical events: cache long
            .recordStats();
    }
    
    // Separate cache for recent events (shorter TTL)
    @Bean
    public Cache recentCorporateActionsCache() {
        return Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(24, TimeUnit.HOURS)  // Recent: cache 24h
            .recordStats()
            .build();
    }
}
```

### Smart Caching Strategy

```java
@Service
public class FinnhubCorporateActionProvider implements CorporateActionProvider {
    
    @Override
    public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
        // If date range is entirely historical (>30 days ago), cache for 7 days
        if (to.isBefore(LocalDate.now().minusDays(30))) {
            return getHistoricalDividends(symbol, from, to);
        }
        
        // If date range includes recent data, cache for 24 hours
        return getRecentDividends(symbol, from, to);
    }
    
    @Cacheable(value = "dividends-historical", key = "#symbol + '-' + #from + '-' + #to")
    private List<Dividend> getHistoricalDividends(String symbol, LocalDate from, LocalDate to) {
        return fetchFromFinnhub(symbol, from, to);
    }
    
    @Cacheable(value = "dividends-recent", key = "#symbol + '-' + #from + '-' + #to")
    private List<Dividend> getRecentDividends(String symbol, LocalDate from, LocalDate to) {
        return fetchFromFinnhub(symbol, from, to);
    }
}
```

### Cache Benefits

**API Call Reduction:**
```
Without cache: 100 users × 10 symbols = 1,000 API calls
With cache: 10 symbols = 10 API calls (100x reduction)

Historical data (>30 days old): 7 day cache = ~1 API call per symbol per week
Recent data: 24 hour cache = 1 API call per symbol per day
```

**Performance:**
```
Finnhub API call: ~500ms
Cache hit: <1ms
Speedup: 500x
```

---

## Data Provider Integration

### Provider Interface

```java
public interface CorporateActionProvider {
    String getName();
    
    List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to);
    List<StockSplit> getStockSplits(String symbol, LocalDate from, LocalDate to);
    
    // Phase 2
    List<Merger> getMergers(String symbol, LocalDate from, LocalDate to);
    List<Spinoff> getSpinoffs(String symbol, LocalDate from, LocalDate to);
    List<SymbolChange> getSymbolChanges(String symbol, LocalDate from, LocalDate to);
}
```

### Finnhub Provider

```java
@Component
@RequiredArgsConstructor
public class FinnhubCorporateActionProvider implements CorporateActionProvider {
    private final FinhubClient finhubClient;
    private final CorporateActionMapper mapper;
    
    @Override
    public String getName() {
        return "FINNHUB";
    }
    
    @Override
    @CircuitBreaker(name = "finnhub", fallbackMethod = "fallbackDividends")
    @Cacheable(value = "corporate-actions", key = "'div-' + #symbol + '-' + #from + '-' + #to")
    public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
        try {
            List<FinnhubDividend> response = finhubClient.stockDividends(
                symbol,
                from.toString(),
                to.toString()
            );
            
            return response.stream()
                .map(mapper::toDividend)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to fetch dividends from Finnhub: {}", e.getMessage());
            throw new CorporateActionException("Failed to fetch dividends", e);
        }
    }
    
    @Override
    @CircuitBreaker(name = "finnhub", fallbackMethod = "fallbackSplits")
    @Cacheable(value = "corporate-actions", key = "'split-' + #symbol + '-' + #from + '-' + #to")
    public List<StockSplit> getStockSplits(String symbol, LocalDate from, LocalDate to) {
        try {
            List<FinnhubSplit> response = finhubClient.stockSplits(
                symbol,
                from.toString(),
                to.toString()
            );
            
            return response.stream()
                .map(mapper::toStockSplit)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to fetch splits from Finnhub: {}", e.getMessage());
            throw new CorporateActionException("Failed to fetch splits", e);
        }
    }
    
    // Fallback methods
    private List<Dividend> fallbackDividends(String symbol, LocalDate from, LocalDate to, Throwable t) {
        log.warn("Returning empty dividend list due to API failure");
        return Collections.emptyList();
    }
    
    private List<StockSplit> fallbackSplits(String symbol, LocalDate from, LocalDate to, Throwable t) {
        log.warn("Returning empty split list due to API failure");
        return Collections.emptyList();
    }
    
    @Override
    public List<Merger> getMergers(String symbol, LocalDate from, LocalDate to) {
        // Not supported by Finnhub
        return Collections.emptyList();
    }
    
    @Override
    public List<Spinoff> getSpinoffs(String symbol, LocalDate from, LocalDate to) {
        // Not supported by Finnhub
        return Collections.emptyList();
    }
}
```

### Provider Factory

```java
@Component
@RequiredArgsConstructor
public class CorporateActionProviderFactory {
    private final FinnhubCorporateActionProvider finnhubProvider;
    // Phase 2: private final DabentoCorporateActionProvider dabentoProvider;
    
    public CorporateActionProvider getPrimaryProvider() {
        return finnhubProvider;
    }
    
    public CorporateActionProvider getSecondaryProvider() {
        // Phase 2: return dabentoProvider;
        return finnhubProvider;
    }
    
    public List<CorporateActionProvider> getAllProviders() {
        // Phase 2: return Arrays.asList(dabentoProvider, finnhubProvider);
        return Arrays.asList(finnhubProvider);
    }
}
```

---

## Testing Strategy

### Unit Tests (40+ tests)

**Split Adjustment Tests:**
- [ ] Test 2:1 forward split
- [ ] Test 4:1 forward split
- [ ] Test 1:10 reverse split
- [ ] Test 3:2 fractional split
- [ ] Test multiple consecutive splits
- [ ] Test split with zero position (no-op)
- [ ] Test split with fractional shares

**Dividend Tests:**
- [ ] Test cash dividend income calculation
- [ ] Test stock dividend quantity adjustment
- [ ] Test dividend with no position
- [ ] Test multiple dividends in period
- [ ] Test total return calculation
- [ ] Test quarterly dividend aggregation

**Corporate Action Service Tests:**
- [ ] Test fetching from provider (mocked)
- [ ] Test caching behavior
- [ ] Test error handling (API failures)
- [ ] Test fallback to empty list
- [ ] Test applying to position

**P&L Integration Tests:**
- [ ] Test P&L with splits applied
- [ ] Test P&L with dividends included
- [ ] Test total return calculation
- [ ] Test edge case: same-day trade + split

**Merger Tests (Phase 2):**
- [ ] Test stock-for-stock merger
- [ ] Test cash-for-stock acquisition
- [ ] Test mixed consideration
- [ ] Test fractional shares in merger

**Spinoff Tests (Phase 2):**
- [ ] Test cost basis allocation
- [ ] Test new position creation
- [ ] Test market value-based split

### Integration Tests (10+ tests)

- [ ] Test with real Finnhub API (dev environment)
- [ ] Test cache hit/miss behavior
- [ ] Test end-to-end split processing
- [ ] Test end-to-end dividend processing
- [ ] Test performance (no degradation)
- [ ] Test error handling (API down)
- [ ] Test rate limiting (respect 60/min)

### User Acceptance Testing

**Test Portfolio:**
```
Symbol: AAPL
Transaction: Buy 100 shares on 2020-01-01 @ $75
Corporate Action: 4:1 split on 2020-08-31
Current Position: Should show 400 shares @ $18.75 basis
Expected: Correct unrealized P&L based on current price

Symbol: KO
Transaction: Buy 100 shares on 2023-01-01 @ $60
Dividends: $0.25/quarter × 4 × 100 shares = $100/year
Current Price: $63
Expected: Capital gain $300, Dividend income $100, Total return $400
```

---

## Future Enhancements

### When to Add Database Persistence

**Current Approach: Stateless (API + Cache)**
- Simple, no new tables
- Always fresh data
- Works well for <100 concurrent users

**Add Database When:**

#### 1. Rate Limit Issues
**Problem:** Many users hitting Finnhub's 60 calls/min limit

**Solution:**
- Add daily batch job to fetch all corporate actions
- Store in database
- Serve from DB instead of API

**Tables:**
```sql
CREATE TABLE corporate_action (
    id INT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(20),
    action_type VARCHAR(50),
    action_date DATE,
    details JSON,
    INDEX (symbol, action_date)
);
```

**Benefit:** 1,000 users = 1 API call, not 1,000

#### 2. Provider Outages
**Problem:** Finnhub API down, can't calculate P&L

**Solution:**
- Database acts as fallback cache
- Last-known corporate actions still available
- Graceful degradation

**Architecture:**
```
Try: Fetch from API (fresh)
Fallback: Fetch from DB (slightly stale)
```

#### 3. Audit Requirements
**Problem:** Need to prove which corporate actions were applied at specific date

**Solution:**
- Store applied corporate actions per calculation
- Track which adjustments affected each position

**Table:**
```sql
CREATE TABLE pnl_calculation_log (
    id INT PRIMARY KEY,
    user_id INT,
    symbol VARCHAR(20),
    calculation_date TIMESTAMP,
    corporate_actions_applied JSON,
    INDEX (user_id, symbol, calculation_date)
);
```

#### 4. Cost Optimization
**Problem:** Provider charges per API call, getting expensive

**Solution:**
- Bulk fetch once daily instead of on-demand
- Store in DB, serve from cache

**Cost Comparison:**
```
On-Demand: 100 users × 10 symbols × 30 days = 30,000 API calls/month
Batch: 10 symbols × 1 call/day = 300 API calls/month (100x reduction)
```

### Database Schema (Future)

If you add database persistence later, here's the minimal schema:

```sql
-- Main table
CREATE TABLE corporate_action (
    id INT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    action_type VARCHAR(50) NOT NULL,  -- SPLIT, DIVIDEND, MERGER, etc.
    action_date DATE NOT NULL,
    details JSON NOT NULL,  -- Store all event-specific data as JSON
    provider VARCHAR(50) NOT NULL,
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY (symbol, action_date, action_type),
    INDEX (symbol, action_date)
);

-- Example rows
INSERT INTO corporate_action VALUES
(1, 'AAPL', 'SPLIT', '2020-08-31', '{"fromFactor": 1, "toFactor": 4}', 'FINNHUB', NOW()),
(2, 'AAPL', 'DIVIDEND', '2024-08-12', '{"amount": 0.25, "currency": "USD"}', 'FINNHUB', NOW());
```

**Key Design Choices:**
- Single table (not 8 tables) - use JSON for flexibility
- Simple schema - easy to add later
- Index on (symbol, date) - fast lookups

### Migration Path

If you decide to add database later:

**Step 1:** Add single table (5 minutes)
```sql
CREATE TABLE corporate_action (...);
```

**Step 2:** Add repository (30 minutes)
```java
@Repository
public interface CorporateActionRepository extends JpaRepository<CorporateAction, Long> {
    List<CorporateAction> findBySymbolAndActionDateBetween(String symbol, LocalDate from, LocalDate to);
}
```

**Step 3:** Update service with fallback (1 hour)
```java
public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
    try {
        // Try API first
        return fetchFromApi(symbol, from, to);
    } catch (Exception e) {
        // Fallback to database
        return fetchFromDatabase(symbol, from, to);
    }
}
```

**Total effort to add database: 2-3 hours**

### Comparison Table

| Aspect | Stateless (Now) | Stateful (Future) |
|--------|-----------------|-------------------|
| **Complexity** | Low (no new tables) | Medium (1 table + sync job) |
| **API Calls** | On-demand (cached) | Batch (1x daily) |
| **Freshness** | Always current | Up to 24h stale |
| **Resilience** | API dependency | Works during outages |
| **Audit Trail** | No | Yes |
| **Cost** | Pay per API call | Lower API costs |
| **When to Use** | <100 users, occasional | >100 users, frequent |

---

## Acceptance Criteria

### Phase 1 Complete When:

✅ **Functional:**
- [ ] System correctly adjusts positions for all types of splits (forward, reverse, fractional)
- [ ] System tracks all dividend payments and includes them in total return
- [ ] Stock dividends correctly increase share quantity and adjust cost basis
- [ ] P&L calculations reflect corporate actions accurately
- [ ] API endpoints return correct corporate action data

✅ **Performance:**
- [ ] Corporate action data fetched within 2 seconds for any symbol
- [ ] Cache hit rate >80% for historical queries
- [ ] P&L calculation performance degradation <10%

✅ **Data Quality:**
- [ ] >99% accuracy compared to brokerage statements
- [ ] Zero false positives (incorrect corporate actions)
- [ ] Handles edge cases gracefully

✅ **Testing:**
- [ ] >90% code coverage for corporate action modules
- [ ] All unit tests passing (40+ tests)
- [ ] All integration tests passing (10+ tests)

### Phase 2 Complete When:

✅ **M&A Support:**
- [ ] Stock-for-stock mergers processed correctly
- [ ] Cash acquisitions processed correctly
- [ ] Mixed consideration handled correctly
- [ ] Cost basis preserved through mergers

✅ **Spinoff Support:**
- [ ] New positions created automatically
- [ ] Cost basis allocated correctly
- [ ] Parent position preserved

✅ **Multi-Provider:**
- [ ] Both Finnhub and Databento/QUODD integrated
- [ ] Provider failover working
- [ ] No duplicate events from multiple providers

---

## Risk Mitigation

### High-Risk Items

1. **API Rate Limits**
   - **Risk:** Finnhub free tier = 60 calls/min
   - **Mitigation:** Aggressive caching (7 day TTL for historical), batch queries
   - **Fallback:** Add database if rate limits become issue

2. **Provider API Downtime**
   - **Risk:** Can't calculate P&L if Finnhub is down
   - **Mitigation:** Circuit breaker returns empty list (graceful degradation)
   - **Fallback:** Add database for resilience

3. **Incorrect Split Ratios**
   - **Risk:** Wrong calculation destroys position accuracy
   - **Mitigation:** 15+ unit tests, cross-validate with known splits (AAPL 4:1, TSLA 5:1)

4. **Cache Invalidation**
   - **Risk:** Stale data in cache causes incorrect calculations
   - **Mitigation:** 24 hour TTL for recent data, manual cache eviction endpoint

5. **Performance Degradation**
   - **Risk:** Adding corporate actions slows down P&L calculation
   - **Mitigation:** Aggressive caching, benchmark before/after, <10% degradation target

---

## Timeline Summary

| Phase | Duration | Deliverable |
|-------|----------|-------------|
| Phase 1: Dividends & Splits | 12 days | Basic corporate actions (Finnhub, stateless) |
| Phase 2: M&A & Complex | 5 days | Full corporate actions (Multi-provider) |
| **Total** | **17 days** | **Production-ready corporate actions** |

**Week-by-Week:**
- Week 1: API integration, models, caching
- Week 2: Business logic (splits, dividends, P&L integration)
- Week 3 (partial): APIs, testing, documentation
- Week 4 (partial): Multi-provider, complex events

---

## Documentation Deliverables

- [ ] Corporate Actions User Guide
- [ ] API Documentation (Swagger/OpenAPI)
- [ ] Algorithm Documentation (split adjustment, cost basis)
- [ ] Provider Integration Guide
- [ ] Caching Strategy Documentation
- [ ] Troubleshooting Guide

---

## Success Metrics

**Accuracy:**
- [ ] >99% agreement with brokerage statements
- [ ] Zero critical bugs in first month
- [ ] <1% support tickets related to corporate actions

**Performance:**
- [ ] P&L calculation time increase <10%
- [ ] API response times <500ms p95
- [ ] Cache hit rate >80%

**Simplicity:**
- [ ] Zero new database tables (stateless design maintained)
- [ ] <2,000 lines of new code
- [ ] Easy to understand and maintain

---

**Document Version:** 2.0 (Stateless Design)  
**Last Updated:** June 19, 2026  
**Status:** READY FOR IMPLEMENTATION  
**Priority:** 🔴 CRITICAL - MUST IMPLEMENT BEFORE PRODUCTION

**Key Change from v1.0:** Removed database persistence, using stateless in-memory processing with aggressive caching. Database approach documented as future enhancement.
