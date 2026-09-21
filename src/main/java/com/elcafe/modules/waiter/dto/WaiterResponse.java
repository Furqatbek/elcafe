package com.elcafe.modules.waiter.dto;

import com.elcafe.modules.waiter.enums.WaiterRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterResponse {
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private WaiterRole role;
    private Boolean active;
    private List<String> permissions;
    private Integer activeTablesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
