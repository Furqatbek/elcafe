package uz.megahotdog.modules.sms.dto;

import uz.megahotdog.modules.sms.enums.CampaignStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignStatsResponse {

    private Long campaignId;
    private String campaignName;
    private CampaignStatus status;
    private Integer recipientCount;
    private Integer sentCount;
    private Integer deliveredCount;
    private Integer failedCount;
    private Double deliveryRate;
    private BigDecimal totalCost;
    private Map<String, Long> statusBreakdown;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
