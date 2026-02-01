package com.elcafe.modules.pos.offline.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResult {

    private String clientOrderId;
    private Long syncedOrderId;
    private boolean success;
    private String error;

    public static SyncResult success(String clientOrderId, Long syncedOrderId) {
        return SyncResult.builder()
            .clientOrderId(clientOrderId)
            .syncedOrderId(syncedOrderId)
            .success(true)
            .build();
    }

    public static SyncResult failure(String clientOrderId, String error) {
        return SyncResult.builder()
            .clientOrderId(clientOrderId)
            .success(false)
            .error(error)
            .build();
    }
}
