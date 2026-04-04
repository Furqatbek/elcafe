package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.CreateIngredientRequest;
import com.elcafe.modules.menu.dto.IngredientDTO;
import com.elcafe.modules.menu.dto.UpdateIngredientRequest;
import com.elcafe.modules.menu.entity.Ingredient;
import com.elcafe.modules.menu.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock private IngredientRepository ingredientRepository;
    @InjectMocks private IngredientService ingredientService;

    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        ingredient = Ingredient.builder()
                .id(1L).name("Flour").description("All-purpose flour")
                .unit("kg").costPerUnit(new BigDecimal("5000"))
                .currentStock(new BigDecimal("100")).minimumStock(new BigDecimal("10"))
                .isActive(true).build();
    }

    @Test @DisplayName("getAllIngredients — returns paginated")
    void getAllIngredients_returnsPage() {
        when(ingredientRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(ingredient)));

        Page<IngredientDTO> result = ingredientService.getAllIngredients(PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Flour");
    }

    @Test @DisplayName("getIngredientById — found")
    void getIngredientById_found() {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));

        IngredientDTO result = ingredientService.getIngredientById(1L);

        assertThat(result.getName()).isEqualTo("Flour");
        assertThat(result.getUnit()).isEqualTo("kg");
    }

    @Test @DisplayName("getIngredientById — not found throws")
    void getIngredientById_notFound_throws() {
        when(ingredientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingredientService.getIngredientById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ingredient not found");
    }

    @Test @DisplayName("createIngredient — success")
    void createIngredient_success() {
        CreateIngredientRequest request = new CreateIngredientRequest();
        request.setName("Flour");
        request.setUnit("kg");
        request.setCostPerUnit(new BigDecimal("5000"));
        request.setCurrentStock(new BigDecimal("100"));
        request.setMinimumStock(new BigDecimal("10"));

        when(ingredientRepository.existsByName("Flour")).thenReturn(false);
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> {
            Ingredient saved = i.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        IngredientDTO result = ingredientService.createIngredient(request);

        assertThat(result.getName()).isEqualTo("Flour");
        verify(ingredientRepository).save(any(Ingredient.class));
    }

    @Test @DisplayName("createIngredient — duplicate name throws")
    void createIngredient_duplicateName_throws() {
        CreateIngredientRequest request = new CreateIngredientRequest();
        request.setName("Flour");
        when(ingredientRepository.existsByName("Flour")).thenReturn(true);

        assertThatThrownBy(() -> ingredientService.createIngredient(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test @DisplayName("updateIngredient — success")
    void updateIngredient_success() {
        UpdateIngredientRequest request = new UpdateIngredientRequest();
        request.setName("Updated Flour");
        request.setCostPerUnit(new BigDecimal("5500"));

        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));

        IngredientDTO result = ingredientService.updateIngredient(1L, request);

        assertThat(result.getName()).isEqualTo("Updated Flour");
        assertThat(result.getCostPerUnit()).isEqualByComparingTo("5500");
    }

    @Test @DisplayName("deleteIngredient — success")
    void deleteIngredient_success() {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));

        ingredientService.deleteIngredient(1L);

        verify(ingredientRepository).delete(ingredient);
    }

    @Test @DisplayName("searchIngredients — returns filtered page")
    void searchIngredients_returnsPage() {
        when(ingredientRepository.searchIngredients(eq("Flour"), any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(ingredient)));

        var result = ingredientService.searchIngredients("Flour", null,
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Flour");
    }

    @Test @DisplayName("getLowStockIngredients — returns low stock list")
    void getLowStockIngredients_returnsList() {
        when(ingredientRepository.findLowStockIngredients()).thenReturn(java.util.List.of(ingredient));

        var result = ingredientService.getLowStockIngredients();

        assertThat(result).hasSize(1);
    }
}
