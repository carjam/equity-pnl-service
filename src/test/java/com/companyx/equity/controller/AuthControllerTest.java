package com.companyx.equity.controller;

import com.companyx.equity.dto.AuthRequest;
import com.companyx.equity.dto.AuthResponse;
import com.companyx.equity.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.companyx.equity.error.RestExceptionHandler;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for AuthController
 */
@WebMvcTest(AuthController.class)
@Import(RestExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AuthenticationManager authenticationManager;
    
    @MockBean
    private UserDetailsService userDetailsService;
    
    @MockBean
    private JwtUtil jwtUtil;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    public void testLogin_Success() throws Exception {
        AuthRequest request = new AuthRequest("test-user", "password123");
        
        UserDetails userDetails = User.builder()
                .username("test-user")
                .password("password123")
                .authorities(Collections.emptyList())
                .build();
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null); // Successful authentication
        when(userDetailsService.loadUserByUsername("test-user"))
                .thenReturn(userDetails);
        when(jwtUtil.generateToken(userDetails))
                .thenReturn("mock-jwt-token");
        
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.uid").value("test-user"));
        
        verify(authenticationManager, times(1)).authenticate(any());
        verify(userDetailsService, times(1)).loadUserByUsername("test-user");
        verify(jwtUtil, times(1)).generateToken(userDetails);
    }
    
    @Test
    public void testLogin_InvalidCredentials() throws Exception {
        AuthRequest request = new AuthRequest("test-user", "wrong-password");
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        
        verify(authenticationManager, times(1)).authenticate(any());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(jwtUtil, never()).generateToken(any());
    }
    
    @Test
    public void testLogin_MissingUsername() throws Exception {
        String invalidRequest = "{\"password\":\"password123\"}";
        
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    public void testLogin_MissingPassword() throws Exception {
        String invalidRequest = "{\"uid\":\"test-user\"}";
        
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    public void testLogin_EmptyRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    public void testLogin_EmptyUsername() throws Exception {
        AuthRequest request = new AuthRequest("", "password123");
        
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    public void testLogin_EmptyPassword() throws Exception {
        AuthRequest request = new AuthRequest("test-user", "");
        
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    public void testLogin_InvalidJsonFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("invalid json"))
                .andExpect(status().isBadRequest());
    }
}
