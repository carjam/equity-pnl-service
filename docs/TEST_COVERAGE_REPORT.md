# Test Coverage Snapshot

**Last updated:** June 20, 2026 · **258 tests** · all passing (`.\mvnw.cmd test`)

For how to run tests, see [RUNNING_TESTS.md](RUNNING_TESTS.md).

---

## Summary

| Metric | Value |
|--------|-------|
| Test classes | ~35 |
| Total tests | 258 |
| Profile | H2 in-memory (`@ActiveProfiles("test")`) |
| CI | `./mvnw test -B` on every push/PR to `main` |

JaCoCo is not configured in `pom.xml`; coverage is estimated **~90–95%** of business logic based on suite breadth (not a formal report).

---

## Coverage by area

| Area | Key test classes |
|------|------------------|
| **P&L core** | `PnLCalculationTest`, `PnLServiceTest`, `PnLServiceBugFixTest` |
| **Corporate actions** | `SplitAdjustmentServiceTest`, `DividendServiceTest`, `MergerServiceTest`, `SpinoffServiceTest`, `CorporateActionServiceComplexTest` |
| **E2E / fixtures** | `CorporateActionsPnLEndToEndTest`, `RealWorldCorporateActionsPnLEndToEndTest`, `PnLServiceCorporateActionsTest` |
| **API** | `TransactionControllerTest`, `CorporateActionControllerTest`, `AuthControllerTest`, `IntegrationTest` |
| **Security** | `JwtUtilTest`, `RestExceptionHandlerTest` |
| **Resilience** | `FinhubRepositoryRetryTest`, `CompositeCorporateActionProviderTest` |
| **Persistence** | `TransactionRepositoryTest`, `UserRepositoryTest` |

---

## Scenarios explicitly verified

- Long/short positions, partial closes, average cost, long↔short transitions
- Stock splits and dividends (AAPL, KO fixtures)
- M&A, spinoffs, symbol changes, delistings (FOX, EBAY, FB, TWTR fixtures)
- JWT auth, validation errors, correlation IDs
- OpenAPI spec available at `/v3/api-docs`

---

## Not covered (deferred)

- Load / performance testing
- Formal JaCoCo gate in CI
- Contract tests against live Finnhub

See [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md).

---

*Historical detailed reports: [archive/TEST_DOCUMENTATION.md](archive/TEST_DOCUMENTATION.md), [archive/TEST_COVERAGE_REPORT-june19.md](archive/TEST_COVERAGE_REPORT-june19.md)*
