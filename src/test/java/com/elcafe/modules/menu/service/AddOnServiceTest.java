package com.elcafe.modules.menu.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.AddOnResponse;
import com.elcafe.modules.menu.dto.CreateAddOnRequest;
import com.elcafe.modules.menu.dto.UpdateAddOnRequest;
import com.elcafe.modules.menu.entity.AddOn;
import com.elcafe.modules.menu.entity.AddOnGroup;
import com.elcafe.modules.menu.repository.AddOnGroupRepository;
import com.elcafe.modules.menu.repository.AddOnRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddOnServiceTest {

    @Mock private AddOnRepository addOnRepository;
    @Mock private AddOnGroupRepository addOnGroupRepository;
    @InjectMocks private AddOnService addOnService;

    private AddOnGroup group;
    private AddOn addOn;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        group = AddOnGroup.builder()
                .id(1L).restaurant(restaurant).name("Extras")
                .active(true).minSelection(0).maxSelection(3).build();
        group.setAddOns(new ArrayList<>());

        addOn = AddOn.builder()
                .id(1L).addOnGroup(group).name("Cheese")
                .description("Extra cheese").price(new BigDecimal("5000"))
                .available(true).sortOrder(0).build();
    }

    @Test @DisplayName("getAllByGroup — returns mapped list")
    void getAllByGroup_returnsList() {
        when(addOnGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(addOnRepository.findByAddOnGroupIdOrderBySortOrder(1L)).thenReturn(List.of(addOn));

        List<AddOnResponse> result = addOnService.getAllAddOnsByGroup(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Cheese");
    }

    @Test @DisplayName("getAvailableByGroup — filters unavailable")
    void getAvailableByGroup_filtersUnavailable() {
        when(addOnGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(addOnRepository.findByAddOnGroupIdAndAvailableTrueOrderBySortOrder(1L)).thenReturn(List.of(addOn));

        List<AddOnResponse> result = addOnService.getAvailableAddOnsByGroup(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAvailable()).isTrue();
    }

    @Test @DisplayName("getById — found")
    void getById_found() {
        when(addOnRepository.findByIdAndAddOnGroupId(1L, 1L)).thenReturn(Optional.of(addOn));

        AddOnResponse result = addOnService.getAddOnById(1L, 1L);

        assertThat(result.getName()).isEqualTo("Cheese");
        assertThat(result.getPrice()).isEqualByComparingTo("5000");
    }

    @Test @DisplayName("getById — not found throws")
    void getById_notFound_throws() {
        when(addOnRepository.findByIdAndAddOnGroupId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addOnService.getAddOnById(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("create — success")
    void create_success() {
        CreateAddOnRequest request = new CreateAddOnRequest();
        request.setAddOnGroupId(1L);
        request.setName("Bacon");
        request.setPrice(new BigDecimal("8000"));
        request.setAvailable(true);
        request.setSortOrder(1);

        when(addOnGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(addOnRepository.existsByAddOnGroupIdAndName(1L, "Bacon")).thenReturn(false);
        when(addOnRepository.save(any(AddOn.class))).thenAnswer(i -> {
            AddOn saved = i.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        AddOnResponse result = addOnService.createAddOn(request);

        assertThat(result.getName()).isEqualTo("Bacon");
        verify(addOnRepository).save(any(AddOn.class));
    }

    @Test @DisplayName("create — duplicate name throws")
    void create_duplicateName_throws() {
        CreateAddOnRequest request = new CreateAddOnRequest();
        request.setAddOnGroupId(1L);
        request.setName("Cheese");
        when(addOnGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(addOnRepository.existsByAddOnGroupIdAndName(1L, "Cheese")).thenReturn(true);

        assertThatThrownBy(() -> addOnService.createAddOn(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test @DisplayName("update — success")
    void update_success() {
        UpdateAddOnRequest request = new UpdateAddOnRequest();
        request.setName("Double Cheese");
        request.setPrice(new BigDecimal("10000"));

        when(addOnRepository.findByIdAndAddOnGroupId(1L, 1L)).thenReturn(Optional.of(addOn));
        when(addOnRepository.existsByAddOnGroupIdAndName(1L, "Double Cheese")).thenReturn(false);
        when(addOnRepository.save(any(AddOn.class))).thenAnswer(i -> i.getArgument(0));

        AddOnResponse result = addOnService.updateAddOn(1L, 1L, request);

        assertThat(result.getName()).isEqualTo("Double Cheese");
        assertThat(result.getPrice()).isEqualByComparingTo("10000");
    }

    @Test @DisplayName("delete — success")
    void delete_success() {
        when(addOnRepository.findByIdAndAddOnGroupId(1L, 1L)).thenReturn(Optional.of(addOn));

        addOnService.deleteAddOn(1L, 1L);

        verify(addOnRepository).delete(addOn);
    }
}
