# Running P&L Tests - Quick Start Guide

## Run the Tests

```bash
# Navigate to project root
cd c:\Users\carja\Workspace\equity-pnl-service

# Run only the P&L calculation tests
mvn test -Dtest=PnLCalculationTest

# Alternative: Run with more detailed output
mvn test -Dtest=PnLCalculationTest -e

# Alternative: Run a specific test if needed
mvn test -Dtest=PnLCalculationTest#testLongPositionProfit
```

---

## What to Look For

### 1. Overall Results
```
Tests run: 14, Failures: X, Errors: Y, Skipped: 0
```

### 2. Individual Test Results

For **PASSING** tests, you'll see:
```
[INFO] Running com.companyx.equity.service.PnLCalculationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

For **FAILING** tests, you'll see:
```
testShortPositionProfit(com.companyx.equity.service.PnLCalculationTest)
  Expected: <1000.0>
  Actual: <-1000.0>
```

---

## Document Results

Create a file with your findings:

### Test Results Template

```markdown
# P&L Test Results - Run 1

Date: [date/time]
Branch: feature/bug-fixes-and-retry-strategy
Commit: 3f1ad05

## Summary
- Total Tests: 14
- Passed: X
- Failed: Y
- Pass Rate: X%

## Individual Results

### ✅ Passing Tests
- [ ] 1.1 Long Profit (Buy 100@$50, Sell 100@$60)
- [ ] 1.2 Long Loss (Buy 100@$50, Sell 100@$40)
- [ ] 1.3 Long Hold (Buy 100@$50, Hold @$55)
- [ ] 1.4 Partial Sale (Buy 100@$50, Sell 50@$60)
- [ ] 2.1 Short Profit (Short 100@$50, Cover @$40)
- [ ] 2.2 Short Loss (Short 100@$50, Cover @$60)
- [ ] 2.3 Short Hold (Short 100@$50, Hold @$55)
- [ ] 3.1 Long→Short Transition
- [ ] 3.2 Short→Long Transition
- [ ] 4.1 Average Cost Basis
- [ ] 5.1 Multiple Round Trips

### ❌ Failing Tests
[List failures with Expected vs Actual]

Example:
**Test 2.1: testShortPositionProfit**
- Expected Realized P&L: $1,000.0
- Actual Realized P&L: $-1,000.0
- Difference: $2,000.0 (sign error)

**Test 3.1: testLongToShortTransition**
- Expected Realized P&L: $1,000.0
- Actual Realized P&L: $500.0
- Difference: $500.0 (incorrect calculation)

## Analysis
[Quick notes on patterns you see]
- All short tests failing? → Line 184 bug confirmed
- Transition tests failing? → Lines 186-188 bug confirmed
- All tests passing? → Current implementation correct, just needs refactoring
```

---

## Quick Analysis Questions

After running tests, answer these:

1. **How many tests passed?** _____ out of 14

2. **Pattern in failures?**
   - [ ] All long tests pass, short tests fail
   - [ ] Simple tests pass, transitions fail
   - [ ] All tests fail
   - [ ] All tests pass

3. **Type of errors?**
   - [ ] Sign errors (negative instead of positive)
   - [ ] Magnitude errors (wrong amount)
   - [ ] Quantity errors (wrong shares)
   - [ ] Compilation/runtime errors

4. **Ready to proceed?**
   - [ ] Yes - I've documented the results
   - [ ] Need help interpreting results

---

## After Running Tests

**If all tests PASS:** 
Great! Current implementation is mathematically correct. We can proceed directly to refactoring for elegance (reducing 6 conditionals to 1).

**If some tests FAIL:**
Perfect - we've identified the bugs. Share the results and I'll:
1. Analyze the failures
2. Identify root causes
3. Propose fixes
4. Then proceed to refactor

---

## Copy/Paste Template for Quick Reply

```
Test Results:
- Passed: X/14
- Failed: Y/14

Failing tests:
- [test name]: Expected [value], Got [value]
- [test name]: Expected [value], Got [value]

Ready for analysis? Yes/No
```

---

**Now run the tests and let me know the results!** 🚀

I'll be ready to analyze whatever we find and proceed to the next phase.
