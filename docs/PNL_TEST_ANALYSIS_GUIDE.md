# Running P&L Calculation Tests - Analysis Guide

## Overview

This guide explains how to run the P&L calculation tests and interpret the results to identify bugs in the current implementation.

---

## Running the Tests

```bash
# Run only the P&L calculation tests
mvn test -Dtest=PnLCalculationTest

# Run with verbose output
mvn test -Dtest=PnLCalculationTest -X

# Run a specific test
mvn test -Dtest=PnLCalculationTest#testLongPositionProfit
```

---

## Expected Issues in Current Implementation

Based on the code analysis, we expect these tests to **FAIL**:

### 🔴 Test 2.1: Short Position Profit - **WILL FAIL**
**Line 184 Bug:**
```java
realized = new BigDecimal(transQuant).multiply(startPrice.subtract(transPrice));
```

**Problem:** Sign is backwards!
- When covering short: startPrice = $50, transPrice = $40
- Formula calculates: `100 * (50 - 40) = $1,000` ✅ This accidentally works!
- BUT when price goes up: `100 * (50 - 60) = -$1,000` ❌ Wrong sign!

**Expected in test:** +$1,000 profit  
**Likely actual:** Could be wrong depending on price movement

### 🔴 Test 3.1 & 3.2: Transitions - **MAY FAIL**
**Lines 186-188, 216-218:**
```java
//short -> long transition
realized = startVal.add(new BigDecimal(startQuant).multiply(transPrice));
```

**Problem:** This formula is not transparent:
- Hard to verify if it correctly separates:
  1. Realized P&L from closing old position
  2. Basis for new position

**Expected:** Clear separation of close + open  
**Likely actual:** May produce incorrect realized P&L

### 🟡 Test 4.1: Average Cost Basis - **SHOULD PASS**
The averaging logic looks correct for same-direction adds.

### ✅ Tests 1.1-1.4: Simple Longs - **SHOULD PASS**
Lines 208-210 look mathematically correct for long positions.

---

## Interpreting Test Results

### Scenario 1: All Tests Pass ✅
**Unlikely but possible**

If all tests pass, the current implementation is mathematically correct but:
- Still has 6 conditionals (maintenance issue)
- Uses confusing negative basis convention
- Could be simplified with refactoring

**Action:** Proceed directly to Phase 4 (refactor to symmetry)

---

### Scenario 2: Some Tests Fail ❌
**Most likely**

#### For Each Failing Test:

1. **Capture the failure details:**
   ```
   Test: testShortPositionProfit
   Expected realized: $1,000.0
   Actual realized: $-1,000.0  
   OR
   Actual realized: $500.0
   ```

2. **Document the discrepancy:**
   - What was expected?
   - What was actual?
   - What formula was used?

3. **Trace through the logic:**
   - Which conditional branch was taken? (long→long, short→short, transition)
   - What was `startQuant`? `endQuant`? `transPrice`? `startPrice`?
   - Does the formula match the expected P&L formula?

---

## Debugging Individual Tests

### Step-by-Step Analysis Template

For each failing test:

```markdown
## Test: [Test Name]

### Setup:
- Initial position: [qty @ price]
- Transaction: [BUY/SELL qty @ price]
- Current price: [price]

### Expected:
- Quantity: [expected qty]
- Realized P&L: $[expected]
- Unrealized P&L: $[expected]

### Actual:
- Quantity: [actual qty]
- Realized P&L: $[actual]
- Unrealized P&L: $[actual]

### Analysis:
1. Which code path was taken?
   - [ ] Long → Long (lines 178-180)
   - [ ] Short → Short (lines 182-184)
   - [ ] Transition (lines 186-188)

2. Variable values:
   - startQuant: [value]
   - transQuant: [value]
   - endQuant: [value]
   - startPrice: [value]
   - transPrice: [value]

3. Formula used:
   ```java
   [paste actual formula from code]
   ```

4. Why it's wrong:
   [explanation]

5. Correct formula should be:
   ```
   [mathematical formula]
   ```

### Fix Required:
[describe the fix]
```

---

## Common Issues to Look For

### Issue 1: Sign Errors
**Symptom:** P&L has correct magnitude but wrong sign  
**Cause:** Missing negative, or `subtract` should be `add`  
**Example:** `+$1,000` expected but got `-$1,000`

### Issue 2: Basis Calculation Errors
**Symptom:** P&L magnitude is wrong  
**Cause:** Using wrong price or quantity in calculation  
**Example:** Expected `(60-50)*100 = $1,000` but got `(60-50)*50 = $500`

### Issue 3: Transition Errors  
**Symptom:** Transition tests fail but simple tests pass  
**Cause:** Not properly closing old position before opening new  
**Example:** Expected realized P&L of $1,000 from close + unrealized from new position

### Issue 4: Average Cost Errors
**Symptom:** Tests with multiple buys/sells at different prices fail  
**Cause:** Not properly tracking weighted average cost  
**Example:** Two buys at $50 and $60 should average to $55, not $50

---

## Documentation Requirements

After running tests, create `PNL_TEST_RESULTS.md` with:

### 1. Summary Table
```markdown
| Test | Status | Expected Realized | Actual Realized | Δ |
|------|--------|-------------------|-----------------|---|
| 1.1 Long Profit | ✅ PASS | $1,000 | $1,000 | $0 |
| 1.2 Long Loss | ✅ PASS | -$1,000 | -$1,000 | $0 |
| 2.1 Short Profit | ❌ FAIL | $1,000 | -$1,000 | $2,000 |
...
```

### 2. Detailed Analysis
For each failing test, use the template above.

### 3. Root Cause Summary
```markdown
## Bugs Identified

### Bug 1: Short Position Realized P&L Sign Error (Line 184)
- **Tests affected:** 2.1, 2.2, 3.2
- **Root cause:** Formula calculates (startPrice - transPrice) instead of (transPrice - startPrice)
- **Fix:** Change to `realized = transQuant * (transPrice - startPrice)`

### Bug 2: Transition Logic (Lines 186-188)
[if applicable]
...
```

---

## Next Steps After Analysis

### If 0-2 tests fail:
**Phase 3:** Fix specific bugs → **Phase 4:** Refactor to symmetry

### If 3-6 tests fail:
**Phase 3:** Fix bugs one by one → Re-run tests → **Phase 4:** Refactor

### If 7+ tests fail:
**Consider:** Current implementation may be fundamentally flawed  
**Option:** Skip Phase 3, go straight to Phase 4 (clean rewrite with symmetry)

---

## Safety Checks

Before proceeding to refactor:

- [ ] All tests documented (pass/fail)
- [ ] Root causes identified for failures
- [ ] Fix approach agreed upon
- [ ] Confidence level: High / Medium / Low

**Only proceed to Phase 4 when confidence is HIGH**

---

## Test Run Checklist

- [ ] Tests compiled successfully
- [ ] All 14 tests executed
- [ ] Results captured (pass/fail)
- [ ] Failing tests analyzed
- [ ] Root causes documented
- [ ] `PNL_TEST_RESULTS.md` created
- [ ] Ready for Phase 3 (fix) or Phase 4 (refactor)

---

**Next Step:** Run `mvn test -Dtest=PnLCalculationTest` and document results
