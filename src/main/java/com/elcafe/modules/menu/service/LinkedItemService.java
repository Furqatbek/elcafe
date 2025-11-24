package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.*;
import com.elcafe.modules.menu.entity.*;
import com.elcafe.modules.menu.enums.LinkType;
import com.elcafe.modules.menu.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkedItemService {

    private final LinkedItemRepository linkedItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<LinkedItemDTO> getLinkedItems(Long productId) {
        return linkedItemRepository.findByProductIdOrderBySortOrder(productId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkedItemDTO> getLinkedItemsByType(Long productId, LinkType linkType) {
        return linkedItemRepository.findByProductIdAndLinkTypeOrderBySortOrder(productId, linkType)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public LinkedItemDTO addLinkedItem(Long productId, AddLinkedItemRequest request) {
        if (productId.equals(request.getLinkedProductId())) {
            throw new RuntimeException("Cannot link product to itself");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Product linkedProduct = productRepository.findById(request.getLinkedProductId())
                .orElseThrow(() -> new RuntimeException("Linked product not found"));

        if (linkedItemRepository.existsByProductIdAndLinkedProductId(productId, request.getLinkedProductId())) {
            throw new RuntimeException("Products are already linked");
        }

        LinkedItem linkedItem = LinkedItem.builder()
                .product(product)
                .linkedProduct(linkedProduct)
                .linkType(request.getLinkType())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        LinkedItem saved = linkedItemRepository.save(linkedItem);
        log.info("Added linked item: {} -> {}", product.getName(), linkedProduct.getName());
        return toDTO(saved);
    }

    @Transactional
    public void deleteLinkedItem(Long id) {
        LinkedItem linkedItem = linkedItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Linked item not found"));

        linkedItemRepository.delete(linkedItem);
        log.info("Deleted linked item: {}", id);
    }

    private LinkedItemDTO toDTO(LinkedItem linkedItem) {
        return LinkedItemDTO.builder()
                .id(linkedItem.getId())
                .productId(linkedItem.getProduct().getId())
                .linkedProductId(linkedItem.getLinkedProduct().getId())
                .linkedProductName(linkedItem.getLinkedProduct().getName())
                .linkedProductImageUrl(linkedItem.getLinkedProduct().getImageUrl())
                .linkType(linkedItem.getLinkType())
                .sortOrder(linkedItem.getSortOrder())
                .build();
    }
}
