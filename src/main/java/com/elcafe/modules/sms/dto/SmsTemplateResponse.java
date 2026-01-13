package com.elcafe.modules.sms.dto;

import com.elcafe.modules.sms.entity.SmsTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsTemplateResponse {

    private Long id;
    private String name;
    private String content;
    private String type;
    private String description;
    private Boolean isActive;
    private Integer usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SmsTemplateResponse from(SmsTemplate template) {
        return SmsTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .content(template.getContent())
                .type(template.getType())
                .description(template.getDescription())
                .isActive(template.getIsActive())
                .usageCount(template.getUsageCount())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
