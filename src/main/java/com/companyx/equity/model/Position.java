package com.companyx.equity.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;

/**
 * In-memory P&L position (computed from transactions, not persisted).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Position implements Serializable {
    public Position(User user, Timestamp timestamp, String symbol) {
        this.user = user;
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.quantity = BigInteger.ZERO;
        this.value = BigDecimal.ZERO;
        this.realized = BigDecimal.ZERO;
        this.unrealized = BigDecimal.ZERO;
    }

    public Position(Position other) {
        this.id = other.id;
        this.user = other.user;
        this.timestamp = other.timestamp;
        this.symbol = other.symbol;
        this.quantity = other.quantity;
        this.value = other.value;
        this.realized = other.realized;
        this.unrealized = other.unrealized;
        this.price = other.price;
    }

    private int id;
    private Timestamp timestamp;
    private String symbol;
    private BigInteger quantity;
    private BigDecimal value;
    private BigDecimal realized;
    private BigDecimal unrealized;
    private BigDecimal price;
    private User user;

    @Override
    public String toString() {
        return "{" +
            "\"timestamp\":" + timestamp +
            "\"symbol\":" + symbol +
            "\"quantity\":" + quantity +
            "\"value\":" + value +
            "\"realized\":" + realized +
            "\"unrealized\":" + unrealized +
        "}";
    }
}
