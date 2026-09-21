package com.elcafe.modules.waiter.dto;

import com.elcafe.modules.waiter.enums.WaiterRole;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWaiterRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "^[0-9]{4,6}$", message = "PIN code must be 4-6 digits")
    private String pinCode;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Pattern(regexp = "^\\+?[0-9]{10,20}$", message = "Phone number must be valid")
    private String phoneNumber;

    @NotNull(message = "Role is required")
    private WaiterRole role;

    private Boolean active;

    private List<String> permissions;
}
