package com.elcafe.modules.pos.barcode.service;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.pos.barcode.dto.BarcodeLookupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for barcode/SKU lookup functionality.
 * Supports scanning products by barcode, SKU, or UPC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    /**
     * Look up a product by barcode or SKU.
     * Checks products first, then variants.
     */
    public BarcodeLookupResult lookup(Long restaurantId, String code) {
        // Try to find product by barcode
        Optional<Product> productByBarcode = productRepository.findByRestaurantIdAndBarcode(restaurantId, code);
        if (productByBarcode.isPresent()) {
            Product product = productByBarcode.get();
            log.debug("Found product by barcode: {} -> {}", code, product.getName());
            return BarcodeLookupResult.product(product);
        }

        // Try to find product by SKU
        Optional<Product> productBySku = productRepository.findByRestaurantIdAndSku(restaurantId, code);
        if (productBySku.isPresent()) {
            Product product = productBySku.get();
            log.debug("Found product by SKU: {} -> {}", code, product.getName());
            return BarcodeLookupResult.product(product);
        }

        // Try to find variant by barcode
        Optional<ProductVariant> variantByBarcode = variantRepository.findByProductRestaurantIdAndBarcode(restaurantId, code);
        if (variantByBarcode.isPresent()) {
            ProductVariant variant = variantByBarcode.get();
            log.debug("Found variant by barcode: {} -> {}", code, variant.getName());
            return BarcodeLookupResult.variant(variant);
        }

        // Try to find variant by SKU
        Optional<ProductVariant> variantBySku = variantRepository.findByProductRestaurantIdAndSku(restaurantId, code);
        if (variantBySku.isPresent()) {
            ProductVariant variant = variantBySku.get();
            log.debug("Found variant by SKU: {} -> {}", code, variant.getName());
            return BarcodeLookupResult.variant(variant);
        }

        log.debug("No product found for code: {}", code);
        return BarcodeLookupResult.notFound(code);
    }

    /**
     * Check if a barcode/SKU exists.
     */
    public boolean exists(Long restaurantId, String code) {
        return productRepository.existsByRestaurantIdAndBarcodeOrSku(restaurantId, code) ||
               variantRepository.existsByProductRestaurantIdAndBarcodeOrSku(restaurantId, code);
    }
}
