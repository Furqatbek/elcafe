package com.elcafe.modules.pos.barcode.service;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.pos.barcode.dto.BarcodeLookupResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarcodeServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @InjectMocks private BarcodeService barcodeService;

    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Steak");
        product.setPrice(new BigDecimal("80000"));
        product.setBarcode("1234567890");
        product.setSku("STK-001");
        product.setInStock(true);

        variant = ProductVariant.builder()
                .id(1L).product(product).name("Large Steak")
                .price(new BigDecimal("100000")).inStock(true)
                .barcode("VAR-1234").build();
    }

    @Test @DisplayName("lookup — finds product by barcode")
    void lookupByBarcode_product_found() {
        when(productRepository.findByRestaurantIdAndBarcode(1L, "1234567890"))
                .thenReturn(Optional.of(product));

        BarcodeLookupResult result = barcodeService.lookup(1L, "1234567890");

        assertThat(result.isFound()).isTrue();
        assertThat(result.getName()).isEqualTo("Steak");
        assertThat(result.getProductId()).isEqualTo(1L);
    }

    @Test @DisplayName("lookup — falls back to variant barcode")
    void lookupByBarcode_variant_found() {
        when(productRepository.findByRestaurantIdAndBarcode(1L, "VAR-1234")).thenReturn(Optional.empty());
        when(productRepository.findByRestaurantIdAndSku(1L, "VAR-1234")).thenReturn(Optional.empty());
        when(variantRepository.findByProductRestaurantIdAndBarcode(1L, "VAR-1234"))
                .thenReturn(Optional.of(variant));

        BarcodeLookupResult result = barcodeService.lookup(1L, "VAR-1234");

        assertThat(result.isFound()).isTrue();
        assertThat(result.getName()).isEqualTo("Large Steak");
        assertThat(result.getVariantId()).isEqualTo(1L);
    }

    @Test @DisplayName("lookup — not found returns notFound result")
    void lookupByBarcode_notFound() {
        when(productRepository.findByRestaurantIdAndBarcode(1L, "UNKNOWN")).thenReturn(Optional.empty());
        when(productRepository.findByRestaurantIdAndSku(1L, "UNKNOWN")).thenReturn(Optional.empty());
        when(variantRepository.findByProductRestaurantIdAndBarcode(1L, "UNKNOWN")).thenReturn(Optional.empty());
        when(variantRepository.findByProductRestaurantIdAndSku(1L, "UNKNOWN")).thenReturn(Optional.empty());

        BarcodeLookupResult result = barcodeService.lookup(1L, "UNKNOWN");

        assertThat(result.isFound()).isFalse();
    }

    @Test @DisplayName("lookup — finds product by SKU")
    void lookupBySku_found() {
        when(productRepository.findByRestaurantIdAndBarcode(1L, "STK-001")).thenReturn(Optional.empty());
        when(productRepository.findByRestaurantIdAndSku(1L, "STK-001")).thenReturn(Optional.of(product));

        BarcodeLookupResult result = barcodeService.lookup(1L, "STK-001");

        assertThat(result.isFound()).isTrue();
        assertThat(result.getName()).isEqualTo("Steak");
    }

    @Test @DisplayName("exists — returns true when barcode exists")
    void exists_true() {
        when(productRepository.existsByRestaurantIdAndBarcodeOrSku(1L, "1234567890")).thenReturn(true);

        assertThat(barcodeService.exists(1L, "1234567890")).isTrue();
    }

    @Test @DisplayName("exists — returns false when not found")
    void exists_false() {
        when(productRepository.existsByRestaurantIdAndBarcodeOrSku(1L, "NONE")).thenReturn(false);
        when(variantRepository.existsByProductRestaurantIdAndBarcodeOrSku(1L, "NONE")).thenReturn(false);

        assertThat(barcodeService.exists(1L, "NONE")).isFalse();
    }
}
