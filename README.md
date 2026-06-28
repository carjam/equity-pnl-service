# Equity P&L Service

**A production-ready Spring Boot service for calculating profit and loss (P&L) on equity positions with real-time market data integration.**

> 💼 **Portfolio Project:** This is a demonstration of enterprise-grade financial software engineering. Code available for review only. See [LICENSE](LICENSE) for terms.

[![CI](https://github.com/carjam/equity-pnl-service/actions/workflows/ci.yml/badge.svg)](https://github.com/carjam/equity-pnl-service/actions/workflows/ci.yml)
[![OWASP](https://github.com/carjam/equity-pnl-service/actions/workflows/owasp.yml/badge.svg)](https://github.com/carjam/equity-pnl-service/actions/workflows/owasp.yml)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/Tests-307-brightgreen.svg)](docs/RUNNING_TESTS.md)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

> **Reviewers:** start with **[Portfolio Demo Guide](docs/PORTFOLIO_DEMO.md)** (tests, Swagger, Docker smoke script).

## 📚 Documentation

| Document | Description |
|----------|-------------|
| **[Documentation Index](docs/README.md)** | All docs |
| **[Portfolio Demo](docs/PORTFOLIO_DEMO.md)** | Demo script for reviewers |
| **[Project Status](docs/PROJECT_STATUS.md)** | Current status |
| **[Running Tests](docs/RUNNING_TESTS.md)** | `.\mvnw.cmd test` |
| **[Corporate Actions](docs/corporate-actions/README.md)** | Phase 0 summary |
| **[Future Enhancements](docs/FUTURE_ENHANCEMENTS.md)** | Deferred work |

## 🚀 Quick Start

### Prerequisites
- **Java 21** (required — JDK 17 cannot compile this project)
- **Maven 3.6+**
- **MySQL 8.0+** (or H2 for testing)

### Running Tests
```bash
mvn test
```
See [docs/RUNNING_TESTS.md](docs/RUNNING_TESTS.md) for detailed instructions.

### Running the Application

**Development Mode:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**API docs (dev):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) — authenticate via **Authentication → POST /api/v1/auth/login**, then **Authorize** with `Bearer <token>`.

**Production Mode:**
```bash
java -jar target/equity-pnl-service.jar --spring.profiles.active=prod
```

**Docker Compose:**
```bash
docker-compose up
```

## ⚙️ Configuration

### Environment Setup

Create a `.env` file (see `.env.template` for reference):

```bash
# Database
DB_URL=jdbc:mysql://localhost:3306/equity_pnl
DB_USERNAME=your_username
DB_PASSWORD=your_password

# External APIs
FINHUB_URL=https://finnhub.io/api/v1
FINHUB_KEY=your_finhub_api_key

# Security
JWT_SECRET=your-256-bit-secret-key
JWT_EXPIRATION=86400000

# Timezone (optional, defaults to UTC)
APP_TIMEZONE=America/Los_Angeles
```

### Database Setup

```sql
CREATE USER 'your_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON *.* TO 'your_user'@'localhost';
FLUSH PRIVILEGES;
CREATE DATABASE equity_pnl;
```

Flyway migrations run automatically on startup.

## 🏗️ Architecture

### REST API Endpoints

#### Authentication
```
POST /api/v1/auth/login
```

#### Transactions & P&L
```
GET  /api/v1/transactions                          # All transactions (scoped to authenticated user)
GET  /api/v1/transactions/{id}                     # Specific transaction
GET  /api/v1/pnl?from={date}&to={date}             # P&L with AVCO methodology disclosure
GET  /api/v1/pnl/total-return?symbol={}&from={}&to={}  # HPR, annualized return, income breakdown
GET  /api/v1/pnl/tax-lots?symbol={}&from={}&to={}  # FIFO lots, STCG/LTCG, wash-sale flags
```

#### Corporate Actions
```
GET  /api/v1/corporate-actions?symbol={}&from={}&to={}
GET  /api/v1/corporate-actions/dividends|splits|mergers|spinoffs|symbol-changes|delistings
GET  /api/v1/corporate-actions/providers
```

#### Market Data (Finnhub Integration)
```
GET  /Mark/{symbol}                          # Current quote
GET  /Candle/{symbol}?from={date}&to={date}  # Historical candles
```

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.5.15 |
| Security | Spring Security + JWT |
| Database | MySQL 8.0 + Flyway |
| Caching | Caffeine |
| Resilience | Resilience4j (Circuit Breaker, Retry, Bulkhead) |
| External API | Finnhub Market Data |
| Testing | JUnit 5, Mockito, H2 |

### Features

✅ **Implemented:**
- Real-time P&L with **corporate actions**: splits, dividends, mergers, spinoffs, symbol changes, delistings
- **Fractional shares** — `BigDecimal(20,8)` throughout; 3:2 splits, stock dividends, fractional purchases all preserved
- **Average Cost (AVCO)** basis methodology with explicit disclosure in every `/pnl` response (IRC §1012 disclaimer)
- **Return of capital** distributions reduce cost basis; excess ROC recognized as realized gain
- **Delisting with cash consideration** — buyout/going-private proceeds computed correctly (not always treated as total loss)
- **Qualified dividend flag** (`Boolean qualified`) on every dividend; `GET /pnl/total-return` splits income accordingly
- **FIFO tax-lot reporting** — `GET /pnl/tax-lots` returns closed lots with STCG/LTCG classification (IRC §1222)
- **Wash-sale detection** — IRC §1091 ±30-day window flagged with disallowed loss amount
- **HPR & annualized return** — `GET /pnl/total-return` returns holding period return and geometric annualized return
- **Dividend ex-date semantics** — CFA-correct holder-of-record quantity used at each ex-date (not period-end quantity)
- Long and short position support
- JWT authentication with per-user data isolation
- Finnhub market data integration with circuit breaker + retry
- 307 automated tests, all passing

For deferred work see [docs/FUTURE_ENHANCEMENTS.md](docs/FUTURE_ENHANCEMENTS.md).

## 🧪 Testing

**307 tests** — full suite green.

```bash
mvn test
```

See [docs/RUNNING_TESTS.md](docs/RUNNING_TESTS.md) for details.

## 🔒 Security

- JWT-based authentication with BCrypt password encryption
- Spring Security integration
- Per-user data isolation on all transaction and P&L queries
- Input validation on all endpoints
- SQL injection protection (JPA/Hibernate)
- Secured actuator endpoints
- HTTPS recommended for production

## 📊 Monitoring & Health

**Actuator Endpoints (dev):**
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/info
```

Production: health checks via `/actuator/health`, metrics for Prometheus integration, circuit breaker status monitoring.

## 📦 Project Structure

```
equity-pnl-service/
├── docs/                   # Start: PORTFOLIO_DEMO.md
├── postman/
├── .github/workflows/      # CI: test, OWASP, Docker
├── src/main · src/test/    # 307 tests
├── spec/CHECKLIST.md
├── Dockerfile
└── docker-compose.staging.yml
```

## 📄 License

**Proprietary / All Rights Reserved**

This code is available for **viewing and portfolio evaluation only**.

You may:
- ✅ View the code on GitHub
- ✅ Review it for technical assessment
- ✅ Reference it in hiring discussions

You may NOT:
- ❌ Use this code in any project
- ❌ Copy or modify this code
- ❌ Distribute this code

For commercial licensing inquiries, please contact the author.

See [LICENSE](LICENSE) for full terms.

## 📞 Support & Resources

- [Complete Documentation Index](docs/README.md)
- [Portfolio Demo Guide](docs/PORTFOLIO_DEMO.md)
- [Project Status](docs/PROJECT_STATUS.md)
- [Testing Guide](docs/RUNNING_TESTS.md)
- [Finnhub API Documentation](https://finnhub.io/docs/api)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Resilience4j Documentation](https://resilience4j.readme.io/)

---

**Last updated:** June 27, 2026 · **Tests:** 307 passing on `main`
