# Running Tests

**258 tests** · H2 in-memory · no MySQL required for the suite.

```powershell
.\mvnw.cmd test
```

Requires **JDK 21** (`JAVA_HOME` pointed at a 21 install). See troubleshooting below if you see `release version 21 not supported`.

---

## Common commands

```powershell
# Full suite
.\mvnw.cmd test

# Single class
.\mvnw.cmd test -Dtest=PnLCalculationTest

# Corporate actions / fixtures
.\mvnw.cmd test -Dtest=RealWorldCorporateActionsPnLEndToEndTest,CorporateActionsPnLEndToEndTest

# Pattern
.\mvnw.cmd test -Dtest=*ServiceTest
```

---

## Run the app (dev + Swagger)

**PowerShell:**
```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger: http://localhost:8080/swagger-ui.html (needs MySQL + env vars — see [../README.md](../README.md)).

---

## Test layout

```
src/test/java/com/companyx/equity/
├── IntegrationTest.java              # Auth E2E, health, OpenAPI, correlation ID
├── controller/                       # MockMvc — auth, transactions, corporate actions, finhub
├── service/
│   ├── PnLCalculationTest.java       # Core P&L math (11 scenarios)
│   ├── PnLServiceTest.java
│   ├── PnLServiceBugFixTest.java
│   ├── CorporateActionsPnLEndToEndTest.java
│   ├── RealWorldCorporateActionsPnLEndToEndTest.java  # FOX, EBAY, FB, TWTR
│   └── …                             # Dividend, merger, spinoff, symbol services
├── provider/                         # Finnhub composite, fixtures
├── repository/                       # JPA + Finhub retry (MockWebServer)
├── security/                         # JWT
├── model/corporateaction/              # Domain model unit tests
└── …
```

Profile: `@ActiveProfiles("test")` · config: `src/test/resources/application-test.properties`

---

## Expected output

```
[INFO] Tests run: 258, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Coverage snapshot: [TEST_COVERAGE_REPORT.md](TEST_COVERAGE_REPORT.md)

---

## CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs `./mvnw test -B` on every push/PR to `main`, plus OWASP and Docker build.

**OWASP:** dependency-check **12.2.2** with shared NVD cache. Add repo secret **`NVD_API_KEY`** ([request key](https://nvd.nist.gov/developers/request-an-api-key)). First NVD sync is slow; run **Actions → OWASP NVD Cache Refresh** to warm cache, then CI OWASP job finishes in ~2–5 min.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `release version 21 not supported` | Set `JAVA_HOME` to JDK 21; use `.\mvnw.cmd` |
| Git Bash `mvnw` not found / CRLF | Use PowerShell + `mvnw.cmd`, or `git checkout mvnw` |
| H2 / Flyway errors | Confirm `@ActiveProfiles("test")` on integration tests |
| Port 8080 in use | Stop other app before `spring-boot:run` |

---

## Related

- [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md) — demo paths
- [FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md) — load tests, JaCoCo gate (deferred)
- [archive/TEST_DOCUMENTATION.md](archive/TEST_DOCUMENTATION.md) — historical test inventory

---

*Last updated: June 20, 2026*
