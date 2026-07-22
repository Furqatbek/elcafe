package com.elcafe.modules.selfservice.exception;

/**
 * Thrown when a product or bundle is not found.
 */
public class ProductNotFoundException extends SelfServiceException {

    private final Long productId;
    private final boolean isBundle;

    public ProductNotFoundException(Long productId) {
        super("Product not found: " + productId);
        this.productId = productId;
        this.isBundle = false;
    }

    public ProductNotFoundException(Long id, boolean isBundle) {
        super((isBundle ? "Bundle" : "Product") + " not found: " + id);
        this.productId = id;
        this.isBundle = isBundle;
    }

    public Long getProductId() {
        return productId;
    }

    public boolean isBundle() {
        return isBundle;
    }
}
