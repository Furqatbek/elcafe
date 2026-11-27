package com.elcafe.modules.menu.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ConflictException;
import com.elcafe.exception.ConstraintViolations;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.ProductListDTO;
import com.elcafe.modules.menu.dto.PublicMenuCategoryDTO;
import com.elcafe.modules.menu.dto.PublicMenuProductDTO;
import com.elcafe.modules.menu.entity.*;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.AddOnGroupRepository;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AddOnGroupRepository addOnGroupRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "menu", key = "#restaurantId")
    public List<PublicMenuCategoryDTO> getPublicMenu(Long restaurantId) {
        log.info("Fetching public menu for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        if (!restaurant.getActive()) {
            throw new ResourceNotFoundException("Restaurant is not active");
        }

        List<Category> categories = categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(restaurantId);

        return categories.stream()
                .map(category -> {
                    List<PublicMenuProductDTO> products = category.getProducts().stream()
                            .filter(product -> product.getInStock() != null && product.getInStock())
                            .map(product -> PublicMenuProductDTO.builder()
                                    .id(product.getId())
                                    .name(product.getName())
                                    .description(product.getDescription())
                                    .imageUrl(product.getImageUrl())
                                    .price(product.getPrice())
                                    .priceWithMargin(product.getPriceWithMargin())
                                    .itemType(product.getItemType())
                                    .sortOrder(product.getSortOrder())
                                    .status(product.getStatus())
                                    .inStock(product.getInStock())
                                    .featured(product.getFeatured())
                                    .hasVariants(product.getHasVariants())
                                    .isSoldByWeight(product.getIsSoldByWeight())
                                    .weightUnit(product.getWeightUnit())
                                    .minWeight(product.getMinWeight())
                                    .maxWeight(product.getMaxWeight())
                                    .createdAt(product.getCreatedAt())
                                    .updatedAt(product.getUpdatedAt())
                                    .build())
                            .collect(Collectors.toList());

                    return PublicMenuCategoryDTO.builder()
                            .id(category.getId())
                            .name(category.getName())
                            .description(category.getDescription())
                            .imageUrl(category.getImageUrl())
                            .sortOrder(category.getSortOrder())
                            .active(category.getActive())
                            .createdAt(category.getCreatedAt())
                            .updatedAt(category.getUpdatedAt())
                            .products(products)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Category createCategory(Category category) {
        log.info("Creating category: {}", category.getName());
        return categoryRepository.save(category);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Category updateCategory(Long id, Category categoryData) {
        log.info("Updating category: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        category.setName(categoryData.getName());
        category.setDescription(categoryData.getDescription());
        category.setImageUrl(categoryData.getImageUrl());
        category.setSortOrder(categoryData.getSortOrder());
        category.setActive(categoryData.getActive());
        category.setKitchenStation(categoryData.getKitchenStation());

        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    @Transactional(readOnly = true)
    public List<Category> getCategoriesByRestaurant(Long restaurantId) {
        return categoryRepository.findByRestaurant_IdOrderBySortOrder(restaurantId);
    }

    @Transactional(readOnly = true)
    public List<Category> getActiveCategoriesByRestaurant(Long restaurantId) {
        log.info("Fetching active categories for restaurant: {}", restaurantId);
        return categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(restaurantId);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public void deleteCategory(Long id) {
        log.info("Deleting category: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // products.category_id is ON DELETE CASCADE and each product's recipe rows
        // cascade from the product, so deleting a category that still holds
        // products silently destroys those products AND every recipe attached to
        // them. Refuse: the caller's intent cannot be satisfied safely.
        int productCount = productRepository.findByCategoryIdOrderBySortOrder(id).size();
        if (productCount > 0) {
            throw new BadRequestException(String.format(
                    "Category '%s' still has %d product(s). Deleting it would also delete those "
                            + "products and their recipes. Move or delete the products first.",
                    category.getName(), productCount));
        }

        categoryRepository.delete(category);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Product createProduct(Product product) {
        log.info("Creating product: {}", product.getName());
        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Product updateProduct(Long id, Product productData) {
        log.info("Updating product: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Update all fields
        if (productData.getCategory() != null) {
            product.setCategory(productData.getCategory());
        }
        product.setName(productData.getName());
        product.setDescription(productData.getDescription());
        product.setImageUrl(productData.getImageUrl());
        product.setPrice(productData.getPrice());
        product.setCostPrice(productData.getCostPrice());
        product.setItemType(productData.getItemType());
        product.setSortOrder(productData.getSortOrder());
        product.setStatus(productData.getStatus());
        product.setInStock(productData.getInStock());
        product.setFeatured(productData.getFeatured());
        product.setHasVariants(productData.getHasVariants());
        product.setIsSoldByWeight(productData.getIsSoldByWeight());
        product.setWeightUnit(productData.getWeightUnit());
        product.setMinWeight(productData.getMinWeight());
        product.setMaxWeight(productData.getMaxWeight());

        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Product updateProductStock(Long id, Boolean inStock) {
        log.info("Updating product stock: {} to {}", id, inStock);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        product.setInStock(inStock);
        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Product updateProductStatus(Long id, ProductStatus status) {
        log.info("Updating product status: {} to {}", id, status);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        product.setStatus(status);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryIdOrderBySortOrder(categoryId);
    }

    @Transactional(readOnly = true)
    public List<ProductListDTO> getProductsByRestaurant(Long restaurantId) {
        log.info("Fetching products for restaurant: {}", restaurantId);

        List<Category> categories = categoryRepository.findByRestaurant_IdOrderBySortOrder(restaurantId);

        return categories.stream()
                .flatMap(category -> category.getProducts().stream()
                        .map(product -> ProductListDTO.builder()
                                .id(product.getId())
                                .name(product.getName())
                                .description(product.getDescription())
                                .imageUrl(product.getImageUrl())
                                .price(product.getPrice())
                                .priceWithMargin(product.getPriceWithMargin())
                                .costPrice(product.getCostPrice())
                                .marginPercentage(product.getMarginPercentage())
                                .itemType(product.getItemType())
                                .sortOrder(product.getSortOrder())
                                .status(product.getStatus())
                                .inStock(product.getInStock())
                                .featured(product.getFeatured())
                                .hasVariants(product.getHasVariants())
                                .categoryId(category.getId())
                                .categoryName(category.getName())
                                .available(product.getInStock())
                                .isFeatured(product.getFeatured())
                                .isSoldByWeight(product.getIsSoldByWeight())
                                .weightUnit(product.getWeightUnit())
                                .minWeight(product.getMinWeight())
                                .maxWeight(product.getMaxWeight())
                                .createdAt(product.getCreatedAt())
                                .updatedAt(product.getUpdatedAt())
                                .build()))
                .sorted((a, b) -> {
                    String nameA = a.getName() != null ? a.getName() : "";
                    String nameB = b.getName() != null ? b.getName() : "";
                    return nameA.compareToIgnoreCase(nameB);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Product toggleProductStatus(Long id) {
        log.info("Toggling status for product: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Toggle between DRAFT and LIVE
        if (product.getStatus() == ProductStatus.LIVE) {
            product.setStatus(ProductStatus.DRAFT);
        } else {
            product.setStatus(ProductStatus.LIVE);
        }

        return productRepository.save(product);
    }

    /**
     * Delete a product that nothing else refers to.
     *
     * This used to delete unconditionally, which failed in two different ways.
     * A product referenced by a production batch, a packaging rule, an employee
     * consumption or a promotion's free item is held by a foreign key with no
     * delete rule, so the database rejected the delete and the caller got a bare
     * 500. A product that had been sold was worse: order_items.product_id has no
     * foreign key at all, so the delete succeeded and silently orphaned the
     * sales history, leaving those lines with no cost price for the rest of time.
     *
     * Both cases are now refused with a 409 that says to deactivate the product
     * instead — setting isAvailable to false takes it off the menu and keeps the
     * history intact, which is what deleting a sold product was meant to achieve.
     */
    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public void deleteProduct(Long id) {
        log.info("Deleting product: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        long soldLines = productRepository.countOrderItemsReferencing(id);
        if (soldLines > 0) {
            log.warn("Refusing to delete product {} ({}): it appears on {} order line(s)",
                    id, product.getName(), soldLines);
            throw new ConflictException(String.format(
                    "\"%s\" has already been sold (%d order line%s) and cannot be deleted, "
                            + "because that would leave those sales with no product and no cost price. "
                            + "Mark it unavailable instead to take it off the menu.",
                    product.getName(), soldLines, soldLines == 1 ? "" : "s"));
        }

        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            String constraint = ConstraintViolations.nameOf(e);
            log.warn("Product {} ({}) is still referenced (constraint={}): {}",
                    id, product.getName(), constraint, e.getMostSpecificCause().getMessage());
            throw new ConflictException(String.format(
                    "\"%s\" cannot be deleted because %s still refers to it. "
                            + "Remove that first, or mark the product unavailable to take it "
                            + "off the menu instead.",
                    product.getName(), describeBlocker(constraint)));
        }
    }

    /**
     * Plain-language name for whatever is holding a product.
     *
     * The caller is a restaurant manager, not a DBA, so
     * "packaging_rules_product_id_fkey" tells them nothing about what to go and
     * change. These are the six foreign keys that reference products(id) with no
     * delete rule, i.e. the only ones that can block a delete; the rest either
     * cascade or null out. Constraint names here are all <table>_<column>_fkey,
     * so the table prefix identifies them. Anything unrecognised falls back to
     * the raw name rather than pretending to know.
     */
    private static String describeBlocker(String constraint) {
        if (constraint == null || constraint.isBlank()) {
            return "another record";
        }
        if (constraint.startsWith("packaging_rules")) {
            return "a packaging rule";
        }
        if (constraint.startsWith("production_batches")) {
            return "a production batch";
        }
        if (constraint.startsWith("employee_consumptions")) {
            return "an employee consumption record";
        }
        if (constraint.startsWith("promotions")) {
            return "a promotion (as its free item)";
        }
        if (constraint.startsWith("bundle_items") || constraint.startsWith("bundle_options")) {
            return "a bundle";
        }
        return "another record (" + constraint + ")";
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public AddOnGroup createAddOnGroup(AddOnGroup addOnGroup) {
        log.info("Creating add-on group: {}", addOnGroup.getName());
        return addOnGroupRepository.save(addOnGroup);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public AddOnGroup updateAddOnGroup(Long id, AddOnGroup addOnGroupData) {
        log.info("Updating add-on group: {}", id);

        AddOnGroup addOnGroup = addOnGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", id));

        addOnGroup.setName(addOnGroupData.getName());
        addOnGroup.setDescription(addOnGroupData.getDescription());
        addOnGroup.setRequired(addOnGroupData.getRequired());
        addOnGroup.setMinSelection(addOnGroupData.getMinSelection());
        addOnGroup.setMaxSelection(addOnGroupData.getMaxSelection());
        addOnGroup.setActive(addOnGroupData.getActive());

        return addOnGroupRepository.save(addOnGroup);
    }

    @Transactional(readOnly = true)
    public AddOnGroup getAddOnGroupById(Long id) {
        return addOnGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", id));
    }

    @Transactional(readOnly = true)
    public List<AddOnGroup> getAddOnGroupsByRestaurant(Long restaurantId) {
        return addOnGroupRepository.findByRestaurant_IdAndActiveTrue(restaurantId);
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public void deleteAddOnGroup(Long id) {
        log.info("Deleting add-on group: {}", id);
        AddOnGroup addOnGroup = addOnGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AddOnGroup", "id", id));
        addOnGroupRepository.delete(addOnGroup);
    }
}
