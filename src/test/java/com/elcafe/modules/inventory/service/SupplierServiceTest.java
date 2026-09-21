package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.SupplierRequest;
import com.elcafe.modules.inventory.dto.SupplierResponse;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock private SupplierRepository supplierRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private SupplierService supplierService;

    private Restaurant restaurant;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        supplier = Supplier.builder()
                .id(1L)
                .restaurant(restaurant)
                .name("Fresh Foods")
                .code("FF001")
                .contactPerson("John")
                .phone("+998901234567")
                .email("john@freshfoods.com")
                .address("123 Market St")
                .paymentTerms("Net 30")
                .creditLimit(BigDecimal.valueOf(50000000))
                .currency("UZS")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("getAllByRestaurant — returns mapped list")
    void getAllByRestaurant_returnsList() {
        when(supplierRepository.findByRestaurant_IdOrderByNameAsc(1L)).thenReturn(List.of(supplier));

        List<SupplierResponse> result = supplierService.getAllByRestaurant(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Fresh Foods");
        assertThat(result.get(0).getCode()).isEqualTo("FF001");
    }

    @Test
    @DisplayName("getActiveByRestaurant — returns only active suppliers")
    void getActiveByRestaurant_filtersInactive() {
        when(supplierRepository.findByRestaurant_IdAndActiveTrueOrderByNameAsc(1L)).thenReturn(List.of(supplier));

        List<SupplierResponse> result = supplierService.getActiveByRestaurant(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActive()).isTrue();
    }

    @Test
    @DisplayName("getById — found")
    void getById_found() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

        SupplierResponse result = supplierService.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Fresh Foods");
        assertThat(result.getRestaurantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getById — not found throws")
    void getById_notFound_throws() {
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.getById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Supplier not found");
    }

    @Test
    @DisplayName("create — success with all fields")
    void create_success() {
        SupplierRequest request = SupplierRequest.builder()
                .restaurantId(1L)
                .name("Fresh Foods")
                .code("FF001")
                .contactPerson("John")
                .phone("+998901234567")
                .email("john@freshfoods.com")
                .paymentTerms("Net 30")
                .creditLimit(BigDecimal.valueOf(50000000))
                .build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(supplierRepository.existsByRestaurantIdAndCode(1L, "FF001")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(i -> {
            Supplier s = i.getArgument(0);
            s.setId(1L);
            return s;
        });

        SupplierResponse result = supplierService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Fresh Foods");
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    @DisplayName("create — duplicate code throws")
    void create_duplicateCode_throws() {
        SupplierRequest request = SupplierRequest.builder()
                .restaurantId(1L)
                .name("Another Supplier")
                .code("FF001")
                .build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(supplierRepository.existsByRestaurantIdAndCode(1L, "FF001")).thenReturn(true);

        assertThatThrownBy(() -> supplierService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");

        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — restaurant not found throws")
    void create_restaurantNotFound_throws() {
        SupplierRequest request = SupplierRequest.builder().restaurantId(99L).name("X").build();
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Restaurant not found");
    }

    @Test
    @DisplayName("create — defaults currency to UZS and active to true")
    void create_defaults() {
        SupplierRequest request = SupplierRequest.builder()
                .restaurantId(1L)
                .name("New Supplier")
                .build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(i -> {
            Supplier s = i.getArgument(0);
            s.setId(2L);
            return s;
        });

        SupplierResponse result = supplierService.create(request);

        assertThat(result.getCurrency()).isEqualTo("UZS");
        assertThat(result.getActive()).isTrue();
    }

    @Test
    @DisplayName("update — success")
    void update_success() {
        SupplierRequest request = SupplierRequest.builder()
                .name("Updated Foods")
                .code("UF001")
                .contactPerson("Jane")
                .phone("+998909876543")
                .build();

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsByRestaurantIdAndCodeAndIdNot(1L, "UF001", 1L)).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(i -> i.getArgument(0));

        SupplierResponse result = supplierService.update(1L, request);

        assertThat(result.getName()).isEqualTo("Updated Foods");
        assertThat(result.getContactPerson()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("update — duplicate code on different supplier throws")
    void update_duplicateCode_throws() {
        SupplierRequest request = SupplierRequest.builder()
                .name("Other")
                .code("EXISTING")
                .build();

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsByRestaurantIdAndCodeAndIdNot(1L, "EXISTING", 1L)).thenReturn(true);

        assertThatThrownBy(() -> supplierService.update(1L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("delete — soft deletes by setting inactive")
    void delete_softDeletes() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(i -> i.getArgument(0));

        supplierService.delete(1L);

        assertThat(supplier.getActive()).isFalse();
        verify(supplierRepository).save(supplier);
    }

    @Test
    @DisplayName("hardDelete — removes from database")
    void hardDelete_success() {
        when(supplierRepository.existsById(1L)).thenReturn(true);

        supplierService.hardDelete(1L);

        verify(supplierRepository).deleteById(1L);
    }

    @Test
    @DisplayName("hardDelete — not found throws")
    void hardDelete_notFound_throws() {
        when(supplierRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> supplierService.hardDelete(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Supplier not found");
    }

    @Test
    @DisplayName("toggleActive — flips active status")
    void toggleActive_flipsStatus() {
        supplier.setActive(true);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(i -> i.getArgument(0));

        SupplierResponse result = supplierService.toggleActive(1L);

        assertThat(result.getActive()).isFalse();
    }

    @Test
    @DisplayName("getActiveCount — delegates to repository")
    void getActiveCount_delegates() {
        when(supplierRepository.countByRestaurantIdAndActiveTrue(1L)).thenReturn(5L);

        long count = supplierService.getActiveCount(1L);

        assertThat(count).isEqualTo(5L);
    }
}
