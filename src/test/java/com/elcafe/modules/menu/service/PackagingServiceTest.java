package com.elcafe.modules.menu.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.menu.entity.PackagingRule;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.QuantityMode;
import com.elcafe.modules.menu.repository.PackagingRuleRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PackagingServiceTest {

    @Mock private PackagingRuleRepository packagingRuleRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private PackagingService packagingService;

    private Ingredient bowl;
    private Ingredient spoon;
    private Ingredient bag;

    @BeforeEach
    void setUp() {
        bowl = Ingredient.builder().id(100L).name("Plastic Bowl").unit("pieces")
                .costPerUnit(BigDecimal.ZERO).currentStock(new BigDecimal("1000"))
                .trackInventory(true).active(true).version(0L).build();

        spoon = Ingredient.builder().id(101L).name("Spoon").unit("pieces")
                .costPerUnit(BigDecimal.ZERO).currentStock(new BigDecimal("1000"))
                .trackInventory(true).active(true).version(0L).build();

        bag = Ingredient.builder().id(102L).name("Bag").unit("pieces")
                .costPerUnit(new BigDecimal("500")).currentStock(new BigDecimal("500"))
                .trackInventory(true).active(true).version(0L).build();
    }

    private PackagingRule rule(Product product, Ingredient packaging, QuantityMode mode, int qty, boolean charge) {
        return PackagingRule.builder()
                .id((long) (Math.random() * 10000))
                .product(product)
                .packagingIngredient(packaging)
                .orderTypes("DELIVERY,TAKEAWAY")
                .quantityMode(mode)
                .autoAddQuantity(qty)
                .chargeToCustomer(charge)
                .active(true)
                .build();
    }

    private OrderItem orderItem(Long productId, String name, int qty) {
        return OrderItem.builder()
                .productId(productId)
                .productName(name)
                .quantity(qty)
                .unitPrice(new BigDecimal("25000"))
                .totalPrice(new BigDecimal("25000").multiply(BigDecimal.valueOf(qty)))
                .isPackagingItem(false)
                .build();
    }

    @Test @DisplayName("PER_ITEM: 3 soups → 3 bowls")
    void perItemMode() {
        Product soup = new Product(); soup.setId(1L); soup.setName("Soup");
        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of(rule(soup, bowl, QuantityMode.PER_ITEM, 1, false)));

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 3)), OrderType.DELIVERY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("Plastic Bowl");
        assertThat(result.get(0).getQuantity()).isEqualTo(3);
        assertThat(result.get(0).getIsPackagingItem()).isTrue();
    }

    @Test @DisplayName("PER_ORDER: 3 soups → 1 bag (not 3)")
    void perOrderMode() {
        Product soup = new Product(); soup.setId(1L); soup.setName("Soup");
        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of(rule(soup, bag, QuantityMode.PER_ORDER, 1, true)));

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 3)), OrderType.TAKEAWAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualTo(1);
    }

    @Test @DisplayName("FIXED: always adds configured quantity")
    void fixedMode() {
        Product soup = new Product(); soup.setId(1L); soup.setName("Soup");
        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of(rule(soup, spoon, QuantityMode.FIXED, 2, false)));

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 5)), OrderType.DELIVERY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualTo(2);
    }

    @Test @DisplayName("DINE_IN: no packaging added")
    void dineInExcluded() {
        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 1)), OrderType.DINE_IN);

        assertThat(result).isEmpty();
    }

    @Test @DisplayName("Deduplication: 2 products both add Bag (PER_ORDER) → only 1 bag")
    void perOrderDeduplication() {
        Product soup = new Product(); soup.setId(1L); soup.setName("Soup");
        Product burger = new Product(); burger.setId(2L); burger.setName("Burger");

        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of(rule(soup, bag, QuantityMode.PER_ORDER, 1, true)));
        when(packagingRuleRepository.findByProductIdAndActiveTrue(2L))
                .thenReturn(List.of(rule(burger, bag, QuantityMode.PER_ORDER, 1, true)));

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 1), orderItem(2L, "Burger", 1)),
                OrderType.DELIVERY);

        long bagCount = result.stream().filter(i -> i.getProductName().equals("Bag")).count();
        assertThat(bagCount).isEqualTo(1);
    }

    @Test @DisplayName("chargeToCustomer=true: price set from product")
    void chargeToCustomer() {
        Product soup = new Product(); soup.setId(1L); soup.setName("Soup");
        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of(rule(soup, bag, QuantityMode.PER_ORDER, 1, true)));

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 1)), OrderType.TAKEAWAY);

        assertThat(result.get(0).getUnitPrice()).isEqualByComparingTo("500");
        assertThat(result.get(0).getTotalPrice()).isEqualByComparingTo("500");
    }

    @Test @DisplayName("chargeToCustomer=false: price is zero")
    void freePackaging() {
        Product soup = new Product(); soup.setId(1L); soup.setName("Soup");
        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of(rule(soup, bowl, QuantityMode.PER_ITEM, 1, false)));

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 2)), OrderType.DELIVERY);

        assertThat(result.get(0).getUnitPrice()).isEqualByComparingTo("0");
    }

    @Test @DisplayName("Inactive rules: skipped")
    void inactiveRulesSkipped() {
        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of()); // findByProductIdAndActiveTrue returns empty for inactive

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 1)), OrderType.DELIVERY);

        assertThat(result).isEmpty();
    }

    @Test @DisplayName("Order type filter: DELIVERY-only rule skipped for TAKEAWAY")
    void orderTypeFilter() {
        Product soup = new Product(); soup.setId(1L); soup.setName("Soup");
        PackagingRule deliveryOnly = rule(soup, bowl, QuantityMode.PER_ITEM, 1, false);
        deliveryOnly.setOrderTypes("DELIVERY");
        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of(deliveryOnly));

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 1)), OrderType.TAKEAWAY);

        assertThat(result).isEmpty();
    }

    @Test @DisplayName("Multiple rules for same product")
    void multipleRules() {
        Product soup = new Product(); soup.setId(1L); soup.setName("Soup");
        when(packagingRuleRepository.findByProductIdAndActiveTrue(1L))
                .thenReturn(List.of(
                        rule(soup, bowl, QuantityMode.PER_ITEM, 1, false),
                        rule(soup, spoon, QuantityMode.PER_ITEM, 1, false),
                        rule(soup, bag, QuantityMode.PER_ORDER, 1, true)
                ));

        List<OrderItem> result = packagingService.getPackagingItems(
                List.of(orderItem(1L, "Soup", 2)), OrderType.DELIVERY);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getQuantity()).isEqualTo(2); // 2 bowls
        assertThat(result.get(1).getQuantity()).isEqualTo(2); // 2 spoons
        assertThat(result.get(2).getQuantity()).isEqualTo(1); // 1 bag
    }
}
