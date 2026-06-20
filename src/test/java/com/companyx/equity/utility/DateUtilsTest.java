package com.companyx.equity.utility;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DateUtils utility class
 */
public class DateUtilsTest {
    
    @Test
    public void testDateFromEpoch() {
        // Unix epoch for 2024-01-01 00:00:00 UTC
        Long unixSeconds = 1704067200L;
        Date result = DateUtils.dateFromEpoch(unixSeconds);
        
        assertNotNull(result);
        assertEquals(1704067200000L, result.getTime());
    }
    
    @Test
    public void testDateFromEpochZero() {
        Date result = DateUtils.dateFromEpoch(0L);
        assertNotNull(result);
        assertEquals(0L, result.getTime());
    }
    
    @Test
    public void testEpochFromDate() {
        Date date = new Date(1704067200000L);
        Long result = DateUtils.epochFromDate(date);
        
        assertEquals(1704067200L, result);
    }
    
    @Test
    public void testEpochFromDateZero() {
        Date date = new Date(0L);
        Long result = DateUtils.epochFromDate(date);
        
        assertEquals(0L, result);
    }
    
    @Test
    public void testRoundTripConversion() {
        Long originalEpoch = 1704067200L;
        Date date = DateUtils.dateFromEpoch(originalEpoch);
        Long resultEpoch = DateUtils.epochFromDate(date);
        
        assertEquals(originalEpoch, resultEpoch);
    }
    
    @Test
    public void testStringFromEpochGMT() {
        Long unixSeconds = 1704067200L; // 2024-01-01 00:00:00 UTC
        String result = DateUtils.stringFromEpochGMT(unixSeconds);
        
        assertNotNull(result);
        assertTrue(result.contains("2024-01-01"));
        assertTrue(result.contains("GMT"));
    }
    
    @Test
    public void testStringFromEpochPT() {
        // 2024-01-01 00:00:00 UTC = 2023-12-31 16:00:00 PST (GMT-8)
        Long unixSeconds = 1704067200L;
        String result = DateUtils.stringFromEpochPT(unixSeconds);
        
        assertNotNull(result);
        // In winter, should be PST
        assertTrue(result.contains("2023-12-31"));
        assertTrue(result.contains("PST"));
    }
    
    @Test
    public void testStringFromEpochPTSummerTime() {
        // July 1, 2024 00:00:00 UTC = June 30, 2024 17:00:00 PDT (GMT-7)
        Long unixSeconds = 1719792000L;
        String result = DateUtils.stringFromEpochPT(unixSeconds);
        
        assertNotNull(result);
        // In summer, should be PDT
        assertTrue(result.contains("2024-06-30"));
        assertTrue(result.contains("PDT"));
    }
    
    @Test
    public void testStringFromEpochWithTimeZone() {
        Long unixSeconds = 1704067200L; // 2024-01-01 00:00:00 UTC
        TimeZone et = TimeZone.getTimeZone("America/New_York");
        
        String result = DateUtils.stringFromEpoch(unixSeconds, et);
        
        assertNotNull(result);
        // EST is GMT-5, so should be 2023-12-31 19:00:00
        assertTrue(result.contains("2023-12-31"));
        assertTrue(result.contains("EST"));
    }
    
    @Test
    public void testStringFromEpochWithTimeZoneId() {
        Long unixSeconds = 1704067200L; // 2024-01-01 00:00:00 UTC
        
        String resultUTC = DateUtils.stringFromEpoch(unixSeconds, "UTC");
        String resultPT = DateUtils.stringFromEpoch(unixSeconds, "America/Los_Angeles");
        String resultET = DateUtils.stringFromEpoch(unixSeconds, "America/New_York");
        
        assertNotNull(resultUTC);
        assertTrue(resultUTC.contains("2024-01-01"));
        
        assertNotNull(resultPT);
        assertTrue(resultPT.contains("2023-12-31"));
        
        assertNotNull(resultET);
        assertTrue(resultET.contains("2023-12-31"));
    }
    
    @Test
    public void testStringFromEpochMultipleTimeZones() {
        Long unixSeconds = 1704067200L;
        
        // Test various timezones
        String utc = DateUtils.stringFromEpoch(unixSeconds, "UTC");
        String london = DateUtils.stringFromEpoch(unixSeconds, "Europe/London");
        String tokyo = DateUtils.stringFromEpoch(unixSeconds, "Asia/Tokyo");
        String sydney = DateUtils.stringFromEpoch(unixSeconds, "Australia/Sydney");
        
        assertNotNull(utc);
        assertNotNull(london);
        assertNotNull(tokyo);
        assertNotNull(sydney);
        
        // Tokyo is ahead of UTC (should be 2024-01-01 09:00:00)
        assertTrue(tokyo.contains("2024-01-01"));
        assertTrue(tokyo.contains("09:00"));
    }
    
    @Test
    public void testNegativeEpoch() {
        // Test dates before 1970
        Long negativeEpoch = -86400L; // One day before epoch
        Date result = DateUtils.dateFromEpoch(negativeEpoch);
        
        assertNotNull(result);
        assertEquals(-86400000L, result.getTime());
    }
    
    @Test
    public void testInvalidTimeZoneId() {
        Long unixSeconds = 1704067200L;
        
        // Invalid timezone should default to GMT
        String result = DateUtils.stringFromEpoch(unixSeconds, "Invalid/Timezone");
        
        assertNotNull(result);
        // Should still work, defaults to GMT
        assertTrue(result.contains("2024-01-01"));
    }
}
