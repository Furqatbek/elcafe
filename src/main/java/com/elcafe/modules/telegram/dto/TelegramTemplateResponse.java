package com.elcafe.modules.telegram.dto;

import com.elcafe.modules.telegram.entity.TelegramTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramTemplateResponse {

    private Long id;
    private String name;
    private String content;
    private String type;
    private String description;
    private Boolean hasImage;
    private String imageUrl;
    private Boolean hasButtons;
    private List<Map<String, String>> buttonsConfig;
    private Boolean isActive;
    private Integer usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TelegramTemplateResponse from(TelegramTemplate template) {
        return TelegramTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .content(template.getContent())
                .type(template.getType())
                .description(template.getDescription())
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
