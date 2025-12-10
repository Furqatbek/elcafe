package com.elcafe.modules.courier.dto;

import com.elcafe.modules.courier.enums.CourierType;
import com.elcafe.modules.courier.enums.CourierVehicle;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCourierRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String phone;

    @NotNull(message = "Courier type is required")
    private CourierType courierType;

    @NotNull(message = "Vehicle type is required")
    private CourierVehicle vehicle;

    @Size(max = 50)
    private String vehiclePlate;

    @Size(max = 100)
    private String licenseNumber;

    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 20)
    private String emergencyContact;

    @Builder.Default
    private Boolean available = true;

    @Builder.Default
    private Boolean active = true;
}
