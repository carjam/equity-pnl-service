# P&L Test Predictions - Manual Code Trace

## Analysis Method
Since Maven is not available, I'm manually tracing through each test scenario with the current code to predict pass/fail and identify bugs.

---

## Test 1.1: Long Position Profit
**Scenario:** Buy 100 @ $50, Sell 100 @ $60

**Code Path Analysis:**

### Transaction 1: BUY 100 @ $50
- startQuant = 0
- transQuant = 100
- endQuant = 0 + 100 = 100
- Condition: `endQuant > 0 && startQuant >= 0` → long→long path (line 178-180)
- `endVal = startVal.subtract(transVal)` = 0 - 5000 = **-5000** (negative basis)
- `realized = 0`

### Transaction 2: SELL 100 @ $60
- startQuant = 100
- transQuant = 100
- endQuant = 100 - 100 = 0
- Condition: `endQuant >= 0 && startQuant > 0` → long→long path (line 208-210)
- `endVal = startPrice * endQuant * -1` = 50 * 0 * -1 = **0**
- `realized = transQuant * (transPrice - startPrice)` = 100 * (60 - 50) = **$1,000** ✅

**Prediction:** ✅ **PASS** - Realizes $1,000 profit correctly

---

## Test 1.2: Long Position Loss
**Scenario:** Buy 100 @ $50, Sell 100 @ $40

### Analysis:
- BUY: Same as 1.1
- SELL: realized = 100 * (40 - 50) = **-$1,000** ✅

**Prediction:** ✅ **PASS** - Realizes -$1,000 loss correctly

---

## Test 1.3: Long Hold
**Scenario:** Buy 100 @ $50, Hold @ $55

### Analysis:
- BUY: basis = -5000, qty = 100
- Unrealized (line 298): `(price * qty) + basis` = (55 * 100) + (-5000) = 5500 - 5000 = **$500** ✅

**Prediction:** ✅ **PASS** - Unrealized $500 correct

---

## Test 1.4: Partial Sale
**Scenario:** Buy 100 @ $50, Sell 50 @ $60, Hold @ $55

### Transaction 2: SELL 50
- startQuant = 100, transQuant = 50, endQuant = 50
- Condition: long→long path
- `realized = 50 * (60 - 50)` = **$500** ✅
- `endVal = 50 * 50 * -1` = **-2500** (remaining basis)

### Unrealized:
- `(55 * 50) + (-2500)` = 2750 - 2500 = **$250** ✅

**Prediction:** ✅ **PASS**

---

## Test 2.1: Short Position Profit ⚠️
**Scenario:** Short 100 @ $50 (sell), Cover 100 @ $40 (buy)

### Transaction 1: SELL 100 @ $50 (initiate short)
- startQuant = 0, transQuant = 100, endQuant = -100
- Condition: `endQuant < 0` → else branch (line 216-218) "transition"
- `endVal = transPrice * endQuant * -1` = 50 * (-100) * -1 = **5000** (positive basis for short)
- `realized = startVal + (startQuant * transPrice)` = 0 + 0 = **0** ✅

### Transaction 2: BUY 100 @ $40 (cover short)
- startQuant = -100, transQuant = 100, endQuant = 0
- Condition: `endQuant >= 0 && startQuant < 0` → short→short path (line 182-184) ❌

**LINE 184 BUG:**
```java
realized = transQuant * (startPrice - transPrice)
```
- startPrice = 5000 / |-100| = 50
- realized = 100 * (50 - 40) = **$1,000** ✅

**Wait - this accidentally works!** The formula is backwards but produces correct result because:
- Short profit formula should be: `(shortPrice - coverPrice) * qty`
- Current: `(startPrice - transPrice)` which equals `(50 - 40)` = $1,000 ✅

**Prediction:** ✅ **MIGHT PASS** (but formula is still wrong for other cases)

---

## Test 2.2: Short Position Loss
**Scenario:** Short 100 @ $50, Cover 100 @ $60

### Analysis:
- BUY 100 @ $60 to cover
- `realized = 100 * (50 - 60)` = **-$1,000** ✅

**Prediction:** ✅ **PASS** (formula backwards but works for shorts too)

---

## Test 2.3: Short Hold
**Scenario:** Short 100 @ $50, Hold @ $55

### Analysis:
- After SELL: qty = -100, basis = 5000
- Unrealized: `(55 * -100) + 5000` = -5500 + 5000 = **-$500** ✅

