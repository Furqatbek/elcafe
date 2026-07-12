package com.elcafe.modules.menu.service;

import com.elcafe.exception.ConflictException;

import com.elcafe.exception.BadRequestException;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.AddOnGroupResponse;
import com.elcafe.modules.menu.dto.CreateAddOnGroupRequest;
import com.elcafe.modules.menu.dto.UpdateAddOnGroupRequest;
import com.elcafe.modules.menu.entity.AddOn;
import com.elcafe.modules.menu.entity.AddOnGroup;
import com.elcafe.modules.menu.repository.AddOnGroupRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddOnGroupServiceTest {

    @Mock private AddOnGroupRepository addOnGroupRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private AddOnGroupService addOnGroupService;

    private Restaurant restaurant;
    private AddOnGroup group;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        group = AddOnGroup.builder()
                .id(1L).restaurant(restaurant).name("Extras")
                .description("Extra toppings").required(false)
                .minSelection(0).maxSelection(3).active(true)
                .build();
        group.setAddOns(new ArrayList<>());
    }

    @Test @DisplayName("getAllByRestaurant — returns mapped list")
    void getAllByRestaurant_returnsList() {
        when(addOnGroupRepository.findByRestaurant_Id(1L)).thenReturn(List.of(group));

        List<AddOnGroupResponse> result = addOnGroupService.getAllAddOnGroupsByRestaurant(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Extras");
    }

    @Test @DisplayName("getActiveByRestaurant — filters inactive")
    void getActiveByRestaurant_filtersInactive() {
        when(addOnGroupRepository.findByRestaurant_IdAndActiveTrue(1L)).thenReturn(List.of(group));

        List<AddOnGroupResponse> result = addOnGroupService.getActiveAddOnGroupsByRestaurant(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActive()).isTrue();
    }

    @Test @DisplayName("getById — found")
    void getById_found() {
        when(addOnGroupRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(group));

        AddOnGroupResponse result = addOnGroupService.getAddOnGroupById(1L, 1L);

        assertThat(result.getName()).isEqualTo("Extras");
        assertThat(result.getRestaurantId()).isEqualTo(1L);
    }

    @Test @DisplayName("getById — not found throws")
    void getById_notFound_throws() {
        when(addOnGroupRepository.findByIdAndRestaurantId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addOnGroupService.getAddOnGroupById(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("create — success")
    void create_success() {
        CreateAddOnGroupRequest request = new CreateAddOnGroupRequest();
        request.setRestaurantId(1L);
        request.setName("Sauces");
        request.setRequired(false);
        request.setMinSelection(0);
        request.setMaxSelection(2);
        request.setActive(true);

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(addOnGroupRepository.existsByRestaurantIdAndName(1L, "Sauces")).thenReturn(false);
        when(addOnGroupRepository.save(any(AddOnGroup.class))).thenAnswer(i -> {
            AddOnGroup saved = i.getArgument(0);
            saved.setId(2L);
            saved.setAddOns(new ArrayList<>());
            return saved;
        });

        AddOnGroupResponse result = addOnGroupService.createAddOnGroup(request);

        assertThat(result.getName()).isEqualTo("Sauces");
        verify(addOnGroupRepository).save(any(AddOnGroup.class));
    }

    @Test @DisplayName("create — duplicate name throws")
    void create_duplicateName_throws() {
        CreateAddOnGroupRequest request = new CreateAddOnGroupRequest();
        request.setRestaurantId(1L);
        request.setName("Extras");
        request.setMinSelection(0);
        request.setMaxSelection(1);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(addOnGroupRepository.existsByRestaurantIdAndName(1L, "Extras")).thenReturn(true);

        assertThatThrownBy(() -> addOnGroupService.createAddOnGroup(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test @DisplayName("create — minSelection > maxSelection throws")
    void create_invalidSelection_throws() {
        CreateAddOnGroupRequest request = new CreateAddOnGroupRequest();
        request.setRestaurantId(1L);
        request.setName("Invalid");
        request.setMinSelection(5);
        request.setMaxSelection(2);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(addOnGroupRepository.existsByRestaurantIdAndName(anyLong(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> addOnGroupService.createAddOnGroup(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Minimum selection");
    }

    @Test @DisplayName("update — success")
    void update_success() {
        UpdateAddOnGroupRequest request = new UpdateAddOnGroupRequest();
        request.setName("Updated Extras");
        request.setMaxSelection(5);

        when(addOnGroupRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(group));
        when(addOnGroupRepository.existsByRestaurantIdAndName(1L, "Updated Extras")).thenReturn(false);
        when(addOnGroupRepository.save(any(AddOnGroup.class))).thenAnswer(i -> i.getArgument(0));

        AddOnGroupResponse result = addOnGroupService.updateAddOnGroup(1L, 1L, request);

        assertThat(result.getName()).isEqualTo("Updated Extras");
    }

    @Test @DisplayName("delete — success")
    void delete_success() {
        when(addOnGroupRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(group));

        addOnGroupService.deleteAddOnGroup(1L, 1L);

        verify(addOnGroupRepository).delete(group);
    }
}
