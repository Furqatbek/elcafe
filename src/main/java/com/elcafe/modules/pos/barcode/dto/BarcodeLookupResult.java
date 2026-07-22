package com.elcafe.modules.pos.barcode.dto;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeLookupResult {

    private boolean found;
    private String searchedCode;
    private LookupType type;
    private Long productId;
    private Long variantId;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private String barcode;
    private boolean available;
    private boolean isSoldByWeight;
    private String weightUnit;

    public enum LookupType {
        PRODUCT,
        VARIANT,
        NOT_FOUND
    }

    public static BarcodeLookupResult product(Product product) {
        return BarcodeLookupResult.builder()
            .found(true)
            .type(LookupType.PRODUCT)
            .productId(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .price(product.getPrice())
            .sku(product.getSku())
            .barcode(product.getBarcode())
            .available(product.getIsAvailable() != null && product.getIsAvailable())
            .isSoldByWeight(product.getIsSoldByWeight() != null && product.getIsSoldByWeight())
            .weightUnit(product.getWeightUnit())
            .build();
    }

    public static BarcodeLookupResult variant(ProductVariant variant) {
        return BarcodeLookupResult.builder()
            .found(true)
            .type(LookupType.VARIANT)
            .productId(variant.getProduct().getId())
            .variantId(variant.getId())
            .name(variant.getName())
            .price(variant.getPrice())
            .sku(variant.getSku())
            .barcode(variant.getBarcode())
            .available(variant.getIsAvailable() != null && variant.getIsAvailable())
            .build();
    }

    public static BarcodeLookupResult notFound(String code) {
        return BarcodeLookupResult.builder()
            .found(false)
            .searchedCode(code)
            .type(LookupType.NOT_FOUND)
            .build();
    }
}
