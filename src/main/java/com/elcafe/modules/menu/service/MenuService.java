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
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

        List<Category> categories = categoryRepository.findByRestaurantIdAndActiveTrueOrderBySortOrder(restaurantId);

        // Convert to DTOs with products
        return categories.stream()
                .map(this::convertToCategoryDTO)
                .collect(Collectors.toList());
    }

    private PublicMenuCategoryDTO convertToCategoryDTO(Category category) {
        // Get products for this category
        List<Product> products = productRepository.findByCategoryIdAndStatusOrderBySortOrder(
                category.getId(), ProductStatus.LIVE);

        List<PublicMenuProductDTO> productDTOs = products.stream()
                .filter(Product::getInStock)
                .map(this::convertToProductDTO)
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
                .products(productDTOs)
                .build();
    }

    private PublicMenuProductDTO convertToProductDTO(Product product) {
        return PublicMenuProductDTO.builder()
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
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
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

        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    @Transactional(readOnly = true)
    public List<Category> getCategoriesByRestaurant(Long restaurantId) {
        return categoryRepository.findByRestaurantIdOrderBySortOrder(restaurantId);
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

        product.setName(productData.getName());
        product.setDescription(productData.getDescription());
        product.setImageUrl(productData.getImageUrl());
        product.setPrice(productData.getPrice());
        product.setSortOrder(productData.getSortOrder());
        product.setStatus(productData.getStatus());
        product.setInStock(productData.getInStock());
        product.setFeatured(productData.getFeatured());

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
        List<Product> products = productRepository.findByRestaurantIdAndStatus(restaurantId, ProductStatus.LIVE);
        return products.stream()
                .map(this::convertToProductListDTO)
                .collect(Collectors.toList());
    }

    private ProductListDTO convertToProductListDTO(Product product) {
        return ProductListDTO.builder()
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
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .available(product.getInStock()) // For frontend compatibility
                .isFeatured(product.getFeatured()) // For frontend compatibility
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
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
        return addOnGroupRepository.findByRestaurantIdAndActiveTrue(restaurantId);
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
