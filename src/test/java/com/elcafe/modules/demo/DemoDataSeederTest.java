package com.elcafe.modules.demo;

import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo box is the one deployment nobody runs a test suite against before showing it to someone.
 *
 * <p>So the seeding is tested here instead: it runs at boot, on a machine with no operator watching,
 * and a demo that comes up with an empty menu or a login nobody can use is discovered by the person
 * being shown it. Everything asserted below is something a viewer would notice within a minute.
 */
@SpringBootTest
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:demoseedit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
})
class DemoDataSeederTest {

    @Autowired private DemoDataSeeder seeder;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryIngredientRepository ingredientRepository;
    @Autowired private InventoryProductIngredientRepository productIngredientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PartnerRepository partnerRepository;
    @Autowired private PartnerRestaurantRepository partnerRestaurantRepository;

    @Test
    @DisplayName("the demo comes up with a venue and a menu, not an empty shell")
    void seedsAVenueWithAMenu() {
        assertThat(restaurantRepository.findAll())
                .anySatisfy(restaurant -> assertThat(restaurant.getName()).isEqualTo("Qahvoon Demo"));

        assertThat(productRepository.findAll())
                .extracting(product -> product.getName())
                .contains("Osh", "Lagman", "Somsa", "Green tea");

        // Every item live and on sale: a menu of drafts shows a viewer nothing.
        assertThat(productRepository.findAll())
                .allSatisfy(product -> {
                    assertThat(product.getStatus()).isEqualTo(ProductStatus.LIVE);
                    assertThat(product.getInStock()).isTrue();
                });
    }

    @Test
    @DisplayName("recipes exist, so auto sold-out can be shown rather than described")
    void seedsRecipesThinEnoughToDemonstrate() {
        assertThat(ingredientRepository.findAll())
                .extracting(ingredient -> ingredient.getName())
                .contains("Lamb", "Beef", "Rice", "Carrot", "Pastry sheets");

        assertThat(productIngredientRepository.findAll())
                .as("a dish with no recipe cannot demonstrate selling itself out")
                .isNotEmpty();

        // Somsa needs two pastry sheets and six are in stock: three orders take it off the menu,
        // which is a minute of demo rather than an afternoon.
        var pastry = ingredientRepository.findAll().stream()
                .filter(ingredient -> "Pastry sheets".equals(ingredient.getName()))
                .findFirst().orElseThrow();
        assertThat(pastry.getCurrentStock()).isLessThan(new java.math.BigDecimal("10"));
    }

    @Test
    @DisplayName("the venue login is a venue admin, not the platform operator")
    void seedsAVenueAdmin() {
        var admin = userRepository.findByEmail("demo@restos.uz").orElseThrow();

        // Showing the product as a SUPER_ADMIN shows a cross-tenant console no restaurant ever sees.
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getRestaurantId()).isNotNull();
        assertThat(admin.getActive()).isTrue();
    }

    @Test
    @DisplayName("a partner is wired up with a markup, so channel pricing is visible not described")
    void seedsAPartnerWithChannelPricing() {
        var zbr = partnerRepository.findAll().stream()
                .filter(partner -> "zbr".equals(partner.getSlug()))
                .findFirst().orElseThrow();
        assertThat(zbr.getActive()).isTrue();
        assertThat(zbr.getApiKeyHash()).isNotBlank();

        assertThat(partnerRestaurantRepository.findAll())
                .filteredOn(grant -> grant.getPartnerId().equals(zbr.getId()))
                .singleElement()
                .satisfies(grant -> {
                    assertThat(grant.getCanReadMenu()).isTrue();
                    assertThat(grant.getCanPushOrders()).isTrue();
                    assertThat(grant.getPriceAdjustmentType()).isEqualTo(PriceAdjustmentType.PERCENT);
                    assertThat(grant.getPriceAdjustmentValue()).isEqualByComparingTo("15");
                });
    }

    @Test
    @DisplayName("restarting the demo box does not multiply its menu")
    void isIdempotent() {
        long restaurantsBefore = restaurantRepository.count();
        long productsBefore = productRepository.count();
        long partnersBefore = partnerRepository.count();

        // A demo box gets restarted — for a deploy, for a reboot, because someone broke it. Seeding
        // again on every boot would double the menu each time and issue a partner key nobody sees.
        seeder.run(null);

        assertThat(restaurantRepository.count()).isEqualTo(restaurantsBefore);
        assertThat(productRepository.count()).isEqualTo(productsBefore);
        assertThat(partnerRepository.count()).isEqualTo(partnersBefore);
    }
}
