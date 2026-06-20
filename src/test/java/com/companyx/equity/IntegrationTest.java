package com.companyx.equity;

import com.companyx.equity.dto.AuthRequest;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.repository.TransactionTypeRepository;
import com.companyx.equity.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests
 * Tests the full application stack from HTTP request to database
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class IntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TransactionTypeRepository transactionTypeRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    public void setup() {
        // Clean up and setup test data
        userRepository.deleteAll();
        transactionTypeRepository.deleteAll();
        
        // Create test user
        User user = new User();
        user.setUid("integration-test-user");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole("USER");
        user.setEnabled(true);
        userRepository.save(user);
        
        // Create transaction types
        transactionTypeRepository.save(new TransactionType(null, TransactionType.BUY));
        transactionTypeRepository.save(new TransactionType(null, TransactionType.SALE));
        transactionTypeRepository.save(new TransactionType(null, TransactionType.DEPOSIT));
        transactionTypeRepository.save(new TransactionType(null, TransactionType.WITHDRAWAL));
    }
    
    @Test
    public void testFullAuthenticationFlow() throws Exception {
        AuthRequest request = new AuthRequest("integration-test-user", "password123");
        
        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.uid").value("integration-test-user"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        // Extract token for subsequent requests
        String token = objectMapper.readTree(response).get("token").asText();
        
        // Use token to access protected endpoint
        mockMvc.perform(get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
    
    @Test
    public void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    public void testInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    public void testHealthCheck() throws Exception {
        // Test actuator health endpoint
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    public void testOpenApiDocsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Equity P&L Service API"));
    }

    @Test
    public void testCorrelationIdHeaderPropagated() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-Id", "test-correlation-abc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-correlation-abc"));
    }
}
