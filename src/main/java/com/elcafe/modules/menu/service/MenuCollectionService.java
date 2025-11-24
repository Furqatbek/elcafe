package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.*;
import com.elcafe.modules.menu.entity.*;
import com.elcafe.modules.menu.repository.*;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuCollectionService {

    private final MenuCollectionRepository menuCollectionRepository;
    private final MenuCollectionItemRepository menuCollectionItemRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public Page<MenuCollectionDTO> getMenuCollections(Long restaurantId, Pageable pageable) {
        return menuCollectionRepository.findByRestaurantId(restaurantId, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public MenuCollectionDTO getMenuCollectionById(Long id) {
        MenuCollection collection = menuCollectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu collection not found with id: " + id));
        return toDTO(collection);
    }

    @Transactional(readOnly = true)
    public List<MenuCollectionDTO> getActiveMenuCollections(Long restaurantId) {
        return menuCollectionRepository.findActiveMenuCollections(restaurantId, LocalDate.now())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public MenuCollectionDTO createMenuCollection(CreateMenuCollectionRequest request) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        MenuCollection collection = MenuCollection.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(true)
                .build();

        MenuCollection saved = menuCollectionRepository.save(collection);

        if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            addProductsToCollection(saved.getId(), request.getProductIds());
        }

        log.info("Created menu collection: {}", saved.getName());
        return toDTO(menuCollectionRepository.findById(saved.getId()).orElseThrow());
    }

    @Transactional
    public void addProductsToCollection(Long collectionId, List<Long> productIds) {
        MenuCollection collection = menuCollectionRepository.findById(collectionId)
                .orElseThrow(() -> new RuntimeException("Menu collection not found"));

        int sortOrder = 0;
        for (Long productId : productIds) {
            if (!menuCollectionItemRepository.existsByMenuCollectionIdAndProductId(collectionId, productId)) {
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new RuntimeException("Product not found"));

                MenuCollectionItem item = MenuCollectionItem.builder()
                        .menuCollection(collection)
                        .product(product)
                        .sortOrder(sortOrder++)
                        .isFeatured(false)
                        .build();

                menuCollectionItemRepository.save(item);
            }
        }
        log.info("Added {} products to menu collection: {}", productIds.size(), collection.getName());
    }

    @Transactional
    public void deleteMenuCollection(Long id) {
        MenuCollection collection = menuCollectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu collection not found"));

        menuCollectionRepository.delete(collection);
        log.info("Deleted menu collection: {}", collection.getName());
    }

    private MenuCollectionDTO toDTO(MenuCollection collection) {
        List<MenuCollectionItemDTO> items = collection.getItems().stream()
                .map(item -> MenuCollectionItemDTO.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .productImageUrl(item.getProduct().getImageUrl())
                        .sortOrder(item.getSortOrder())
                        .isFeatured(item.getIsFeatured())
                        .build())
                .toList();

        return MenuCollectionDTO.builder()
                .id(collection.getId())
                .restaurantId(collection.getRestaurant().getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .imageUrl(collection.getImageUrl())
                .isActive(collection.getIsActive())
                .startDate(collection.getStartDate())
                .endDate(collection.getEndDate())
                .sortOrder(collection.getSortOrder())
                .items(items)
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .build();
    }
}
