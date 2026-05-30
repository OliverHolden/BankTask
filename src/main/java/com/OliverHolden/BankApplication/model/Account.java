package com.OliverHolden.BankApplication.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Account {

    @Id
    @EqualsAndHashCode.Include
    private String accountNumber;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String sortCode;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @DecimalMin(value = "0.00")
    @DecimalMax(value = "10000.00")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private String currency;

    private OffsetDateTime createdTimestamp;

    private OffsetDateTime updatedTimestamp;
}
