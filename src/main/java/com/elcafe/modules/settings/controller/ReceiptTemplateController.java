package com.elcafe.modules.settings.controller;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.settings.entity.ReceiptTemplate;
import com.elcafe.modules.settings.repository.ReceiptTemplateRepository;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/settings/receipt-template")
@RequiredArgsConstructor
public class ReceiptTemplateController {

    private final ReceiptTemplateRepository receiptTemplateRepository;
    private final RestaurantRepository restaurantRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<ReceiptTemplate>> getTemplate(
            @RequestParam Long restaurantId) {
        log.info("Getting receipt template for restaurant: {}", restaurantId);

        ReceiptTemplate template = receiptTemplateRepository.findByRestaurantId(restaurantId)
                .orElse(null);

        return ResponseEntity.ok(ApiResponse.success("Receipt template retrieved", template));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReceiptTemplate>> saveTemplate(
            @RequestParam Long restaurantId,
            @RequestBody ReceiptTemplate request) {
        log.info("Saving receipt template for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        ReceiptTemplate template = receiptTemplateRepository.findByRestaurantId(restaurantId)
                .orElse(ReceiptTemplate.builder().restaurant(restaurant).build());

        template.setRestaurantName(request.getRestaurantName());
        template.setTagline(request.getTagline());
        template.setPhone(request.getPhone());
        template.setWebsite(request.getWebsite());
        template.setFooterMessage(request.getFooterMessage());
        template.setCurrency(request.getCurrency() != null ? request.getCurrency() : "UZS");
        template.setShowQrCode(request.getShowQrCode() != null ? request.getShowQrCode() : true);
        template.setQrUrl(request.getQrUrl());
        template.setQrTitle(request.getQrTitle());
        template.setQrSubtitle(request.getQrSubtitle());
        template.setPaperWidthMm(request.getPaperWidthMm() != null ? request.getPaperWidthMm() : 58);
        template.setKitchenHeaderText(request.getKitchenHeaderText());
        template.setKitchenFooterText(request.getKitchenFooterText());

        template = receiptTemplateRepository.save(template);
        log.info("Receipt template saved for restaurant: {}", restaurantId);

        return ResponseEntity.ok(ApiResponse.success("Receipt template saved successfully", template));
    }
}
