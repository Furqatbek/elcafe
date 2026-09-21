package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.CreateProductVariantRequest;
import com.elcafe.modules.menu.dto.ProductVariantResponse;
import com.elcafe.modules.menu.dto.UpdateProductVariantRequest;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private ProductVariantService productVariantService;

    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Burger");

        variant = ProductVariant.builder()
                .id(1L).product(product).name("Large")
                .description("Large size").price(new BigDecimal("35000"))
                .inStock(true).sortOrder(0).build();
    }

    @Test @DisplayName("getVariantsByProduct — returns list")
    void getVariantsByProduct_returnsList() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(variant));

        List<ProductVariantResponse> result = productVariantService.getAllVariantsByProduct(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Large");
    }

    @Test @DisplayName("getVariantsByProduct paginated — returns page")
    void getVariantsByProduct_returnsPage() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productVariantRepository.findByProductId(eq(1L), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(variant)));

        var result = productVariantService.getAllVariantsByProduct(1L, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("searchVariants — searches by query")
    void searchVariants_returnsResults() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productVariantRepository.searchVariantsByProduct(eq(1L), eq("Large"), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(variant)));

        var result = productVariantService.searchVariants(1L, "Large", org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Large");
    }

    @Test @DisplayName("getInStockVariants — filters out-of-stock")
    void getActiveVariants_filtersInactive() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productVariantRepository.findByProductIdAndInStock(1L, true)).thenReturn(List.of(variant));

        List<ProductVariantResponse> result = productVariantService.getInStockVariants(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInStock()).isTrue();
    }

    @Test @DisplayName("getVariantById — found")
    void getVariantById_found() {
        when(productVariantRepository.findByIdAndProductId(1L, 1L)).thenReturn(Optional.of(variant));

        ProductVariantResponse result = productVariantService.getVariantById(1L, 1L);

        assertThat(result.getName()).isEqualTo("Large");
        assertThat(result.getPrice()).isEqualByComparingTo("35000");
    }

    @Test @DisplayName("getVariantById — not found throws")
    void getVariantById_notFound_throws() {
        when(productVariantRepository.findByIdAndProductId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productVariantService.getVariantById(1L, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("variant not found");
    }

    @Test @DisplayName("createVariant — success")
    void createVariant_success() {
        CreateProductVariantRequest request = new CreateProductVariantRequest();
        request.setName("Small");
        request.setPrice(new BigDecimal("25000"));
        request.setInStock(true);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsByProductIdAndName(1L, "Small")).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(i -> {
            ProductVariant saved = i.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        ProductVariantResponse result = productVariantService.createVariant(1L, request);

        assertThat(result.getName()).isEqualTo("Small");
        verify(productVariantRepository).save(any(ProductVariant.class));
    }

    @Test @DisplayName("updateVariant — success")
    void updateVariant_success() {
        UpdateProductVariantRequest request = new UpdateProductVariantRequest();
        request.setName("Extra Large");
        request.setPrice(new BigDecimal("45000"));

        when(productVariantRepository.findByIdAndProductId(1L, 1L)).thenReturn(Optional.of(variant));
        when(productVariantRepository.existsByProductIdAndName(1L, "Extra Large")).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(i -> i.getArgument(0));

        ProductVariantResponse result = productVariantService.updateVariant(1L, 1L, request);

        assertThat(result.getName()).isEqualTo("Extra Large");
        assertThat(result.getPrice()).isEqualByComparingTo("45000");
    }

    @Test @DisplayName("deleteVariant — success")
    void deleteVariant_success() {
        when(productVariantRepository.findByIdAndProductId(1L, 1L)).thenReturn(Optional.of(variant));

        productVariantService.deleteVariant(1L, 1L);

        verify(productVariantRepository).delete(variant);
    }

    @Test @DisplayName("createVariant — product not found throws")
    void createVariant_productNotFound_throws() {
        CreateProductVariantRequest request = new CreateProductVariantRequest();
        request.setName("Medium");
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productVariantService.createVariant(99L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product not found");
    }
}
