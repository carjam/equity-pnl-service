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

Badges in README. All green as of June 21, 2026:

- **CI** — Maven Test + Docker → GHCR
- **OWASP Dependency Check** — separate workflow (~5 min with warm cache)

If OWASP fails with “NVD cache missing”, run **OWASP NVD Cache Refresh** once (workflow_dispatch).

## Secrets

| Secret | Purpose |
|--------|---------|
| `NVD_API_KEY` | OWASP NVD API (configured) |
| `STAGING_*` | Optional VPS deploy only |

Pin for reviewers: [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)
