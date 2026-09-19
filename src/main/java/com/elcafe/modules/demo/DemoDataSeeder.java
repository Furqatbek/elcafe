package com.elcafe.modules.demo;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.partner.service.PartnerAccessService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fills a demo deployment with something worth looking at.
 *
 * <p>A fresh deployment starts empty — migrations create the schema and nothing else — which is right
 * for a real venue and useless for a demo. Somebody shown an empty menu, an empty dashboard and no
 * partner to integrate with learns nothing about the product.
 *
 * <p><b>Only ever runs under the {@code demo} Spring profile.</b> That is the whole safety story:
 * production does not set it, so this class is not even instantiated there. It is also idempotent —
 * it looks for its own restaurant by name and returns if it finds one — so restarting a demo box does
 * not multiply its menu.
 *
 * <p>What it deliberately does NOT seed: tables and their QR codes, historical orders, staff beyond
 * one login. Those either need a human to walk through the flow anyway or would be fabricated history
 * shown as if it were real. Two minutes in the admin panel covers the first; the second is not
 * something a demo should teach anyone to trust.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
// After AdminBootstrapInitializer (@Order(0)), which needs an empty users table to create the
// platform operator. Seeding a venue admin first would make that bootstrap skip itself, and the
// demo box would have no SUPER_ADMIN.
@Order(100)
public class DemoDataSeeder implements ApplicationRunner {

    /** The seeder's own marker. Finding this venue means the demo is already seeded. */
    private static final String DEMO_RESTAURANT = "Qahvoon Demo";
    private static final String DEMO_ADMIN_EMAIL = "demo@restos.uz";

    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryProductIngredientRepository productIngredientRepository;
    private final UserRepository userRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerRestaurantRepository partnerRestaurantRepository;
    private final PartnerAccessService partnerAccessService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (restaurantRepository.findAll().stream()
                .anyMatch(restaurant -> DEMO_RESTAURANT.equals(restaurant.getName()))) {
            log.info("Demo data already present — leaving it alone.");
            return;
        }

        Restaurant restaurant = seedRestaurant();
        seedMenu(restaurant);
        seedAdmin(restaurant);
        seedPartner(restaurant);

