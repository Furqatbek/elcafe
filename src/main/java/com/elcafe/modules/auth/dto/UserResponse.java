package com.elcafe.modules.auth.dto;

import com.elcafe.modules.auth.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User information")
public class UserResponse {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "Email address", example = "user@elcafe.com")
    private String email;

    @Schema(description = "First name", example = "John")
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "Phone number", example = "+1234567890")
    private String phone;

    @Schema(description = "User role", example = "OPERATOR")
    private UserRole role;

    @Schema(description = "Account active status")
    private Boolean active;

    @Schema(description = "Email verification status")
    private Boolean emailVerified;

    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;
}
