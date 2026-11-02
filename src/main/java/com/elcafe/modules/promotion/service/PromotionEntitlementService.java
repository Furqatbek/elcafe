package com.elcafe.modules.promotion.service;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.entity.PromotionProduct;
import com.elcafe.modules.promotion.enums.PromotionScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a promotion actually entitles a given cart to.
 *
 * <p>This is the authority on promotion scope. Clients compute a discount to show
 * the customer, but a client's arithmetic cannot be trusted with money: whatever a
 * POS or mobile app claims, the order is only entitled to what this class says.
 *
 * <p>Scope is resolved from the product, server-side. A caller that passes a
 * {@code productId} does not need to know, or be believed about, which category
 * that product sits in — which is the whole point, since the bug this exists to
 * stop was a client counting items from unpromoted categories toward a
 * BUY_X_GET_Y threshold.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionEntitlementService {

    private final ProductRepository productRepository;

    /** One cart line: a product, how many of it, and its unit price. */
    public record CartLine(Long productId, int quantity, BigDecimal unitPrice) {}

    /**
     * The largest discount this promotion entitles this cart to. Zero when nothing
     * in the cart qualifies, which is the normal answer for a scoped promotion the
     * customer has not actually met the terms of.
     */
    @Transactional(readOnly = true)
    public BigDecimal maxDiscountFor(Promotion promo, List<CartLine> lines) {
        if (promo == null || lines == null || lines.isEmpty()) return BigDecimal.ZERO;
        if (promo.getPromotionType() == null) return BigDecimal.ZERO;

        List<CartLine> qualifying = qualifyingLines(promo, lines);
        BigDecimal discount = switch (promo.getPromotionType()) {
            case BUY_X_GET_Y -> buyXGetYDiscount(promo, qualifying);
            case PERCENTAGE -> percentageDiscount(promo, qualifying);
            case FIXED_AMOUNT -> fixedAmountDiscount(promo, qualifying);
            case FREE_ITEM -> freeItemDiscount(promo, qualifying);
        };

        // A rule cap applies on top of whatever the type worked out.
        if (promo.getRule() != null && promo.getRule().getMaxDiscountAmount() != null
                && discount.compareTo(promo.getRule().getMaxDiscountAmount()) > 0) {
            discount = promo.getRule().getMaxDiscountAmount();
        }
        return discount.max(BigDecimal.ZERO);
    }

    /**
     * The discount an ALL-scope promotion gives against a bare order subtotal, for
     * callers that have no item breakdown to offer.
     *
     * <p>Only meaningful for {@link PromotionScope#ALL}: a scoped promotion needs the
     * items to know what qualifies, and answering from the subtotal would discount
     * unpromoted goods. BUY_X_GET_Y needs items too — item counts are the whole
     * mechanism — so it is zero here.
     */
    @Transactional(readOnly = true)
    public BigDecimal maxDiscountForSubtotal(Promotion promo, BigDecimal orderSubtotal) {
        if (promo == null || promo.getPromotionType() == null) return BigDecimal.ZERO;
        if (promo.getPromotionScope() != null && promo.getPromotionScope() != PromotionScope.ALL) {
            return BigDecimal.ZERO;
        }
        BigDecimal subtotal = orderSubtotal != null ? orderSubtotal : BigDecimal.ZERO;
        BigDecimal value = promo.getDiscountValue() != null ? promo.getDiscountValue() : BigDecimal.ZERO;

        BigDecimal discount = switch (promo.getPromotionType()) {
            case PERCENTAGE -> subtotal.multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> value.min(subtotal);
            case FREE_ITEM -> promo.getFreeProduct() != null && promo.getFreeProduct().getPrice() != null
                    ? promo.getFreeProduct().getPrice() : BigDecimal.ZERO;
            case BUY_X_GET_Y -> BigDecimal.ZERO;
        };

        if (promo.getRule() != null && promo.getRule().getMaxDiscountAmount() != null
                && discount.compareTo(promo.getRule().getMaxDiscountAmount()) > 0) {
            discount = promo.getRule().getMaxDiscountAmount();
        }
        return discount.max(BigDecimal.ZERO);
    }

    /**
     * The cart lines this promotion covers. For {@link PromotionScope#CATEGORY} the
     * product's category is read from the product, not from the request.
     */
    public List<CartLine> qualifyingLines(Promotion promo, List<CartLine> lines) {
        if (lines == null || lines.isEmpty()) return Collections.emptyList();

        PromotionScope scope = promo.getPromotionScope() != null
                ? promo.getPromotionScope() : PromotionScope.ALL;
        if (scope == PromotionScope.ALL) {
            return lines.stream().filter(l -> l.productId() != null).collect(Collectors.toList());
        }

        List<PromotionProduct> promotionProducts = promo.getPromotionProducts() != null
                ? promo.getPromotionProducts() : Collections.emptyList();

        Set<Long> includedCategories = promotionProducts.stream()
                .filter(pp -> pp.getCategory() != null && Boolean.TRUE.equals(pp.getInclude()))
                .map(pp -> pp.getCategory().getId())
                .collect(Collectors.toSet());
        Set<Long> excludedCategories = promotionProducts.stream()
                .filter(pp -> pp.getCategory() != null && Boolean.FALSE.equals(pp.getInclude()))
                .map(pp -> pp.getCategory().getId())
                .collect(Collectors.toSet());
        Set<Long> includedProducts = promotionProducts.stream()
                .filter(pp -> pp.getProduct() != null && Boolean.TRUE.equals(pp.getInclude()))
                .map(pp -> pp.getProduct().getId())
                .collect(Collectors.toSet());
        Set<Long> excludedProducts = promotionProducts.stream()
                .filter(pp -> pp.getProduct() != null && Boolean.FALSE.equals(pp.getInclude()))
                .map(pp -> pp.getProduct().getId())
                .collect(Collectors.toSet());

        Map<Long, Long> categoryByProduct = categoryByProduct(lines);

        List<CartLine> qualifying = new ArrayList<>();
        for (CartLine line : lines) {
            if (line.productId() == null) continue; // bundle / packaging line
            boolean matches = switch (scope) {
                case CATEGORY -> {
                    Long categoryId = categoryByProduct.get(line.productId());
                    yield categoryId != null
                            && includedCategories.contains(categoryId)
                            && !excludedCategories.contains(categoryId)
                            && !excludedProducts.contains(line.productId());
                }
                case PRODUCT -> includedProducts.contains(line.productId())
                        && !excludedProducts.contains(line.productId());
                default -> true;
            };
            if (matches) qualifying.add(line);
        }
        return qualifying;
    }

    private Map<Long, Long> categoryByProduct(List<CartLine> lines) {
        Set<Long> productIds = lines.stream()
                .map(CartLine::productId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) return Collections.emptyMap();

        Map<Long, Long> map = new HashMap<>();
        for (Product product : productRepository.findAllById(productIds)) {
            if (product.getCategory() != null) {
                map.put(product.getId(), product.getCategory().getId());
            }
        }
        return map;
    }

    /**
     * Buy X get Y. A set is X paid plus Y free, so N free items need N*(X+Y)
     * qualifying units — three qualifying items under a 3+1 earn nothing. The free
     * units are priced cheapest-first, which is the customer-friendly reading and
     * matches what the cart endpoint has always done.
     */
    private BigDecimal buyXGetYDiscount(Promotion promo, List<CartLine> qualifying) {
        if (promo.getBuyQuantity() == null || promo.getGetQuantity() == null) return BigDecimal.ZERO;
        int setSize = promo.getBuyQuantity() + promo.getGetQuantity();
        if (setSize <= 0) return BigDecimal.ZERO;

        int units = qualifying.stream().mapToInt(CartLine::quantity).sum();
        int freeUnits = (units / setSize) * promo.getGetQuantity();
        if (freeUnits <= 0) return BigDecimal.ZERO;

        List<BigDecimal> unitPrices = new ArrayList<>();
        for (CartLine line : qualifying) {
            BigDecimal price = line.unitPrice() != null ? line.unitPrice() : BigDecimal.ZERO;
            for (int i = 0; i < line.quantity(); i++) unitPrices.add(price);
        }
        Collections.sort(unitPrices);

        BigDecimal percent = promo.getDiscountValue() != null ? promo.getDiscountValue() : BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        for (int i = 0; i < freeUnits && i < unitPrices.size(); i++) {
            discount = discount.add(unitPrices.get(i).multiply(percent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return discount;
    }

    private BigDecimal percentageDiscount(Promotion promo, List<CartLine> qualifying) {
        BigDecimal percent = promo.getDiscountValue() != null ? promo.getDiscountValue() : BigDecimal.ZERO;
        return qualifyingAmount(qualifying).multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** A fixed amount can never exceed what the promotion actually covers. */
    private BigDecimal fixedAmountDiscount(Promotion promo, List<CartLine> qualifying) {
        BigDecimal value = promo.getDiscountValue() != null ? promo.getDiscountValue() : BigDecimal.ZERO;
        return value.min(qualifyingAmount(qualifying));
    }

    private BigDecimal freeItemDiscount(Promotion promo, List<CartLine> qualifying) {
        if (promo.getFreeProduct() == null || promo.getFreeProduct().getPrice() == null) return BigDecimal.ZERO;
        // Only worth anything once the cart contains something the promotion covers.
        if (qualifying.isEmpty()) return BigDecimal.ZERO;
        return promo.getFreeProduct().getPrice();
    }

    private BigDecimal qualifyingAmount(List<CartLine> qualifying) {
        return qualifying.stream()
                .map(l -> (l.unitPrice() != null ? l.unitPrice() : BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
