package com.companyx.equity.utility;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Utility class for date conversions
 * 
 * Note: For timezone-aware formatting, use the instance methods via Spring DI
 * or use the static methods with explicit timezone parameters.
 */
public class DateUtils {
    
    // https://stackoverflow.com/questions/17432735/convert-unix-timestamp-to-date-in-java
    public static Date dateFromEpoch(Long unixSeconds) {
        // convert seconds to milliseconds
        return new java.util.Date(unixSeconds * 1000L);
    }

    public static Long epochFromDate(Date date) {
        // convert milliseconds to seconds
        return date.getTime() / 1000L;
    }

    /**
     * Format epoch seconds as string in GMT timezone
     */
    public static String stringFromEpochGMT(Long unixSeconds) {
        return stringFromEpoch(unixSeconds, TimeZone.getTimeZone("GMT"));
    }

    /**
     * Format epoch seconds as string in Pacific timezone (America/Los_Angeles)
     * Automatically handles PST/PDT transitions
     * 
     * @deprecated Use stringFromEpoch() with configured timezone instead
     */
    @Deprecated
    public static String stringFromEpochPT(Long unixSeconds) {
        return stringFromEpoch(unixSeconds, TimeZone.getTimeZone("America/Los_Angeles"));
    }
    
    /**
     * Format epoch seconds as string in the specified timezone
     * 
     * @param unixSeconds Unix epoch seconds
     * @param timeZone The timezone to use for formatting
     * @return Formatted date string
     */
    public static String stringFromEpoch(Long unixSeconds, TimeZone timeZone) {
        Date date = dateFromEpoch(unixSeconds);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        sdf.setTimeZone(timeZone);
        return sdf.format(date);
    }
    
    /**
     * Format epoch seconds as string using timezone ID
     * 
     * @param unixSeconds Unix epoch seconds
     * @param timeZoneId Timezone ID (e.g., "America/Los_Angeles", "UTC", "America/New_York")
     * @return Formatted date string
     */
    public static String stringFromEpoch(Long unixSeconds, String timeZoneId) {
        return stringFromEpoch(unixSeconds, TimeZone.getTimeZone(timeZoneId));
    }
}
