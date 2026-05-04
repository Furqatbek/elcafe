package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftInventorySnapshot;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.pos.shift.repository.ShiftInventorySnapshotRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftInventoryServiceTest {

    @Mock private ShiftInventorySnapshotRepository snapshotRepository;
    @Mock private EmployeeShiftRepository shiftRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @InjectMocks private ShiftInventoryService service;

    @Captor private ArgumentCaptor<List<ShiftInventorySnapshot>> snapshotsCaptor;

    private EmployeeShift shift;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);

        User employee = new User();
        employee.setId(10L);

        shift = new EmployeeShift();
        shift.setId(100L);
        shift.setRestaurant(restaurant);
        shift.setEmployee(employee);

        when(shiftRepository.findById(100L)).thenReturn(Optional.of(shift));
    }

    private Ingredient ingredient(Long id, String name, BigDecimal stock) {
        return Ingredient.builder()
                .id(id).name(name).unit("kg")
                .currentStock(stock).trackInventory(true).active(true).version(0L)
                .build();
    }

    @Test @DisplayName("start snapshot records current stock levels")
    void startSnapshot() {
        Ingredient meat = ingredient(1L, "Meat", new BigDecimal("50"));
        Ingredient flour = ingredient(2L, "Flour", new BigDecimal("200"));

        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(1L)).thenReturn(List.of(meat, flour));
        when(snapshotRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<ShiftInventorySnapshot> result = service.recordStartSnapshot(100L, 1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSnapshotType()).isEqualTo(ShiftInventorySnapshot.SnapshotType.START);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("50");
        assertThat(result.get(1).getQuantity()).isEqualByComparingTo("200");
    }

    @Test @DisplayName("end snapshot records current levels with expected")
    void endSnapshot() {
        Ingredient meat = ingredient(1L, "Meat", new BigDecimal("45")); // was 50, now 45

        // Start snapshot existed
        ShiftInventorySnapshot startSnap = ShiftInventorySnapshot.builder()
                .shift(shift).ingredient(meat)
                .snapshotType(ShiftInventorySnapshot.SnapshotType.START)
                .quantity(new BigDecimal("50")).build();

        when(snapshotRepository.findByShiftIdAndSnapshotType(100L, ShiftInventorySnapshot.SnapshotType.START))
                .thenReturn(List.of(startSnap));
        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(1L)).thenReturn(List.of(meat));
        when(snapshotRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<ShiftInventorySnapshot> result = service.recordEndSnapshot(100L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSnapshotType()).isEqualTo(ShiftInventorySnapshot.SnapshotType.END);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("45");
    }

    @Test @DisplayName("discrepancies flagged correctly")
    void discrepancies() {
        Ingredient meat = ingredient(1L, "Meat", new BigDecimal("45"));

        ShiftInventorySnapshot withVariance = ShiftInventorySnapshot.builder()
                .shift(shift).ingredient(meat)
                .snapshotType(ShiftInventorySnapshot.SnapshotType.END)
                .quantity(new BigDecimal("45"))
                .expectedQuantity(new BigDecimal("47"))
                .variance(new BigDecimal("-2")) // 2kg missing
                .build();
        ShiftInventorySnapshot noVariance = ShiftInventorySnapshot.builder()
                .shift(shift).ingredient(ingredient(2L, "Flour", new BigDecimal("200")))
                .snapshotType(ShiftInventorySnapshot.SnapshotType.END)
                .quantity(new BigDecimal("200"))
                .expectedQuantity(new BigDecimal("200"))
                .variance(BigDecimal.ZERO)
                .build();

        when(snapshotRepository.findByShiftIdAndVarianceIsNotNull(100L))
                .thenReturn(List.of(withVariance, noVariance));

        List<ShiftInventorySnapshot> discrepancies = service.getDiscrepancies(100L);

        assertThat(discrepancies).hasSize(1);
        assertThat(discrepancies.get(0).getVariance()).isEqualByComparingTo("-2");
        assertThat(discrepancies.get(0).getIngredient().getName()).isEqualTo("Meat");
    }
}
