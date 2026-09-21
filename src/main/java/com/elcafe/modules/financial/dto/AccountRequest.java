package com.elcafe.modules.financial.dto;

import com.elcafe.modules.financial.entity.Account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotBlank(message = "Account code is required")
    private String code;

    @NotBlank(message = "Account name is required")
    private String name;

    @NotNull(message = "Account type is required")
    private Account.AccountType type;

    @NotNull(message = "Account category is required")
    private Account.AccountCategory category;

    private String description;

    @NotNull(message = "Normal balance is required")
    private Account.NormalBalance normalBalance;

    private Long parentAccountId;

    private Boolean active;

    private Boolean systemAccount;
}
