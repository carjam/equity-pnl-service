package com.companyx.equity.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TimeZoneConfig
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class TimeZoneConfigTest {
    
    @Autowired
    private TimeZoneConfig timeZoneConfig;
    
    @Test
    public void testTimeZoneConfigLoaded() {
        assertNotNull(timeZoneConfig);
    }
    
    @Test
    public void testDefaultTimeZoneIsUTC() {
        // Test configuration specifies UTC
        assertEquals("UTC", timeZoneConfig.getId());
    }
    
    @Test
    public void testGetTimeZone() {
        TimeZone timeZone = timeZoneConfig.getTimeZone();
        assertNotNull(timeZone);
        assertEquals("UTC", timeZone.getID());
    }
    
    @Test
    public void testSetCustomTimeZone() {
        TimeZoneConfig customConfig = new TimeZoneConfig();
        customConfig.setId("America/New_York");
        
        assertEquals("America/New_York", customConfig.getId());
        assertEquals("America/New_York", customConfig.getTimeZone().getID());
    }
    
    @Test
    public void testSetPacificTimeZone() {
        TimeZoneConfig customConfig = new TimeZoneConfig();
        customConfig.setId("America/Los_Angeles");
        
        TimeZone tz = customConfig.getTimeZone();
        assertEquals("America/Los_Angeles", tz.getID());
        
        // Verify it handles DST correctly
        // Create dates in summer and winter
        long summerDate = 1719792000000L; // July 1, 2024
        long winterDate = 1704067200000L; // January 1, 2024
        
        int summerOffset = tz.getOffset(summerDate);
        int winterOffset = tz.getOffset(winterDate);
        
        // Summer should be PDT (GMT-7 = -25200000 ms)
        // Winter should be PST (GMT-8 = -28800000 ms)
        assertTrue(summerOffset > winterOffset, "Summer offset should be less negative (PDT vs PST)");
    }
    
    @Test
    public void testInvalidTimeZoneFallsBackToGMT() {
        TimeZoneConfig customConfig = new TimeZoneConfig();
        customConfig.setId("Invalid/TimeZone");
        
        TimeZone tz = customConfig.getTimeZone();
        // Java's TimeZone.getTimeZone() returns GMT for invalid IDs
        assertEquals("GMT", tz.getID());
    }
}
