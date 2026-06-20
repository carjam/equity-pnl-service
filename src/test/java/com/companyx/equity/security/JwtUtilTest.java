package com.companyx.equity.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JwtUtil
 * Tests JWT token generation, validation, and claims extraction
 */
public class JwtUtilTest {
    
    private JwtUtil jwtUtil;
    private UserDetails userDetails;
    
    // Use a 256-bit key for HS256
    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm-security-requirements";
    private static final Long TEST_EXPIRATION = 3600000L; // 1 hour
    
    @BeforeEach
    public void setup() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", TEST_EXPIRATION);
        
        userDetails = User.builder()
                .username("test-user")
                .password("password")
                .authorities(Collections.emptyList())
                .build();
    }
    
    @Test
    public void testGenerateToken() {
        String token = jwtUtil.generateToken(userDetails);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        // JWT has 3 parts separated by dots
        assertEquals(2, token.split("\\.").length - 1);
    }
    
    @Test
    public void testExtractUsername() {
        String token = jwtUtil.generateToken(userDetails);
        String username = jwtUtil.extractUsername(token);
        
        assertEquals("test-user", username);
    }
    
    @Test
    public void testExtractExpiration() {
        String token = jwtUtil.generateToken(userDetails);
        Date expiration = jwtUtil.extractExpiration(token);
        
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
        
        // Should expire in approximately 1 hour
        long diff = expiration.getTime() - System.currentTimeMillis();
        assertTrue(diff > 3500000 && diff < 3700000); // Between 58 and 62 minutes
    }
    
    @Test
    public void testValidateToken_Valid() {
        String token = jwtUtil.generateToken(userDetails);
        Boolean isValid = jwtUtil.validateToken(token, userDetails);
        
        assertTrue(isValid);
    }
    
    @Test
    public void testValidateToken_WrongUser() {
        String token = jwtUtil.generateToken(userDetails);
        
        UserDetails differentUser = User.builder()
                .username("different-user")
                .password("password")
                .authorities(Collections.emptyList())
                .build();
        
        Boolean isValid = jwtUtil.validateToken(token, differentUser);
        
        assertFalse(isValid);
    }
    
    @Test
    public void testValidateToken_ExpiredToken() throws InterruptedException {
        // Create JWT util with very short expiration
        JwtUtil shortExpirationJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortExpirationJwtUtil, "expiration", 1L); // 1 millisecond
        
        String token = shortExpirationJwtUtil.generateToken(userDetails);
        
        // Wait for token to expire
        Thread.sleep(10);
        
        Boolean isValid = shortExpirationJwtUtil.validateToken(token, userDetails);
        
        assertFalse(isValid);
    }
    
    @Test
    public void testExtractClaim() {
        String token = jwtUtil.generateToken(userDetails);
        
        // Extract subject claim
        String subject = jwtUtil.extractClaim(token, Claims::getSubject);
        assertEquals("test-user", subject);
        
        // Extract issued at claim
        Date issuedAt = jwtUtil.extractClaim(token, Claims::getIssuedAt);
        assertNotNull(issuedAt);
        assertTrue(issuedAt.before(new Date()) || issuedAt.equals(new Date()));
    }
    
    @Test
    public void testTokenWithDifferentSecret_ShouldFail() {
        String token = jwtUtil.generateToken(userDetails);
        
        // Create new JWT util with different secret
        JwtUtil differentSecretJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(differentSecretJwtUtil, "secret", 
                "different-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm-security-requirements");
        ReflectionTestUtils.setField(differentSecretJwtUtil, "expiration", TEST_EXPIRATION);
        
        // Should throw exception when trying to parse with wrong secret
        assertThrows(Exception.class, () -> {
            differentSecretJwtUtil.extractUsername(token);
        });
    }
    
    @Test
    public void testInvalidToken() {
        String invalidToken = "invalid.token.here";
        
        assertThrows(Exception.class, () -> {
            jwtUtil.extractUsername(invalidToken);
        });
    }
    
    @Test
    public void testEmptyToken() {
        assertThrows(Exception.class, () -> {
            jwtUtil.extractUsername("");
        });
    }
    
    @Test
    public void testNullToken() {
        assertThrows(Exception.class, () -> {
            jwtUtil.extractUsername(null);
        });
    }
    
    @Test
    public void testMultipleTokensForSameUser() {
        String token1 = jwtUtil.generateToken(userDetails);
        String token2 = jwtUtil.generateToken(userDetails);
        
        // Both tokens should be valid
        assertTrue(jwtUtil.validateToken(token1, userDetails));
        assertTrue(jwtUtil.validateToken(token2, userDetails));
        
        // But they should be different (due to different issue times)
        assertNotEquals(token1, token2);
    }
}
