# Timezone Configuration

Optional reference. **Default: UTC** (`application.timezone.id=UTC` in `application.properties`).

## Configure

```properties
application.timezone.id=UTC
```

Or via environment:

```bash
APPLICATION_TIMEZONE_ID=America/New_York
```

## Profiles

| Profile | Typical setting |
|---------|-----------------|
| `test` | UTC (fixed in `application-test.properties`) |
| `dev` | Your local zone if desired |
| `staging` / `prod` | UTC recommended |

## Code

`TimeZoneConfig` sets the JVM default from `application.timezone.id` at startup. P&L date boundaries use this zone when parsing query parameters.

## Verify

```powershell
.\mvnw.cmd test -Dtest=TimeZoneConfigTest
```

Historical notes: [archive/TIMEZONE_CHANGES.md](archive/TIMEZONE_CHANGES.md)
