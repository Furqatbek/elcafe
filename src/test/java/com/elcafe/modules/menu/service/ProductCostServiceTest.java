package com.elcafe.modules.menu.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.repository.ProductionBatchRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCostServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @Mock private ProductionBatchRepository productionBatchRepository;
    @InjectMocks private ProductCostService productCostService;

    private Product product;
    private Ingredient flour;
    private Ingredient butter;
    private ProductIngredient piFlour;
    private ProductIngredient piButter;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Bread");
        product.setCostPrice(BigDecimal.ZERO);

        flour = Ingredient.builder()
                .id(1L).name("Flour").unit("kg")
                .costPerUnit(new BigDecimal("5000"))
                .currentStock(new BigDecimal("100"))
                .build();

        butter = Ingredient.builder()
                .id(2L).name("Butter").unit("kg")
                .costPerUnit(new BigDecimal("15000"))
                .currentStock(new BigDecimal("50"))
                .build();

        piFlour = ProductIngredient.builder()
                .id(1L).product(product).ingredient(flour)
                .quantityRequired(new BigDecimal("0.5")).unit("kg").build();

        piButter = ProductIngredient.builder()
                .id(2L).product(product).ingredient(butter)
                .quantityRequired(new BigDecimal("0.1")).unit("kg").build();
    }

    @Test @DisplayName("recalculateProductCost — updates product costPrice")
    void recalculateProductCost_success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productIngredientRepository.findByProductIdWithIngredients(1L))
                .thenReturn(List.of(piFlour, piButter));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        BigDecimal result = productCostService.recalculateProductCost(1L);

        // Flour: 0.5 * 5000 = 2500, Butter: 0.1 * 15000 = 1500, Total = 4000
        assertThat(result).isEqualByComparingTo("4000");
        assertThat(product.getCostPrice()).isEqualByComparingTo("4000");
        verify(productRepository).save(product);
    }

    @Test @DisplayName("recalculateProductCost — refuses a cost more than 10x selling price")
    void recalculateProductCost_refusesInsaneCost() {
        // Selling price 25_000, sane cost ≤ 250_000; but a bad batch yield
        // pushes the calculated cost to 2_600_000.
        product.setPrice(new BigDecimal("25000"));
        product.setCostPrice(new BigDecimal("8000"));
        product.setUsesProductionBatch(true);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productionBatchRepository.getAverageCostPerUnit(1L))
                .thenReturn(new BigDecimal("2600000"));

        BigDecimal result = productCostService.recalculateProductCost(1L);

        // Keeps the old cost and refuses to persist the runaway value.
        assertThat(result).isEqualByComparingTo("8000");
        assertThat(product.getCostPrice()).isEqualByComparingTo("8000");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test @DisplayName("calculateCostFromIngredients — sums ingredient costs")
    void calculateCostFromIngredients_sumsCosts() {
        when(productIngredientRepository.findByProductIdWithIngredients(1L))
                .thenReturn(List.of(piFlour, piButter));

        BigDecimal result = productCostService.calculateCostFromIngredients(1L);

        assertThat(result).isEqualByComparingTo("4000");
    }

    @Test @DisplayName("calculateCostFromIngredients — returns zero when no ingredients")
    void calculateCostFromIngredients_noIngredients() {
        when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of());

        BigDecimal result = productCostService.calculateCostFromIngredients(1L);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test @DisplayName("recalculateProductsUsingIngredient — batch updates")
    void recalculateProductsUsingIngredient_updatesAll() {
        ProductIngredient usage = ProductIngredient.builder()
                .id(1L).product(product).ingredient(flour)
                .quantityRequired(new BigDecimal("0.5")).build();
        when(productIngredientRepository.findByIngredientIdWithProduct(1L)).thenReturn(List.of(usage));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(piFlour));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        int updated = productCostService.recalculateProductsUsingIngredient(1L);

        assertThat(updated).isEqualTo(1);
        verify(productRepository).save(product);
    }

    @Test @DisplayName("recalculateAllProductCosts — full recalculation")
    void recalculateAllProductCosts_updatesAll() {
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productIngredientRepository.findByProductIdInWithIngredients(Set.of(1L)))
                .thenReturn(List.of(piFlour, piButter));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        int updated = productCostService.recalculateAllProductCosts();

        assertThat(updated).isEqualTo(1);
    }

    @Test @DisplayName("recalculateProductCost — uses production batch avg cost when flag set")
    void recalculateProductCost_productionBatch() {
        Product batchProduct = new Product();
        batchProduct.setId(2L);
        batchProduct.setName("Shurva");
        batchProduct.setCostPrice(BigDecimal.ZERO);
        batchProduct.setUsesProductionBatch(true);

        when(productRepository.findById(2L)).thenReturn(Optional.of(batchProduct));
        when(productionBatchRepository.getAverageCostPerUnit(2L)).thenReturn(new BigDecimal("30000"));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        BigDecimal result = productCostService.recalculateProductCost(2L);

        assertThat(result).isEqualByComparingTo("30000");
        assertThat(batchProduct.getCostPrice()).isEqualByComparingTo("30000");
        verify(productionBatchRepository).getAverageCostPerUnit(2L);
        verify(productIngredientRepository, never()).findByProductIdWithIngredients(2L);
    }

    @Test @DisplayName("getCostBreakdown — returns per-ingredient details")
    void getCostBreakdown_returnsDetails() {
        when(productIngredientRepository.findByProductIdWithIngredients(1L))
                .thenReturn(List.of(piFlour, piButter));

        List<ProductCostService.IngredientCostBreakdown> result =
                productCostService.getCostBreakdown(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).ingredientName()).isEqualTo("Flour");
        assertThat(result.get(0).totalCost()).isEqualByComparingTo("2500");
        assertThat(result.get(1).ingredientName()).isEqualTo("Butter");
        assertThat(result.get(1).totalCost()).isEqualByComparingTo("1500");
    }
}
