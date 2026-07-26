package com.elcafe.modules.instagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Create/update payload for an Instagram DM template. Mirrors {@code TelegramTemplateRequest},
 * adapted to Instagram's per-tenant model: there is no {@code restaurantId} field — the owning
 * tenant is always resolved from the caller, never accepted from the request body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramTemplateRequest {

    @NotBlank(message = "Template name is required")
    @Size(max = 100, message = "Template name must be less than 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be less than 500 characters")
    private String description;

    @NotBlank(message = "Template message text is required")
    private String messageText;

    private Boolean hasImage;

    @Size(max = 500, message = "Image URL must be less than 500 characters")
    private String imageUrl;

    private Boolean hasButtons;

    private List<Map<String, String>> buttonsConfig;

    private Boolean isActive;
}
