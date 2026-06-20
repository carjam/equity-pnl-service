package com.companyx.equity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type = "Bearer";
    private String uid;
    
    public AuthResponse(String token) {
        this.token = token;
    }
    
    public AuthResponse(String token, String uid) {
        this.token = token;
        this.uid = uid;
    }
}
