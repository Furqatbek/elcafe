package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.*;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<ProductVariantResponse> getAllVariantsByProduct(Long productId, Pageable pageable) {
        verifyProductExists(productId);
        return productVariantRepository.findByProductId(productId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getAllVariantsByProduct(Long productId) {
        verifyProductExists(productId);
        return productVariantRepository.findByProductId(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getVariantById(Long productId, Long variantId) {
        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new RuntimeException("Product variant not found with id: " + variantId + " for product: " + productId));
        return toResponse(variant);
    }

    @Transactional(readOnly = true)
    public Page<ProductVariantResponse> searchVariants(Long productId, String query, Pageable pageable) {
        verifyProductExists(productId);
        return productVariantRepository.searchVariantsByProduct(productId, query, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getInStockVariants(Long productId) {
        verifyProductExists(productId);
        return productVariantRepository.findByProductIdAndInStock(productId, true).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductVariantResponse createVariant(Long productId, CreateProductVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        if (productVariantRepository.existsByProductIdAndName(productId, request.getName())) {
            throw new RuntimeException("Variant with name '" + request.getName() + "' already exists for this product");
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .inStock(request.getInStock() != null ? request.getInStock() : true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        ProductVariant saved = productVariantRepository.save(variant);
        log.info("Created product variant: {} for product: {}", saved.getName(), productId);
        return toResponse(saved);
    }

    @Transactional
    public ProductVariantResponse updateVariant(Long productId, Long variantId, UpdateProductVariantRequest request) {
        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new RuntimeException("Product variant not found with id: " + variantId + " for product: " + productId));

        if (request.getName() != null) {
            if (!variant.getName().equals(request.getName()) &&
                productVariantRepository.existsByProductIdAndName(productId, request.getName())) {
                throw new RuntimeException("Variant with name '" + request.getName() + "' already exists for this product");
            }
            variant.setName(request.getName());
        }
        if (request.getDescription() != null) variant.setDescription(request.getDescription());
        if (request.getPrice() != null) variant.setPrice(request.getPrice());
        if (request.getInStock() != null) variant.setInStock(request.getInStock());
        if (request.getSortOrder() != null) variant.setSortOrder(request.getSortOrder());

        ProductVariant updated = productVariantRepository.save(variant);
        log.info("Updated product variant: {} for product: {}", updated.getName(), productId);
        return toResponse(updated);
    }

    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new RuntimeException("Product variant not found with id: " + variantId + " for product: " + productId));

        productVariantRepository.delete(variant);
        log.info("Deleted product variant: {} for product: {}", variant.getName(), productId);
    }

    private void verifyProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new RuntimeException("Product not found with id: " + productId);
        }
    }

    private ProductVariantResponse toResponse(ProductVariant variant) {
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct().getId())
                .productName(variant.getProduct().getName())
                .name(variant.getName())
                .description(variant.getDescription())
                .price(variant.getPrice())
                .inStock(variant.getInStock())
                .sortOrder(variant.getSortOrder())
                .build();
    }
}
