package com.companyx.equity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error envelope")
public class ErrorResponse {
    @Schema(example = "2024-06-20T12:00:00Z")
    private Instant timestamp;
    @Schema(example = "400")
    private int status;
    @Schema(example = "Validation Failed")
    private String error;
    @Schema(example = "Invalid input parameters")
    private String message;
    @Schema(example = "/api/v1/transactions/pnl")
    private String path;
    @Schema(description = "Request correlation ID (also sent as X-Correlation-Id header)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String correlationId;
    private List<ValidationError> validationErrors;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
