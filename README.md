# Equity P&L Service

**A production-ready Spring Boot service for calculating profit and loss (P&L) on equity positions with real-time market data integration.**

> 💼 **Portfolio Project:** This is a demonstration of enterprise-grade financial software engineering. Code available for review only. See [LICENSE](LICENSE) for terms.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Test Coverage](https://img.shields.io/badge/Coverage-~95%25-brightgreen.svg)](docs/TEST_COVERAGE_REPORT.md)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

## 📚 Documentation

All project documentation is in the [`docs/`](docs/) directory:

| Document | Description |
|----------|-------------|
| **[Documentation Index](docs/README.md)** | Complete documentation overview |
| **[Portfolio Demo Guide](docs/PORTFOLIO_DEMO.md)** | Demo script for interviews and reviewers |
| **[Project Status](docs/PROJECT_STATUS.md)** | Current status and next steps |
| **[Corporate Actions](docs/corporate-actions/README.md)** | Phase 0 implementation docs |
| **[Running Tests](docs/RUNNING_TESTS.md)** | Quick start guide for testing |
| **[Bug Report](docs/BUG_REPORT.md)** | Known issues and fixes |
| **[Test Coverage](docs/TEST_COVERAGE_REPORT.md)** | Detailed test analysis |
| **[Timezone Config](docs/TIMEZONE_CONFIGURATION.md)** | Configure application timezone |
| **[Notice](docs/NOTICE.md)** | Portfolio and licensing notice |

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
-- Create database and user
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
Authenticate and receive JWT token.

#### Transactions
```
GET  /api/v1/transactions             # Get all transactions
GET  /api/v1/transactions/{id}        # Get specific transaction
GET  /api/v1/pnl?from={date}&to={date}  # Get P&L for date range
```

#### Corporate Actions
```
GET  /api/v1/corporate-actions?symbol={}&from={}&to={}
GET  /api/v1/corporate-actions/dividends|splits|mergers|spinoffs|symbol-changes|delistings
GET  /api/v1/corporate-actions/providers
GET  /api/v1/pnl/total-return?symbol={}&from={}&to={}
```

#### Market Data (Finhub Integration)
```
GET  /Mark/{symbol}                          # Current quote
GET  /Candle/{symbol}?from={date}&to={date}  # Historical candles
```

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT |
| Database | MySQL 8.0 + Flyway |
| Caching | Caffeine |
| Resilience | Resilience4j (Circuit Breaker, Retry, Bulkhead) |
| External API | Finnhub Market Data |
| Testing | JUnit 5, Mockito, H2 |

### Features

✅ **Implemented:**
- Real-time P&L calculation with **corporate actions** (splits, dividends, mergers, spinoffs, symbol changes)
- Long and short position support
- JWT authentication
- Market data integration (Finnhub)
- Circuit breaker pattern
- Comprehensive test suite (255 tests, all passing)
- Configurable timezone support
- Input validation
- Error handling

**Corporate actions:** See [docs/corporate-actions/README.md](docs/corporate-actions/README.md). Production M&A data optional (fixtures for dev/test).

🚧 **Future Enhancements:**
- Event-driven architecture with message queues
- Real-time transaction ingestion
- Advanced caching strategies
- Tax implications (short-term/long-term gains)
- Margin calculations
- ITD/YTD/MTD snapshots
- Transaction costs
- Fractional shares
- Attribution analysis (beta/alpha)
- FIFO/LIFO lot tracking

## 🧪 Testing

### Test Coverage

- **255 test cases** — full suite green (June 20, 2026)
- Key suites: `PnLCalculationTest`, `CorporateActionsPnLEndToEndTest`, `RealWorldCorporateActionsPnLEndToEndTest`, controller and repository tests

```bash
# Windows
.\mvnw.cmd test

# Corporate actions only
.\mvnw.cmd test -Dtest=*CorporateAction*
```

See [docs/TEST_COVERAGE_REPORT.md](docs/TEST_COVERAGE_REPORT.md) for detailed analysis.

## 🐛 Known Issues

Core bugs from the initial review are resolved. Remaining work is optional enhancement, not blocking merge:

- **Production M&A data feed** — Phase 2 logic is implemented; live secondary provider (paid API or SEC EDGAR) deferred until needed. Dev/test fixtures cover FOX→DIS, EBAY→PYPL, FB→META, TWTR cash merger.
- **CI/CD** — GitHub Actions not yet configured.

See [docs/BUG_REPORT.md](docs/BUG_REPORT.md) and [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for details.

## 📦 Project Structure

```
equity-pnl-service/
├── docs/                          # 📚 All documentation
│   ├── README.md                  # Documentation index
│   ├── BUG_REPORT.md             # Bug analysis & fixes
│   ├── RUNNING_TESTS.md          # Testing guide
│   ├── TEST_COVERAGE_REPORT.md   # Coverage analysis
│   └── TIMEZONE_CONFIGURATION.md # Timezone setup
├── src/
│   ├── main/
│   │   ├── java/com/companyx/equity/
│   │   │   ├── config/           # Configuration classes
│   │   │   ├── controller/       # REST controllers
│   │   │   ├── dto/              # Data transfer objects
│   │   │   ├── error/            # Exception handling
│   │   │   ├── model/            # JPA entities
│   │   │   ├── repository/       # Data access layer
│   │   │   ├── security/         # JWT & authentication
│   │   │   ├── service/          # Business logic
│   │   │   └── utility/          # Helper classes
│   │   └── resources/
│   │       ├── application*.properties
│   │       └── db/migration/     # Flyway migrations
│   └── test/                     # 🧪 170+ comprehensive tests
├── spec/                         # Technical specifications
└── .env.template                 # Environment template
```

## 🔒 Security

- JWT-based authentication with BCrypt password encryption
- Spring Security integration
- Input validation on all endpoints
- SQL injection protection (JPA/Hibernate)
- Secured actuator endpoints
- HTTPS recommended for production

## 📊 Monitoring & Health

### Actuator Endpoints

Development:
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/info
```

Production (secured):
- Health checks via `/actuator/health`
- Metrics available for Prometheus integration
- Circuit breaker status monitoring

## 🤝 Contributing

1. Review [docs/BUG_REPORT.md](docs/BUG_REPORT.md) for known issues
2. Run tests: `mvn test` (must pass with >90% coverage)
3. Follow existing code patterns and conventions
4. Update documentation for any changes
5. Add tests for new functionality

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

### Documentation
- [Complete Documentation Index](docs/README.md)
- [Bug Report & Known Issues](docs/BUG_REPORT.md)
- [Testing Guide](docs/RUNNING_TESTS.md)
- [Timezone Configuration](docs/TIMEZONE_CONFIGURATION.md)

### External Resources
- [Finnhub API Documentation](https://finnhub.io/docs/api)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Resilience4j Documentation](https://resilience4j.readme.io/)

---

**Latest Update:** June 20, 2026  
**Test Coverage:** ~95% (255 tests, all passing)  
**Status:** Merge-ready on `feature/bug-fixes-and-retry-strategy`

*For detailed documentation, see the [`docs/`](docs/) directory.*
