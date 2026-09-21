package com.elcafe.modules.pos.offline.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatusResponse {

    private List<DeviceInfo> onlineDevices;
    private List<DeviceInfo> offlineDevices;
    private long pendingOrdersCount;
}
