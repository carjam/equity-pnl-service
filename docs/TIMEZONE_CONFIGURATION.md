# Timezone Configuration

> **Optional reference.** Production default is UTC (`application.timezone.id`). Skip unless you need non-UTC date display.

## Overview

The application now supports configurable timezone settings at the application level, allowing you to set the timezone based on your deployment environment without code changes.

## Configuration

### Application Properties

Add the following to your `application.properties`:

```properties
# Timezone Configuration
application.timezone.id=UTC
```

### Environment-Specific Configuration

#### Development (application-dev.properties)
```properties
# Use local timezone for development
application.timezone.id=America/Los_Angeles
```

#### Production (application-prod.properties)
```properties
# UTC recommended for production (or use environment variable)
application.timezone.id=${APP_TIMEZONE:UTC}
```

#### Testing (application-test.properties)
```properties
# UTC for consistent test results
application.timezone.id=UTC
```

### Environment Variable

You can also set the timezone using an environment variable:

```bash
# Linux/Mac
export APP_TIMEZONE=America/New_York

# Windows
set APP_TIMEZONE=America/New_York

# Docker
docker run -e APP_TIMEZONE=America/Los_Angeles ...
```

## Supported Timezone IDs

Use standard Java timezone IDs. Common examples:

### North America
- `UTC` - Coordinated Universal Time (recommended for production)
- `America/New_York` - Eastern Time (ET)
- `America/Chicago` - Central Time (CT)
- `America/Denver` - Mountain Time (MT)
- `America/Los_Angeles` - Pacific Time (PT)
- `America/Phoenix` - Arizona (no DST)

### Europe
- `Europe/London` - British Time
- `Europe/Paris` - Central European Time
- `Europe/Berlin` - Central European Time
- `Europe/Moscow` - Moscow Time

### Asia
- `Asia/Tokyo` - Japan Time
- `Asia/Shanghai` - China Time
- `Asia/Singapore` - Singapore Time
- `Asia/Dubai` - Gulf Time
- `Asia/Kolkata` - India Time

### Australia
- `Australia/Sydney` - Eastern Australia
- `Australia/Melbourne` - Eastern Australia
- `Australia/Perth` - Western Australia

For a complete list, see: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TimeZone.html

## Usage in Code

### Using TimeZoneConfig (Recommended)

Inject the configuration in your Spring components:

```java
@Service
public class MyService {
    
    private final TimeZoneConfig timeZoneConfig;
    
    public MyService(TimeZoneConfig timeZoneConfig) {
        this.timeZoneConfig = timeZoneConfig;
    }
    
    public String formatDate(Long epochSeconds) {
        return DateUtils.stringFromEpoch(epochSeconds, timeZoneConfig.getTimeZone());
    }
}
```

### Using DateUtils Directly

```java
// With TimeZone object
TimeZone tz = TimeZone.getTimeZone("America/Los_Angeles");
String formatted = DateUtils.stringFromEpoch(epochSeconds, tz);

// With timezone ID string
String formatted = DateUtils.stringFromEpoch(epochSeconds, "America/New_York");

// GMT (UTC)
String formatted = DateUtils.stringFromEpochGMT(epochSeconds);

// Pacific Time (deprecated - use configurable timezone instead)
String formatted = DateUtils.stringFromEpochPT(epochSeconds);
```

## Best Practices

### Production Deployments

1. **Use UTC**: Recommended for all production systems
   ```properties
   application.timezone.id=UTC
   ```
   - Avoids DST complications
   - Easier multi-region deployments
   - Standard in financial systems

2. **Use Environment Variables**: For deployment flexibility
   ```properties
   application.timezone.id=${APP_TIMEZONE:UTC}
   ```

3. **Document Timezone**: Include timezone in API responses
   ```json
   {
     "timestamp": "2024-01-01T00:00:00Z",
     "timezone": "UTC"
   }
   ```

### Development

