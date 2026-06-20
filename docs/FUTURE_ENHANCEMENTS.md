# Future Enhancements - Corporate Actions Database

## Current Design: Stateless

The corporate actions feature is implemented using a **stateless architecture**:
- Fetch corporate actions from provider APIs on-demand
- Aggressive caching (7 day TTL for historical, 24 hour for recent)
- Apply adjustments in-memory during P&L calculation
- **Zero database tables**

## When to Add Database Persistence

### Trigger #1: Rate Limit Issues

**Problem:**  
Finnhub free tier = 60 API calls/minute. With many concurrent users:
```
100 users × 10 symbols × 2 API calls (dividends + splits) = 2,000 calls
At 60/min limit = 33+ minutes to serve all requests
```

**Solution:**  
Add daily batch job to pre-fetch corporate actions for all portfolio symbols.

**Implementation (2-3 hours):**
```sql
CREATE TABLE corporate_action (
    id INT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_date DATE NOT NULL,
    details JSON NOT NULL,
    provider VARCHAR(50) NOT NULL,
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY (symbol, action_date, action_type),
    INDEX (symbol, action_date)
);
```

```java
@Scheduled(cron = "0 0 1 * * *")  // 1 AM daily
public void syncCorporateActions() {
    Set<String> symbols = getPortfolioSymbols();
    for (String symbol : symbols) {
        List<CorporateAction> actions = provider.getActions(symbol);
        repository.saveAll(actions);
    }
}
```

**Benefit:**  
- 1,000 users = 1 API call (not 1,000)
- 100x reduction in API calls
- No rate limit issues

**Cost:**  
- 1 database table
- 1 scheduled job
- Slightly more complex

---

### Trigger #2: Provider Outages

**Problem:**  
Finnhub API down → can't calculate P&L → users see errors

**Solution:**  
Database acts as fallback cache with last-known corporate actions.

**Implementation:**
```java
public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
    try {
        // Try API first (fresh data)
        return fetchFromApi(symbol, from, to);
    } catch (ApiException e) {
        log.warn("API unavailable, falling back to database");
        // Fallback to database (slightly stale)
        return fetchFromDatabase(symbol, from, to);
    }
}
```

**Benefit:**  
- Graceful degradation during outages
- Always returns some data (even if 24h old)
- Better user experience

**Cost:**  
- Database table + sync job
- Data may be up to 24 hours stale

---

### Trigger #3: Audit Requirements

**Problem:**  
Need to prove which corporate actions were applied at specific datetime for regulatory/compliance.

**Solution:**  
Log every P&L calculation with applied corporate actions.

**Implementation:**
```sql
CREATE TABLE pnl_calculation_log (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    calculation_timestamp TIMESTAMP NOT NULL,
    corporate_actions_applied JSON NOT NULL,
    result JSON NOT NULL,
    INDEX (user_id, symbol, calculation_timestamp)
);
```

```java
public PnLResponse calculatePnL(...) {
    List<CorporateAction> actions = getCorporateActions(...);
    PnLResponse result = compute(...);
    
    // Log for audit trail
    auditLogRepository.save(new PnLCalculationLog(
        userId, symbol, actions, result
    ));
    
    return result;
}
```

**Benefit:**  
- Complete audit trail
- Can reproduce historical calculations
- Regulatory compliance

**Cost:**  
- 1 additional table
- Storage grows over time
- Slightly slower calculation

---

### Trigger #4: Cost Optimization

**Problem:**  
Provider charges per API call. With growth, costs become significant.

**Scenario:**
```
1,000 users × 10 symbols × 30 requests/month = 300,000 API calls/month
If provider charges $0.01/call = $3,000/month
```

**Solution:**  
Bulk fetch once daily instead of on-demand.

**Implementation:**
Same as Trigger #1 (daily batch job + database storage).

**Benefit:**
```
Daily batch: 10 symbols × 1 call/day × 30 days = 300 API calls/month
Cost: 300 × $0.01 = $3/month (1000x reduction!)
```

**Cost:**  
- Database table + sync job
- Data up to 24 hours stale

---

## Comparison: Stateless vs Stateful

| Aspect | Stateless (Current) | Stateful (Future) |
|--------|---------------------|-------------------|
| **Complexity** | Low (no tables) | Medium (1 table + job) |
| **API Calls** | On-demand (cached) | Batch (1x daily) |
| **Freshness** | Always current | Up to 24h stale |
| **Resilience** | API dependency | Works during outages |
| **Audit Trail** | No | Yes |
| **Provider Cost** | Pay per call | 100x lower |
| **Rate Limits** | Can hit limits | Never hits limits |
| **When to Use** | <100 users | >100 users |

