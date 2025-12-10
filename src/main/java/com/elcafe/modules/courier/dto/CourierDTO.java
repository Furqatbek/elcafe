package com.elcafe.modules.courier.dto;

import com.elcafe.modules.courier.enums.CourierType;
import com.elcafe.modules.courier.enums.CourierVehicle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierDTO {
    private Long id;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private CourierType courierType;
    private CourierVehicle vehicle;
    private String vehiclePlate;
    private String licenseNumber;
    private Boolean available;
    private Boolean verified;
    private String address;
    private String city;
    private String emergencyContact;
    private Boolean userActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