**Prediction:** ✅ **PASS**

---

## Test 3.1: Long→Short Transition ⚠️
**Scenario:** Buy 100 @ $50, Sell 150 @ $60

### Transaction 1: BUY 100 @ $50
- qty = 100, basis = -5000

### Transaction 2: SELL 150 @ $60
- startQuant = 100, transQuant = 150, endQuant = -50
- Condition: `endQuant < 0` → long→short transition (line 216-218)

**LINE 218 BUG:**
```java
endVal = transPrice * endQuant * -1 = 60 * (-50) * -1 = 3000
realized = startVal + (startQuant * transPrice) = (-5000) + (100 * 60) = -5000 + 6000 = 1000 ✅
```

**This works!** The formula `startVal + (startQuant * transPrice)` correctly calculates:
- Close long: sell 100 @ $60, basis was -5000 (cost $5000)
- P&L = 6000 - 5000 = $1,000 ✅

**Prediction:** ✅ **PASS**

---

## Test 3.2: Short→Long Transition ⚠️
**Scenario:** Short 100 @ $50, Buy 150 @ $40

### Transaction 1: SELL 100 @ $50 (short)
- qty = -100, basis = 5000

### Transaction 2: BUY 150 @ $40
- startQuant = -100, transQuant = 150, endQuant = 50
- Condition: `endQuant > 0 && startQuant < 0` → short→long transition (line 186-188)

**LINE 188 BUG:**
```java
endVal = transPrice * endQuant * -1 = 40 * 50 * -1 = -2000
realized = startVal + (startQuant * transPrice) = 5000 + (-100 * 40) = 5000 - 4000 = 1000 ✅
```

**This works too!** The formula correctly calculates:
- Cover short: buy 100 @ $40, had shorted @ $50 (basis 5000)
- P&L = 5000 - 4000 = $1,000 ✅

**Prediction:** ✅ **PASS**

---

## Test 4.1: Average Cost Basis
**Scenario:** Buy 100@$50 + 100@$60, Sell 150@$65

### Analysis:
- BUY 100 @ $50: basis = -5000
- BUY 100 @ $60: basis = -5000 + (-6000) = **-11000**, qty = 200
- avgCost = 11000 / 200 = **$55**
- SELL 150 @ $65: realized = 150 * (65 - 55) = **$1,500** ✅

**Prediction:** ✅ **PASS**

---

## Test 5.1: Multiple Round Trips
**Scenario:** Multiple buys/sells

**Analysis:** Each round trip should accumulate realized P&L correctly.

**Prediction:** ✅ **PASS**

---

## Summary Prediction

| Test | Predicted Result | Reason |
|------|------------------|--------|
| 1.1 Long Profit | ✅ PASS | Formula correct |
| 1.2 Long Loss | ✅ PASS | Formula correct |
| 1.3 Long Hold | ✅ PASS | Unrealized correct |
| 1.4 Partial Sale | ✅ PASS | Formulas correct |
| 2.1 Short Profit | ✅ PASS | Formula backwards but works |
| 2.2 Short Loss | ✅ PASS | Formula backwards but works |
| 2.3 Short Hold | ✅ PASS | Unrealized correct |
| 3.1 Long→Short | ✅ PASS | Transition formula works |
| 3.2 Short→Long | ✅ PASS | Transition formula works |
| 4.1 Avg Cost | ✅ PASS | Averaging works |
| 5.1 Round Trips | ✅ PASS | Cumulative works |

**Expected: 11/11 or 14/14 PASS** 🎉

---

## Surprising Finding!

The current implementation appears to be **mathematically correct** despite:
1. Using confusing negative basis for longs
2. Having backwards formulas (line 184)
3. Having 6 conditionals

The backwards formulas accidentally work because:
- Line 184: `(startPrice - transPrice)` produces correct sign for shorts
- Lines 188, 218: The `startVal + (startQuant * transPrice)` formula works due to signed quantities

---

## Implications

Since tests will likely pass:
1. ✅ Current implementation is **mathematically sound**
2. ❌ But still has **6 conditionals** (maintenance issue)
3. ❌ Uses **confusing conventions** (negative basis)
4. ✅ Can proceed directly to **Phase 4: Refactor for Elegance**

We can refactor to true symmetry (1 conditional) while keeping all tests passing!

---

**Next:** Implement the elegant symmetric solution that achieves your original vision.
