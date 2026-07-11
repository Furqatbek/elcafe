package com.elcafe.modules.restaurant.mapper;

import com.elcafe.modules.restaurant.dto.RestaurantRequest;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the minimal-create-body regression found by the fresh-DB launch rehearsal: a request with
 * only the required fields (name + address) must still produce an entity that satisfies the
 * NOT NULL columns. The mapper's explicit {@code .active(...)}/{@code .acceptingOrders(...)} calls
 * override the entity's {@code @Builder.Default}s, so nulls here reached the INSERT and 500'd the
 * very first thing a new operator does after logging in.
 */
class RestaurantMapperTest {

    private final RestaurantMapper mapper = new RestaurantMapper();

    @Test
    @DisplayName("minimal create body (name + address) → active and acceptingOrders default TRUE")
    void minimalRequestDefaultsNotNullFlags() {
        RestaurantRequest request = new RestaurantRequest();
        request.setName("Kofi House");
        request.setAddress("Tashkent, Amir Temur 1");
        request.setActive(null);           // explicit null, as an API client may send
        request.setAcceptingOrders(null);

        Restaurant entity = mapper.toEntity(request);

        assertThat(entity.getActive()).isTrue();
        assertThat(entity.getAcceptingOrders()).isTrue();
        assertThat(entity.getRating()).isNotNull(); // @Builder.Default must survive the builder path
    }

    @Test
    @DisplayName("explicit false values are respected, not overwritten by the defaults")
    void explicitFalseRespected() {
        RestaurantRequest request = new RestaurantRequest();
        request.setName("Closed Cafe");
        request.setAddress("Somewhere");
        request.setActive(false);
        request.setAcceptingOrders(false);

        Restaurant entity = mapper.toEntity(request);

        assertThat(entity.getActive()).isFalse();
        assertThat(entity.getAcceptingOrders()).isFalse();
    }
}
