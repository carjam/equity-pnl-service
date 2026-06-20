# Documentation Index

All essential documentation for the Equity P&L Service.

---

## Start Here

| Document | Description |
|----------|-------------|
| **[PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)** | **Demo script** — tests, Swagger, Docker staging, CI talking points |
| **[PROJECT_STATUS.md](PROJECT_STATUS.md)** | Current status, test summary, next steps |
| **[../README.md](../README.md)** | Project overview, quick start, architecture |

---

## Core Documentation

### Status & Planning
- **[PROJECT_STATUS.md](PROJECT_STATUS.md)** — Current status and next steps
- **[PHASE1_AUDIT.md](PHASE1_AUDIT.md)** — Phase 1 security & stability sign-off
- **[BUG_REPORT.md](BUG_REPORT.md)** — Bug analysis (all core issues resolved)
- **[FUTURE_ENHANCEMENTS.md](FUTURE_ENHANCEMENTS.md)** — Deferred work (DB cache, FIFO, etc.)
- **[../spec/CHECKLIST.md](../spec/CHECKLIST.md)** — Production readiness checklist

### Corporate Actions (Phase 0 — Complete)
- **[corporate-actions/README.md](corporate-actions/README.md)** — Index
- **[corporate-actions/PROGRESS.md](corporate-actions/PROGRESS.md)** — Implementation status
- **[corporate-actions/PLAN.md](corporate-actions/PLAN.md)** — Architecture overview
- **[corporate-actions/PROVIDER_STRATEGY.md](corporate-actions/PROVIDER_STRATEGY.md)** — Data sources (Finnhub, fixtures, paid API, EDGAR)

### Testing & CI
- **[RUNNING_TESTS.md](RUNNING_TESTS.md)** — How to run tests and CI overview
- **[TEST_DOCUMENTATION.md](TEST_DOCUMENTATION.md)** — Test suite structure
- **[TEST_COVERAGE_REPORT.md](TEST_COVERAGE_REPORT.md)** — Coverage metrics

### Deployment & Observability
- **[../docker-compose.staging.yml](../docker-compose.staging.yml)** — Staging stack (app + MySQL + Redis)
- **[../postman/equity-pnl-service.postman_collection.json](../postman/equity-pnl-service.postman_collection.json)** — Postman requests
- **CI:** [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) — test, OWASP, Docker → GHCR
- **Deploy:** [`.github/workflows/deploy-staging.yml`](../.github/workflows/deploy-staging.yml) — manual staging deploy

### Configuration & Legal
- **[TIMEZONE_CONFIGURATION.md](TIMEZONE_CONFIGURATION.md)** — Timezone setup
- **[NOTICE.md](NOTICE.md)** — Portfolio and licensing notice

---

## Quick Reference

### Portfolio / interview demo
1. [PORTFOLIO_DEMO.md](PORTFOLIO_DEMO.md)
2. `.\mvnw.cmd test` (257 tests)
3. Swagger at `/swagger-ui.html` (dev profile)

### New developers
1. [PROJECT_STATUS.md](PROJECT_STATUS.md)
2. [RUNNING_TESTS.md](RUNNING_TESTS.md)
3. [../README.md](../README.md)

### Corporate actions
1. [corporate-actions/PROGRESS.md](corporate-actions/PROGRESS.md)
2. [../spec/phase-0-corporate-actions/01-corporate-actions-support.md](../spec/phase-0-corporate-actions/01-corporate-actions-support.md)

---

## Historical Documentation

Superseded session notes live in **[archive/](archive/)**. Do not use for current status — see [PROJECT_STATUS.md](PROJECT_STATUS.md) instead.

---

*Last Updated: June 20, 2026*
