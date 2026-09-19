package com.elcafe.modules.menu.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.ProductListDTO;
import com.elcafe.modules.menu.dto.PublicMenuCategoryDTO;
import com.elcafe.modules.menu.dto.PublicMenuProductDTO;
import com.elcafe.modules.menu.entity.*;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.AddOnGroupRepository;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.partner.outbox.PartnerMenuNotifier;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AddOnGroupRepository addOnGroupRepository;
    private final RestaurantRepository restaurantRepository;
    private final PartnerMenuNotifier partnerMenuNotifier;

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
        // Captured before the setters run: a partner holding a cached menu needs telling only when
        // the number actually moved, not on every save of an unrelated field.
        BigDecimal previousPrice = product.getPrice();
        ProductStatus previousStatus = product.getStatus();
        Boolean previousInStock = product.getInStock();

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

        Product saved = productRepository.save(product);

        // Whether it is on their menu at all comes first: an item withdrawn does not need a price.
        if (previousStatus != saved.getStatus() || !Objects.equals(previousInStock, saved.getInStock())) {
            partnerMenuNotifier.productAvailabilityChanged(saved);
        }
        if (previousPrice == null || saved.getPrice() == null
                ? previousPrice != saved.getPrice()
                : previousPrice.compareTo(saved.getPrice()) != 0) {
            // compareTo, not equals: 30000 and 30000.00 are the same price to a customer and differ
            // only in how the column was written, and a partner does not want waking for that.
            partnerMenuNotifier.productPriceChanged(saved);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Product updateProductStock(Long id, Boolean inStock) {
        log.info("Updating product stock: {} to {}", id, inStock);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        boolean changed = !Objects.equals(product.getInStock(), inStock);
        product.setInStock(inStock);
        Product saved = productRepository.save(product);
        if (changed) {
            // The manual 86. Until now only ingredients reaching zero told a partner anything, so a
            // manager switching an item off left it on sale everywhere but here.
            partnerMenuNotifier.productAvailabilityChanged(saved);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public Product updateProductStatus(Long id, ProductStatus status) {
        log.info("Updating product status: {} to {}", id, status);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        boolean changed = product.getStatus() != status;
        product.setStatus(status);
        Product saved = productRepository.save(product);
        if (changed) {
            // Withdrawing an item is the closest we have to deleting it on their side — their importer
            // has no deletion path, so an item that merely vanished from our menu would stay orderable
            // on theirs. Saying "unavailable" out loud is what actually takes it off sale.
            partnerMenuNotifier.productAvailabilityChanged(saved);
        }
        return saved;
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
                                .recipeAvailable(product.getRecipeAvailable())
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

    @Transactional
    @CacheEvict(value = "menu", allEntries = true)
    public void deleteProduct(Long id) {
        log.info("Deleting product: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        productRepository.delete(product);
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
