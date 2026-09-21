package com.elcafe.modules.financial.dto;

import com.elcafe.modules.financial.entity.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String code;
    private String name;
    private Account.AccountType type;
    private Account.AccountCategory category;
    private String description;
    private BigDecimal balance;
    private Account.NormalBalance normalBalance;
    private Long parentAccountId;
    private String parentAccountName;
    private Boolean active;
    private Boolean systemAccount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
