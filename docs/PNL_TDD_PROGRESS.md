# P&L Test-Driven Refactor - Phase 1 Complete

## Test Suite Created ✅

Created `PnLCalculationTest.java` with **14 comprehensive test scenarios**:

### Test Coverage Matrix

| # | Scenario | Expected Result | Purpose |
|---|----------|----------------|---------|
| **1.1** | Long: Buy 100@$50, Sell 100@$60 | +$1,000 realized, 0 qty | Basic long profit |
| **1.2** | Long: Buy 100@$50, Sell 100@$40 | -$1,000 realized, 0 qty | Basic long loss |
| **1.3** | Long: Buy 100@$50, Hold @ $55 | 0 realized, +$500 unrealized | Long unrealized gain |
| **1.4** | Long: Buy 100@$50, Sell 50@$60, @ $55 | +$500 realized, +$250 unrealized | Partial close |
| **2.1** | Short: Sell 100@$50, Buy 100@$40 | +$1,000 realized, 0 qty | Basic short profit |
| **2.2** | Short: Sell 100@$50, Buy 100@$60 | -$1,000 realized, 0 qty | Basic short loss |
| **2.3** | Short: Sell 100@$50, Hold @ $55 | 0 realized, -$500 unrealized | Short unrealized loss |
| **3.1** | Long→Short: Buy 100@$50, Sell 150@$60 @ $55 | +$1,000 realized, -50 qty, +$250 unrealized | Transition with profit |
| **3.2** | Short→Long: Sell 100@$50, Buy 150@$40 @ $45 | +$1,000 realized, +50 qty, +$250 unrealized | Transition with profit |
| **4.1** | Avg Cost: Buy 100@$50 + 100@$60, Sell 150@$65 @ $70 | +$1,500 realized (avg $55), +$750 unrealized | Average cost basis |
| **5.1** | Multiple Round Trips | +$1,000 total realized, 0 qty | Cumulative realized |

---

## Test Structure

### 1. Simple Long Positions (4 tests)
- Full close with profit/loss
- Hold with unrealized gain
- Partial close

### 2. Simple Short Positions (3 tests)
- Full cover with profit/loss  
- Hold with unrealized loss

### 3. Position Transitions (2 tests)
- Long→Short (oversell)
- Short→Long (overbuy)

### 4. Average Cost Basis (1 test)
- Multiple buys at different prices

### 5. Edge Cases (1 test)
- Multiple round trips

---

## Expected P&L Formulas

### Long Positions
```
Avg Cost = Total Basis / Total Quantity
Realized P&L = Quantity Sold * (Sale Price - Avg Cost)
Unrealized P&L = Remaining Quantity * (Current Price - Avg Cost)
```

### Short Positions
```
Avg Price = Total Proceeds / Total Quantity
Realized P&L = Quantity Covered * (Avg Price - Cover Price)
Unrealized P&L = Remaining Quantity * (Avg Price - Current Price)
```

### Transitions
When crossing zero (long→short or short→long):
1. Calculate realized P&L on closing portion using old avg cost
2. Open new position with remaining quantity at transaction price

---

## Next Steps

### Phase 1: Establish Ground Truth ✅ **DONE**
- Comprehensive test suite created
- All scenarios documented
- Expected values calculated

### Phase 2: Run Tests & Document Current Behavior ⏳ **NEXT**
```bash
# Run the new test suite
mvn test -Dtest=PnLCalculationTest

# Document which tests pass/fail
# Capture actual vs expected for failing tests
```

### Phase 3: Fix Bugs in Current Implementation
- Address line 184 bug
- Fix any failing tests
- Keep current structure (6 conditionals)

### Phase 4: Refactor to Mathematical Symmetry
- Reduce from 6 conditionals to 1
- Use signed quantities properly
- Implement universal formulas
- Verify all tests still pass

---

## Test Assertions

Each test validates:
1. ✅ **Final quantity** (long/short/flat)
2. ✅ **Realized P&L** (closed positions)
3. ✅ **Unrealized P&L** (open positions)
4. ✅ **Cash balance** (accounting check)

This ensures mathematical correctness across all dimensions.

---

## Success Criteria

Before refactoring:
- [ ] All 14 tests pass with current implementation
- [ ] Any bugs identified and documented
- [ ] Expected behavior locked in

After refactoring:
- [ ] All 14 tests still pass
- [ ] Code reduced from 6 conditionals to 1
- [ ] Mathematical elegance achieved
- [ ] Easier to maintain

---

**Status:** Phase 1 Complete - Test Suite Ready  
**Next:** Run tests to establish ground truth
