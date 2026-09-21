package com.elcafe.modules.pos.offline.dto;

import com.elcafe.modules.pos.offline.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationRequest {

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    private String deviceName;

    private DeviceType deviceType;
}
