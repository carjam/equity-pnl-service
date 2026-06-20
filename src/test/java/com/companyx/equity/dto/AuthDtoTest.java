package com.companyx.equity.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Auth DTOs
 */
public class AuthDtoTest {
    
    @Test
    public void testAuthRequestCreation() {
        AuthRequest request = new AuthRequest("test-user", "password123");
        
        assertEquals("test-user", request.getUid());
        assertEquals("password123", request.getPassword());
    }
    
    @Test
    public void testAuthRequestSetters() {
        AuthRequest request = new AuthRequest();
        request.setUid("new-user");
        request.setPassword("new-password");
        
        assertEquals("new-user", request.getUid());
        assertEquals("new-password", request.getPassword());
    }
    
    @Test
    public void testAuthResponseWithToken() {
        AuthResponse response = new AuthResponse("jwt-token");
        
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getType());
        assertNull(response.getUid());
    }
    
    @Test
    public void testAuthResponseWithTokenAndUid() {
        AuthResponse response = new AuthResponse("jwt-token", "test-user");
        
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getType());
        assertEquals("test-user", response.getUid());
    }
    
    @Test
    public void testAuthResponseFullConstructor() {
        AuthResponse response = new AuthResponse("jwt-token", "Bearer", "test-user");
        
        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getType());
        assertEquals("test-user", response.getUid());
    }
    
    @Test
    public void testAuthResponseSetters() {
        AuthResponse response = new AuthResponse();
        response.setToken("new-token");
        response.setType("Custom");
        response.setUid("new-user");
        
        assertEquals("new-token", response.getToken());
        assertEquals("Custom", response.getType());
        assertEquals("new-user", response.getUid());
    }
}
