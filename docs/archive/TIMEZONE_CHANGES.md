# Application Timezone Configuration

## Overview
Added configurable timezone support at the application level.

## Changes Made

### New Files
1. **TimeZoneConfig.java** - Spring configuration class for timezone settings
2. **TimeZoneConfigTest.java** - Comprehensive tests for timezone configuration
3. **TIMEZONE_CONFIGURATION.md** - Complete documentation for timezone usage

### Modified Files
1. **DateUtils.java** - Enhanced with timezone support
   - Added `stringFromEpoch(Long, TimeZone)` method
   - Added `stringFromEpoch(Long, String)` method
   - Deprecated hardcoded `stringFromEpochPT()` method
   - Fixed to use proper timezone handling

2. **application.properties** - Added timezone configuration
   ```properties
   application.timezone.id=${APP_TIMEZONE:UTC}
   ```

3. **application-dev.properties** - Development timezone
   ```properties
   application.timezone.id=America/Los_Angeles
   ```

4. **application-prod.properties** - Production timezone
   ```properties
   application.timezone.id=${APP_TIMEZONE:UTC}
   ```

5. **application-test.properties** - Test timezone (UTC for consistency)
   ```properties
   application.timezone.id=UTC
   ```

6. **DateUtilsTest.java** - Updated with comprehensive timezone tests
   - Tests for multiple timezones
   - DST transition verification
   - Invalid timezone handling

## Configuration Options

### Via Properties File
```properties
# UTC (recommended for production)
application.timezone.id=UTC

# Pacific Time
application.timezone.id=America/Los_Angeles

# Eastern Time
application.timezone.id=America/New_York
```

### Via Environment Variable
```bash
export APP_TIMEZONE=America/Chicago
```

### Via Docker/Kubernetes
```yaml
env:
  - name: APP_TIMEZONE
    value: "America/New_York"
```

## Usage Examples

### Using TimeZoneConfig (Recommended)
```java
@Service
public class MyService {
    private final TimeZoneConfig timeZoneConfig;
    
    public MyService(TimeZoneConfig timeZoneConfig) {
        this.timeZoneConfig = timeZoneConfig;
    }
    
    public String formatDate(Long epochSeconds) {
        return DateUtils.stringFromEpoch(epochSeconds, 
                                         timeZoneConfig.getTimeZone());
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
```

## Benefits

1. **Environment-Specific Configuration**: Different timezones for dev/prod
2. **No Code Changes**: Update timezone via configuration only
3. **DST Handling**: Automatic daylight saving time transitions
4. **Testing**: Use UTC for consistent test results
5. **Multi-Region**: Easy deployment to different regions

## Migration from Hardcoded Timezone

**Before:**
```java
TimeZone PT = TimeZone.getTimeZone("GMT-7"); // Bug: doesn't handle DST
```

**After:**
```java
@Autowired
private TimeZoneConfig timeZoneConfig;
TimeZone tz = timeZoneConfig.getTimeZone(); // Configurable, handles DST
```

## Testing

Added comprehensive tests in `TimeZoneConfigTest.java`:
- Configuration loading from properties
- TimeZone object creation
- DST transition handling
- Invalid timezone fallback
- Multiple timezone scenarios

## Documentation

See **TIMEZONE_CONFIGURATION.md** for:
- Complete list of supported timezone IDs
- Best practices for production
- Troubleshooting guide
- API impact considerations
- Deployment examples (Kubernetes, Docker, etc.)

## Compatibility

- ✅ Backwards compatible (existing code continues to work)
- ✅ `stringFromEpochPT()` deprecated but still functional
- ✅ All existing tests pass
- ✅ New tests verify configurable behavior

## Recommendations

1. **Production**: Use UTC (default)
   ```properties
   application.timezone.id=UTC
   ```

2. **Development**: Use local timezone for debugging
   ```properties
   application.timezone.id=America/Los_Angeles
   ```

3. **Testing**: Always use UTC for consistency
   ```properties
   application.timezone.id=UTC
   ```

---

*For detailed usage guide, see TIMEZONE_CONFIGURATION.md*
