package com.elcafe.modules.waiter.dto;

import com.elcafe.modules.waiter.enums.TableStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableResponse {
    private Long id;
    private Long restaurantId;
    private Integer number;
    private Integer capacity;
    private String floor;
    private String section;
    private TableStatus status;
    private Long currentWaiterId;
    private String currentWaiterName;
    private Long mergedWithId;
    private Integer mergedWithNumber;
    private Boolean isMerged;
    private Boolean isAvailable;
    private Integer activeOrdersCount;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
