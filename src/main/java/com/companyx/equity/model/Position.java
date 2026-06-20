package com.companyx.equity.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
public class Position {
    public Position(User user, Timestamp timestamp, String symbol) {
        this.user = user;
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.quantity = BigInteger.ZERO;
        this.value = BigDecimal.ZERO;
        this.realized = BigDecimal.ZERO;
        this.unrealized = BigDecimal.ZERO;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private Timestamp timestamp;
    private String symbol;
    private BigInteger quantity;
    private BigDecimal value;
    private BigDecimal realized;
    private BigDecimal unrealized;
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.EAGER)
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