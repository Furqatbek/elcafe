package com.elcafe.modules.pos.offline.dto;

import com.elcafe.modules.pos.offline.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfo {

    private Long id;
    private String deviceId;
    private String deviceName;
    private DeviceType deviceType;
    private OffsetDateTime lastHeartbeat;
    private OffsetDateTime lastSync;
    private boolean isOnline;
}