---

## Recommended Migration Path

### Phase 1: Start Stateless (Now)
- ✅ Simple, fast to implement
- ✅ Works great for POC / small user base
- ✅ Can always add database later

### Phase 2: Add Database When Needed
**Trigger:** Any of the above issues (rate limits, outages, audit, cost)

**Effort:** 2-3 hours
1. Create 1 table (5 min)
2. Add repository (30 min)
3. Add scheduled job (1 hour)
4. Update service with fallback (1 hour)
5. Test and deploy (30 min)

**Result:**
- Service continues to work
- Users don't notice any change
- Backend is more robust

---

## Example: Adding Database (Minimal Implementation)

### Step 1: Create Table
```sql
CREATE TABLE corporate_action (
    id INT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_date DATE NOT NULL,
    details JSON NOT NULL,
    provider VARCHAR(50) NOT NULL,
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY (symbol, action_date, action_type),
    INDEX (symbol, action_date)
);
```

### Step 2: Add Entity & Repository
```java
@Entity
@Data
public class CorporateAction {
    @Id
    @GeneratedValue
    private Long id;
    private String symbol;
    private String actionType;
    private LocalDate actionDate;
    @Column(columnDefinition = "JSON")
    private String details;
    private String provider;
    private Timestamp fetchedAt;
}

@Repository
public interface CorporateActionRepository extends JpaRepository<CorporateAction, Long> {
    List<CorporateAction> findBySymbolAndActionDateBetween(
        String symbol, LocalDate from, LocalDate to
    );
}
```

### Step 3: Update Service
```java
@Service
public class CorporateActionService {
    private final CorporateActionProvider apiProvider;
    private final CorporateActionRepository dbRepository;  // NEW
    
    public List<Dividend> getDividends(String symbol, LocalDate from, LocalDate to) {
        // Try database first (fast)
        List<CorporateAction> cached = dbRepository.findBySymbol(symbol, from, to);
        if (!cached.isEmpty() && isFresh(cached)) {
            return toDividends(cached);
        }
        
        // Fallback to API (fresh but slower)
        try {
            List<Dividend> fresh = apiProvider.getDividends(symbol, from, to);
            // Save to database for next time
            dbRepository.saveAll(toEntities(fresh));
            return fresh;
        } catch (ApiException e) {
            // If API fails, use stale database data
            log.warn("API failed, using stale data");
            return toDividends(cached);
        }
    }
}
```

### Step 4: Add Scheduled Job
```java
@Component
public class CorporateActionSyncJob {
    
    @Scheduled(cron = "0 0 1 * * *")  // 1 AM daily
    public void syncAllSymbols() {
        Set<String> symbols = portfolioService.getAllSymbols();
        
        for (String symbol : symbols) {
            try {
                List<CorporateAction> actions = apiProvider.getAll(symbol);
                repository.saveAll(actions);
                Thread.sleep(1100);  // Respect rate limits
            } catch (Exception e) {
                log.error("Failed to sync {}: {}", symbol, e.getMessage());
            }
        }
    }
}
```

**Done!** Database is now your primary cache with API as freshness source.

---

## Decision Framework

**Use Stateless (Current) If:**
- ✅ <100 concurrent users
- ✅ Occasional P&L calculations (not real-time)
- ✅ Free tier API limits sufficient
- ✅ Provider uptime acceptable (99%+)
- ✅ No audit requirements

**Add Database (Future) If:**
- ⚠️ >100 concurrent users
- ⚠️ Hitting rate limits frequently
- ⚠️ Provider outages causing user issues
- ⚠️ API costs becoming significant
- ⚠️ Audit trail required for compliance

---

## Recommendation

**Start with stateless.** It's simple, works great for early stage, and you can always add the database later if needed. The migration is straightforward (2-3 hours) and doesn't disrupt existing functionality.

**Monitor these metrics:**
1. API call volume (Finnhub dashboard)
2. Rate limit errors (429 responses)
3. Provider uptime (circuit breaker metrics)
4. API costs (if on paid tier)

**Add database when:** Any metric becomes problematic.

---

**Document Version:** 1.0  
**Last Updated:** June 19, 2026  
**Related:** [corporate-actions/PROGRESS.md](corporate-actions/PROGRESS.md), [../spec/phase-0-corporate-actions/01-corporate-actions-support.md](../spec/phase-0-corporate-actions/01-corporate-actions-support.md)
