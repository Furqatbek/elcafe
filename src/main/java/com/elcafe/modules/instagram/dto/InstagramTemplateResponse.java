package com.elcafe.modules.instagram.dto;

import com.elcafe.modules.instagram.entity.InstagramTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Template view for the operator. Mirrors {@code TelegramTemplateResponse}. Deliberately omits
 * {@code restaurantId}: every template returned by the service already belongs to the caller's own
 * restaurant, so echoing the tenant id back is redundant — leaving it off means this DTO can never be
 * the thing that leaks another tenant's id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramTemplateResponse {

    private Long id;
    private String name;
    private String description;
    private String messageText;
    private Boolean hasImage;
    private String imageUrl;
    private Boolean hasButtons;
    private List<Map<String, String>> buttonsConfig;
    private Boolean isActive;
    private Integer usageCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static InstagramTemplateResponse from(InstagramTemplate template) {
        return InstagramTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .messageText(template.getMessageText())
                .hasImage(template.getHasImage())
                .imageUrl(template.getImageUrl())
                .hasButtons(template.getHasButtons())
                .buttonsConfig(template.getButtonsConfig())
                .isActive(template.getIsActive())
                .usageCount(template.getUsageCount())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
