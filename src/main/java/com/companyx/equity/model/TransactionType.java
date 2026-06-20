package com.companyx.equity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
public class TransactionType {
    public static final String DEPOSIT = "deposit";
    public static final String WITHDRAWAL = "withdraw";
    public static final String BUY = "buy";
    public static final String SALE = "sell";
    public static final Set CASH_TRANS = Set.of(TransactionType.DEPOSIT, TransactionType.WITHDRAWAL);

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;
    @NotNull
    private String description;
}