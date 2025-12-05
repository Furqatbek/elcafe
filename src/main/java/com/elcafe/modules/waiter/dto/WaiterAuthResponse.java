package com.elcafe.modules.waiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterAuthResponse {
    private Long waiterId;
    private String name;
    private String token;
    private WaiterResponse waiter;
}
