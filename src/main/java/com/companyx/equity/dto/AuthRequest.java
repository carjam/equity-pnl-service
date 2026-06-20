package com.companyx.equity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    @NotBlank(message = "UID is required")
    private String uid;
    
    @NotBlank(message = "Password is required")
    private String password;
}
