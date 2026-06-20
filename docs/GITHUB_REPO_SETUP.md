# GitHub repo polish

One-time setup: https://github.com/carjam/equity-pnl-service

## About (gear icon)

**Description:**
```
Spring Boot P&L API — corporate actions, JWT, Resilience4j, 258 tests, Docker CI. Portfolio demo — docs/PORTFOLIO_DEMO.md
```

**Topics:**
```
spring-boot
java
fintech
jwt
docker
openapi
resilience4j
portfolio-project
```

## CI

Badge in README. Expect green: **CI** (Maven Test, Docker). **OWASP Dependency Check** is a separate workflow — first NVD sync can take 60–90+ min and is not canceled by new pushes.

## Secrets

| Secret | Purpose |
|--------|---------|
| `NVD_API_KEY` | OWASP NVD API (configured) |
| `STAGING_*` | Optional VPS deploy only |

Pin for reviewers: [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)
