package com.elcafe.modules.promotion.service;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.entity.PromotionProduct;
import com.elcafe.modules.promotion.entity.PromotionRule;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromotionEntitlementServiceTest {

    private static final long PROMO_CATEGORY = 9L;   // "Choy" — in the promotion
    private static final long OTHER_CATEGORY = 4L;   // not in the promotion

    @Mock private ProductRepository productRepository;
    @InjectMocks private PromotionEntitlementService service;

    /** productId 1..3 sit in the promoted category, 90..99 do not. */
    @BeforeEach
    void setUp() {
        when(productRepository.findAllById(any())).thenAnswer(i -> {
            Iterable<Long> ids = i.getArgument(0);
            List<Product> products = new ArrayList<>();
            for (Long id : ids) {
                Category category = new Category();
                category.setId(id >= 90 ? OTHER_CATEGORY : PROMO_CATEGORY);
                Product p = new Product();
                p.setId(id);
                p.setCategory(category);
                products.add(p);
            }
            return products;
        });
    }

    private Promotion buyThreeGetOne() {
        Category promoted = new Category();
        promoted.setId(PROMO_CATEGORY);

        Promotion promo = Promotion.builder()
                .id(1L).name("3+1")
                .promotionType(PromotionType.BUY_X_GET_Y)
                .promotionScope(PromotionScope.CATEGORY)
                .discountValue(new BigDecimal("100"))
                .buyQuantity(3).getQuantity(1)
                .build();
        promo.setPromotionProducts(new ArrayList<>(List.of(
                PromotionProduct.builder().id(1L).category(promoted).include(true).build())));
        return promo;
    }

    private static PromotionEntitlementService.CartLine line(Long productId, int qty, String price) {
        return new PromotionEntitlementService.CartLine(productId, qty, new BigDecimal(price));
    }

    @Test @DisplayName("3 promoted + 1 unpromoted earns nothing — the reported bug")
    void threeQualifyingPlusOneOutsideCategory_earnsNothing() {
        BigDecimal discount = service.maxDiscountFor(buyThreeGetOne(), List.of(
                line(1L, 3, "20000"),   // promoted category
                line(90L, 1, "20000")   // other category — must not count
        ));

        assertThat(discount).isEqualByComparingTo("0");
    }

    @Test @DisplayName("4 promoted items earn one free, priced at the cheapest")
    void fourQualifying_earnsOneFree() {
        BigDecimal discount = service.maxDiscountFor(buyThreeGetOne(), List.of(
                line(1L, 3, "20000"),
                line(2L, 1, "15000")    // cheapest qualifying unit is the free one
        ));

        assertThat(discount).isEqualByComparingTo("15000.00");
    }

    @Test @DisplayName("8 promoted items earn two free")
    void eightQualifying_earnsTwoFree() {
        BigDecimal discount = service.maxDiscountFor(buyThreeGetOne(), List.of(
                line(1L, 8, "20000")
        ));

        assertThat(discount).isEqualByComparingTo("40000.00");
    }

    @Test @DisplayName("7 promoted items still earn only one free")
    void sevenQualifying_earnsOneFree() {
        BigDecimal discount = service.maxDiscountFor(buyThreeGetOne(), List.of(
                line(1L, 7, "20000")
        ));

        assertThat(discount).isEqualByComparingTo("20000.00");
    }

    @Test @DisplayName("an excluded category does not qualify even when its category is included")
    void excludedProductDoesNotQualify() {
        Promotion promo = buyThreeGetOne();
        Product blocked = new Product();
        blocked.setId(2L);
        promo.getPromotionProducts().add(
                PromotionProduct.builder().id(2L).product(blocked).include(false).build());

        // 3 of product 1 plus 1 of the excluded product 2 — only 3 qualify.
        BigDecimal discount = service.maxDiscountFor(promo, List.of(
                line(1L, 3, "20000"),
                line(2L, 1, "20000")
        ));

        assertThat(discount).isEqualByComparingTo("0");
    }

    @Test @DisplayName("a 50% BUY_X_GET_Y halves the free item instead of giving it away")
    void partialDiscountValue() {
        Promotion promo = buyThreeGetOne();
        promo.setDiscountValue(new BigDecimal("50"));

        BigDecimal discount = service.maxDiscountFor(promo, List.of(line(1L, 4, "20000")));

        assertThat(discount).isEqualByComparingTo("10000.00");
    }

    @Test @DisplayName("ALL scope counts every line")
    void allScopeCountsEverything() {
        Promotion promo = buyThreeGetOne();
        promo.setPromotionScope(PromotionScope.ALL);

        BigDecimal discount = service.maxDiscountFor(promo, List.of(
                line(1L, 3, "20000"),
                line(90L, 1, "20000")
        ));

        assertThat(discount).isEqualByComparingTo("20000.00");
    }

    @Test @DisplayName("a category-scoped percentage only discounts the qualifying lines")
    void percentageRespectsScope() {
        Category promoted = new Category();
        promoted.setId(PROMO_CATEGORY);
        Promotion promo = Promotion.builder()
                .id(2L).name("10% off tea")
                .promotionType(PromotionType.PERCENTAGE)
                .promotionScope(PromotionScope.CATEGORY)
                .discountValue(new BigDecimal("10"))
                .build();
        promo.setPromotionProducts(new ArrayList<>(List.of(
                PromotionProduct.builder().category(promoted).include(true).build())));

        // 100000 qualifying + 50000 not. 10% of the qualifying part only.
        BigDecimal discount = service.maxDiscountFor(promo, List.of(
                line(1L, 1, "100000"),
                line(90L, 1, "50000")
        ));

        assertThat(discount).isEqualByComparingTo("10000.00");
    }

    @Test @DisplayName("a fixed amount cannot exceed the qualifying lines it applies to")
    void fixedAmountCappedByQualifyingAmount() {
        Category promoted = new Category();
        promoted.setId(PROMO_CATEGORY);
        Promotion promo = Promotion.builder()
                .id(3L).name("20k off tea")
                .promotionType(PromotionType.FIXED_AMOUNT)
                .promotionScope(PromotionScope.CATEGORY)
                .discountValue(new BigDecimal("20000"))
                .build();
        promo.setPromotionProducts(new ArrayList<>(List.of(
                PromotionProduct.builder().category(promoted).include(true).build())));

        BigDecimal discount = service.maxDiscountFor(promo, List.of(
                line(1L, 1, "5000"),     // only 5000 of qualifying goods
                line(90L, 1, "90000")
        ));

        assertThat(discount).isEqualByComparingTo("5000");
    }

    @Test @DisplayName("the rule's max discount caps the result")
    void ruleCapApplies() {
        Promotion promo = buyThreeGetOne();
        promo.setRule(PromotionRule.builder().maxDiscountAmount(new BigDecimal("5000")).build());

        BigDecimal discount = service.maxDiscountFor(promo, List.of(line(1L, 4, "20000")));

        assertThat(discount).isEqualByComparingTo("5000");
    }

    @Test @DisplayName("bundle and packaging lines carry no product and never qualify")
    void linesWithoutProductDoNotQualify() {
        BigDecimal discount = service.maxDiscountFor(buyThreeGetOne(), List.of(
                line(1L, 3, "20000"),
                line(null, 1, "20000")
        ));

        assertThat(discount).isEqualByComparingTo("0");
    }

    @Test @DisplayName("an empty cart earns nothing")
    void emptyCart() {
        assertThat(service.maxDiscountFor(buyThreeGetOne(), List.of())).isEqualByComparingTo("0");
        assertThat(service.maxDiscountFor(buyThreeGetOne(), null)).isEqualByComparingTo("0");
    }
}
