package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.entity.Supplier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private String code;

    // Contact Information
    private String contactPerson;
    private String phone;
    private String email;
    private String address;

    // Business Terms
    private String paymentTerms;
    private BigDecimal creditLimit;
    private String currency;

    // Performance Metrics
    private BigDecimal rating;
    private Integer totalOrders;
    private BigDecimal onTimeDeliveryRate;

    // Status
    private Boolean active;
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SupplierResponse fromEntity(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .restaurantId(supplier.getRestaurant().getId())
                .restaurantName(supplier.getRestaurant().getName())
                .name(supplier.getName())
                .code(supplier.getCode())
                .contactPerson(supplier.getContactPerson())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .address(supplier.getAddress())
                .paymentTerms(supplier.getPaymentTerms())
                .creditLimit(supplier.getCreditLimit())
                .currency(supplier.getCurrency())
                .rating(supplier.getRating())
                .totalOrders(supplier.getTotalOrders())
                .onTimeDeliveryRate(supplier.getOnTimeDeliveryRate())
                .active(supplier.getActive())
                .notes(supplier.getNotes())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }
}
