package com.companyx.equity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @NotNull
    private Timestamp timestamp;
    private String symbol;
    
    @PositiveOrZero(message = "Quantity cannot be negative")
    private BigInteger quantity;
    
    @PositiveOrZero(message = "Value cannot be negative")
    private BigDecimal value;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    private TransactionType transactionType;

    @Override
    public String toString() {
        return "{" +
            "\"timestamp\":" + timestamp +
            "\"symbol\":" + symbol +
            "\"type\":" + transactionType.getDescription() +
            "\"quantity\":" + quantity +
            "\"value\":" + value +
        "}";
    }
}