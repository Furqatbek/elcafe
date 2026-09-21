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
public class BatchSyncResult {

    private int totalOrders;
    private int successCount;
    private int failureCount;
    private List<SyncResult> results;

    public static BatchSyncResult of(List<SyncResult> results) {
        int success = (int) results.stream().filter(SyncResult::isSuccess).count();
        return BatchSyncResult.builder()
            .totalOrders(results.size())
            .successCount(success)
            .failureCount(results.size() - success)
            .results(results)
            .build();
    }
}
