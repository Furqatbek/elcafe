package com.elcafe.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Registration request")
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "User email", example = "user@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(description = "User password", example = "Password123!")
    private String password;

    @NotBlank(message = "First name is required")
    @Schema(description = "First name", example = "John")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "Phone number", example = "+1234567890")
    private String phone;

    // NOTE: Role is intentionally NOT accepted from the client. Public self-registration
    // always creates an unprivileged OWNER (see AuthService.register). Allowing the client
    // to choose the role previously let anyone self-assign ADMIN / platform-wide access.
}
