package com.elcafe.modules.telegram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramTemplateRequest {

    @NotBlank(message = "Template name is required")
    @Size(max = 100, message = "Template name must be less than 100 characters")
    private String name;

    @NotBlank(message = "Template content is required")
    private String content;

    @NotBlank(message = "Template type is required")
    @Size(max = 50, message = "Template type must be less than 50 characters")
    private String type;

    @Size(max = 500, message = "Description must be less than 500 characters")
    private String description;

    private Boolean hasImage;

    @Size(max = 500, message = "Image URL must be less than 500 characters")
    private String imageUrl;

    private Boolean hasButtons;

    private List<Map<String, String>> buttonsConfig;

    private Boolean isActive;
}