        log.info("Demo data seeded for restaurant {} ({}).", restaurant.getName(), restaurant.getId());
    }

    private Restaurant seedRestaurant() {
        return restaurantRepository.save(Restaurant.builder()
                .name(DEMO_RESTAURANT)
                .address("1 Amir Temur, Tashkent")
                .phone("+998900000000")
                .active(true)
                .acceptingOrders(true)
                .deliveryFee(new BigDecimal("10000"))
                .build());
    }

    /**
     * A small menu that exercises the features worth demonstrating rather than a long one that
     * exercises scrolling: a dish sold by size, a dish with a recipe thin enough to sell out on
     * camera, and one with no recipe at all to show that those stay on sale.
     */
    private void seedMenu(Restaurant restaurant) {
        Category mains = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Main dishes").sortOrder(0).active(true).build());
        Category drinks = categoryRepository.save(Category.builder()
                .restaurant(restaurant).name("Drinks").sortOrder(1).active(true).build());

        Product osh = product(mains, "Osh", "Plov with lamb, carrot and cumin", "32000", true);
        productVariantRepository.saveAll(List.of(
                variant(osh, "Regular", "32000", 0),
                variant(osh, "Large", "41000", 1)));

        Product lagman = product(mains, "Lagman", "Hand-pulled noodles in beef broth", "30000", false);
        Product somsa = product(mains, "Somsa", "Baked lamb pastry", "12000", false);
        // No recipe: stays on sale whatever the walk-in holds, which is the honest answer for an item
        // nobody has entered ingredients for.
        product(drinks, "Green tea", "Pot, serves two", "8000", false);

        Ingredient lamb = ingredient(restaurant, "Lamb", "kg", "12.000", "2.000", "95000");
        Ingredient beef = ingredient(restaurant, "Beef", "kg", "8.000", "2.000", "90000");
        Ingredient rice = ingredient(restaurant, "Rice", "kg", "40.000", "10.000", "14000");
        Ingredient carrot = ingredient(restaurant, "Carrot", "kg", "25.000", "5.000", "6000");
        // Deliberately near its threshold: two or three demo orders take Somsa off the menu by
        // themselves, which is the auto sold-out behaviour in one minute rather than one afternoon.
        Ingredient pastry = ingredient(restaurant, "Pastry sheets", "pcs", "6.000", "10.000", "3000");

        productIngredientRepository.saveAll(List.of(
                recipe(osh, lamb, "0.200"), recipe(osh, rice, "0.180"), recipe(osh, carrot, "0.120"),
                recipe(lagman, beef, "0.220"),
                recipe(somsa, lamb, "0.090"), recipe(somsa, pastry, "2.000")));
    }

    private Product product(Category category, String name, String description, String price,
                            boolean hasVariants) {
        return productRepository.save(Product.builder()
                .category(category)
                .name(name)
                .description(description)
                .price(new BigDecimal(price))
                .status(ProductStatus.LIVE)
                .inStock(true)
                .hasVariants(hasVariants)
                .sortOrder(0)
                .build());
    }

    private ProductVariant variant(Product product, String name, String price, int sortOrder) {
        return ProductVariant.builder()
                .product(product).name(name).price(new BigDecimal(price))
                .inStock(true).isAvailable(true).sortOrder(sortOrder).build();
    }

    private Ingredient ingredient(Restaurant restaurant, String name, String unit,
                                  String stock, String minimum, String cost) {
        return ingredientRepository.save(Ingredient.builder()
                .restaurant(restaurant).name(name).unit(unit)
                .currentStock(new BigDecimal(stock))
                .minimumStock(new BigDecimal(minimum))
                .costPerUnit(new BigDecimal(cost))
                .active(true).trackInventory(true).build());
    }

    private ProductIngredient recipe(Product product, Ingredient ingredient, String quantity) {
        return ProductIngredient.builder()
                .product(product).ingredient(ingredient)
                .quantityRequired(new BigDecimal(quantity)).unit(ingredient.getUnit())
                .optional(false).build();
    }

    /**
     * The venue login. Separate from the platform operator on purpose: showing someone the product
     * as a SUPER_ADMIN shows them a cross-tenant console no restaurant will ever see.
     */
    private void seedAdmin(Restaurant restaurant) {
        if (userRepository.existsByEmail(DEMO_ADMIN_EMAIL)) {
            return;
        }
        userRepository.save(User.builder()
                .email(DEMO_ADMIN_EMAIL)
                .password(passwordEncoder.encode(demoPassword()))
                .firstName("Demo").lastName("Manager")
                .role(UserRole.ADMIN)
                .restaurantId(restaurant.getId())
                .active(true).emailVerified(true)
                .build());
        log.info("Demo venue login: {} / {}", DEMO_ADMIN_EMAIL, demoPassword());
    }

    /**
     * A partner the integration can actually be demonstrated against, with a 15% channel markup so
     * the difference between counter and aggregator pricing is visible rather than described.
     *
     * <p>The key is logged once, here, because it is hashed the moment it is stored and cannot be
     * recovered afterwards. A demo box whose partner key nobody wrote down needs the partner deleted
     * and recreated through the admin panel.
     */
    private void seedPartner(Restaurant restaurant) {
        String apiKey = partnerAccessService.generateApiKey();
        Partner zbr = partnerRepository.save(Partner.builder()
                .name("ZBR (demo)").slug("zbr")
                .apiKeyHash(partnerAccessService.hashApiKey(apiKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(apiKey))
                .active(true).build());

        // Menu only. This is the grant ZBR are handed for staging, and we told them in writing that
        // order push stays off on it until one order has gone end to end with a person watching — a
        // promise this seeder was quietly breaking. It is also refused outright while their
        // paymentMode is a constant (V194), so turning it on here would fail the admin path anyway.
        partnerRestaurantRepository.save(PartnerRestaurant.builder()
                .partnerId(zbr.getId()).restaurantId(restaurant.getId())
                .canReadMenu(true).canPushOrders(false).active(true)
                .priceAdjustmentType(PriceAdjustmentType.PERCENT)
                .priceAdjustmentValue(new BigDecimal("15"))
                .priceRounding(new BigDecimal("500"))
                .build());

        log.info("Demo partner key for venue {} — copy it now, it is hashed and cannot be shown again: {}",
                restaurant.getId(), apiKey);
    }

    /**
     * Fixed, and fine to be fixed: this account only ever exists on a box whose entire contents are
     * invented, and a demo whose password has to be looked up is a demo nobody gives.
     */
    private String demoPassword() {
        return "DemoPass123!";
    }
}
