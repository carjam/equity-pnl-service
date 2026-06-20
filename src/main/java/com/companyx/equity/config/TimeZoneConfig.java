package com.companyx.equity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Configuration for application timezone settings
 */
@Configuration
@ConfigurationProperties(prefix = "application.timezone")
@Getter
@Setter
public class TimeZoneConfig {
    
    /**
     * Default timezone ID (e.g., "America/Los_Angeles", "America/New_York", "UTC")
     * Defaults to UTC if not specified
     */
    private String id = "UTC";
    
    /**
     * Get the configured TimeZone object
     */
    public TimeZone getTimeZone() {
        return TimeZone.getTimeZone(id);
    }
}