1. **Local Timezone**: Use your local timezone for easier debugging
   ```properties
   application.timezone.id=America/Los_Angeles
   ```

2. **Match Production**: Or match production timezone to catch issues early
   ```properties
   application.timezone.id=UTC
   ```

### Testing

1. **Always Use UTC**: For consistent, reproducible tests
   ```properties
   application.timezone.id=UTC
   ```

2. **Test DST Transitions**: Add tests for DST boundary dates
   ```java
   // Test winter (PST) vs summer (PDT)
   Long winterDate = 1704067200L; // Jan 1, 2024
   Long summerDate = 1719792000L; // July 1, 2024
   ```

## Migration Guide

### From Hardcoded Timezone

**Before:**
```java
TimeZone PT = TimeZone.getTimeZone("America/Los_Angeles");
sdf.setTimeZone(PT);
```

**After:**
```java
@Autowired
private TimeZoneConfig timeZoneConfig;

TimeZone tz = timeZoneConfig.getTimeZone();
sdf.setTimeZone(tz);
```

### From Static Methods

**Before:**
```java
String result = DateUtils.stringFromEpochPT(epochSeconds);
```

**After (with configuration):**
```java
@Autowired
private TimeZoneConfig timeZoneConfig;

String result = DateUtils.stringFromEpoch(epochSeconds, timeZoneConfig.getTimeZone());
```

**After (without configuration):**
```java
String result = DateUtils.stringFromEpoch(epochSeconds, "America/Los_Angeles");
```

## Troubleshooting

### Invalid Timezone ID

If you specify an invalid timezone ID, Java defaults to GMT:

```properties
# Invalid
application.timezone.id=Invalid/Timezone
# Results in: GMT
```

**Solution**: Use a valid timezone ID from the list above.

### DST Issues

If dates are wrong by 1 hour during DST transitions:

1. **Check timezone ID**: Use region-based IDs, not GMT offsets
   - ✅ Good: `America/Los_Angeles` (handles PST/PDT)
   - ❌ Bad: `GMT-7` (always -7, ignores PST)

2. **Verify date range**: Ensure you're testing both winter and summer dates

### Tests Failing in Different Timezones

If tests fail when run in different regions:

1. **Use UTC in tests**: Set `application.timezone.id=UTC` in test config
2. **Use absolute assertions**: Don't assume timezone in assertions
   ```java
   // Bad
   assertTrue(result.contains("17:00"));
   
   // Good
   assertNotNull(result);
   assertTrue(result.contains("2024-01-01"));
   ```

## API Impact

### REST Endpoints

Consider adding timezone information to responses:

```java
@GetMapping("/transactions")
public ResponseEntity<TransactionResponse> getTransactions() {
    TransactionResponse response = new TransactionResponse();
    response.setTimezone(timeZoneConfig.getId());
    response.setTransactions(transactions);
    return ResponseEntity.ok(response);
}
```

### Date Serialization

Configure Jackson to use the configured timezone:

```java
@Configuration
public class JacksonConfig {
    
    @Bean
    public ObjectMapper objectMapper(TimeZoneConfig timeZoneConfig) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setTimeZone(timeZoneConfig.getTimeZone());
        return mapper;
    }
}
```

## Examples

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: equity-pnl-service
spec:
  template:
    spec:
      containers:
      - name: app
        image: equity-pnl-service:latest
        env:
        - name: APP_TIMEZONE
          value: "America/New_York"
```

### Docker Compose

```yaml
services:
  app:
    image: equity-pnl-service:latest
    environment:
      - APP_TIMEZONE=America/Los_Angeles
```

### Systemd Service

```ini
[Service]
Environment="APP_TIMEZONE=America/Chicago"
ExecStart=/usr/bin/java -jar equity-pnl-service.jar
```

---

*For questions, see [PROJECT_STATUS.md](PROJECT_STATUS.md) or [RUNNING_TESTS.md](RUNNING_TESTS.md).*
