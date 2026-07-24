package com.elcafe.modules.notification.dto.waiter;

import com.elcafe.modules.notification.enums.DevicePlatform;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RegisterDeviceResponse {
    private Long deviceId;          // backend record id
    private String token;
    private DevicePlatform platform;
    private LocalDateTime registeredAt;
}
