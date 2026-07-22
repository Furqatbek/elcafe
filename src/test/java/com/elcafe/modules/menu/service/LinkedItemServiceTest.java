package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.AddLinkedItemRequest;
import com.elcafe.modules.menu.dto.LinkedItemDTO;
import com.elcafe.modules.menu.entity.LinkedItem;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.LinkType;
import com.elcafe.modules.menu.repository.LinkedItemRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkedItemServiceTest {

    @Mock private LinkedItemRepository linkedItemRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private LinkedItemService linkedItemService;

    private Product product;
    private Product linkedProduct;
    private LinkedItem linkedItem;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Steak");

        linkedProduct = new Product();
        linkedProduct.setId(2L);
        linkedProduct.setName("Wine");
        linkedProduct.setImageUrl("wine.jpg");

        linkedItem = LinkedItem.builder()
                .id(1L).product(product).linkedProduct(linkedProduct)
                .linkType(LinkType.RECOMMENDED).sortOrder(0).build();
    }

    @Test @DisplayName("getLinkedItems — returns all for product")
    void getLinkedItems_returnsList() {
        when(linkedItemRepository.findByProductIdOrderBySortOrder(1L)).thenReturn(List.of(linkedItem));

        List<LinkedItemDTO> result = linkedItemService.getLinkedItems(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLinkedProductName()).isEqualTo("Wine");
        assertThat(result.get(0).getLinkType()).isEqualTo(LinkType.RECOMMENDED);
    }

    @Test @DisplayName("getLinkedItemsByType — filters by type")
    void getLinkedItemsByType_filtersType() {
        when(linkedItemRepository.findByProductIdAndLinkTypeOrderBySortOrder(1L, LinkType.RECOMMENDED))
                .thenReturn(List.of(linkedItem));

        List<LinkedItemDTO> result = linkedItemService.getLinkedItemsByType(1L, LinkType.RECOMMENDED);

        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("addLinkedItem — success")
    void addLinkedItem_success() {
        AddLinkedItemRequest request = new AddLinkedItemRequest();
        request.setLinkedProductId(2L);
        request.setLinkType(LinkType.UPSELL);
        request.setSortOrder(1);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.findById(2L)).thenReturn(Optional.of(linkedProduct));
        when(linkedItemRepository.existsByProductIdAndLinkedProductId(1L, 2L)).thenReturn(false);
        when(linkedItemRepository.save(any(LinkedItem.class))).thenAnswer(i -> {
            LinkedItem saved = i.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        LinkedItemDTO result = linkedItemService.addLinkedItem(1L, request);

        assertThat(result.getLinkType()).isEqualTo(LinkType.UPSELL);
        verify(linkedItemRepository).save(any(LinkedItem.class));
    }

    @Test @DisplayName("addLinkedItem — self-link throws")
    void addLinkedItem_selfLink_throws() {
        AddLinkedItemRequest request = new AddLinkedItemRequest();
        request.setLinkedProductId(1L);

        assertThatThrownBy(() -> linkedItemService.addLinkedItem(1L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot link product to itself");
    }

    @Test @DisplayName("addLinkedItem — duplicate throws")
    void addLinkedItem_duplicate_throws() {
        AddLinkedItemRequest request = new AddLinkedItemRequest();
        request.setLinkedProductId(2L);
        request.setLinkType(LinkType.RECOMMENDED);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.findById(2L)).thenReturn(Optional.of(linkedProduct));
        when(linkedItemRepository.existsByProductIdAndLinkedProductId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> linkedItemService.addLinkedItem(1L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already linked");
    }

    @Test @DisplayName("deleteLinkedItem — success")
    void deleteLinkedItem_success() {
        when(linkedItemRepository.findById(1L)).thenReturn(Optional.of(linkedItem));

        linkedItemService.deleteLinkedItem(1L);

        verify(linkedItemRepository).delete(linkedItem);
    }
}
