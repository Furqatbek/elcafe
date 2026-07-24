package com.elcafe.modules.notification.dto.waiter;

import com.elcafe.modules.notification.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterDeviceRequest {
    @NotBlank(message = "token is required")
    private String token;              // "ExponentPushToken[...]"

    @NotNull(message = "platform is required")
    private DevicePlatform platform;

    private String deviceId;
    private String deviceName;
    private String appVersion;
}
