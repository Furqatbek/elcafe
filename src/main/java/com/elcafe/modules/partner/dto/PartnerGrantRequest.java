package com.elcafe.modules.partner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a partner may do at one venue. Both default to the safe answer, so an operator who grants a
 * venue without thinking about capabilities gets read-only access rather than a partner that can write
 * into the kitchen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerGrantRequest {

    @Builder.Default
    private Boolean canReadMenu = true;

    @Builder.Default
    private Boolean canPushOrders = false;
}
