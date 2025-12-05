package com.elcafe.modules.menu.service;

import com.elcafe.modules.menu.dto.*;
import com.elcafe.modules.menu.entity.Ingredient;
import com.elcafe.modules.menu.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    @Transactional(readOnly = true)
    public Page<IngredientDTO> getAllIngredients(Pageable pageable) {
        return ingredientRepository.findAll(pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public IngredientDTO getIngredientById(Long id) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));
        return toDTO(ingredient);
    }

    @Transactional(readOnly = true)
    public Page<IngredientDTO> searchIngredients(String search, Boolean isActive, Pageable pageable) {
        return ingredientRepository.searchIngredients(search, isActive, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<IngredientDTO> getLowStockIngredients() {
        return ingredientRepository.findLowStockIngredients().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public IngredientDTO createIngredient(CreateIngredientRequest request) {
        if (ingredientRepository.existsByName(request.getName())) {
            throw new RuntimeException("Ingredient with name '" + request.getName() + "' already exists");
        }

        Ingredient ingredient = Ingredient.builder()
                .name(request.getName())
                .description(request.getDescription())
                .unit(request.getUnit())
                .costPerUnit(request.getCostPerUnit())
                .currentStock(request.getCurrentStock())
                .minimumStock(request.getMinimumStock())
                .supplier(request.getSupplier())
                .category(request.getCategory())
                .isActive(true)
                .build();

        Ingredient saved = ingredientRepository.save(ingredient);
        log.info("Created ingredient: {}", saved.getName());
        return toDTO(saved);
    }

    @Transactional
    public IngredientDTO updateIngredient(Long id, UpdateIngredientRequest request) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        if (request.getName() != null) ingredient.setName(request.getName());
        if (request.getDescription() != null) ingredient.setDescription(request.getDescription());
        if (request.getUnit() != null) ingredient.setUnit(request.getUnit());
        if (request.getCostPerUnit() != null) ingredient.setCostPerUnit(request.getCostPerUnit());
        if (request.getCurrentStock() != null) ingredient.setCurrentStock(request.getCurrentStock());
        if (request.getMinimumStock() != null) ingredient.setMinimumStock(request.getMinimumStock());
        if (request.getSupplier() != null) ingredient.setSupplier(request.getSupplier());
        if (request.getCategory() != null) ingredient.setCategory(request.getCategory());
        if (request.getIsActive() != null) ingredient.setIsActive(request.getIsActive());

        Ingredient updated = ingredientRepository.save(ingredient);
        log.info("Updated ingredient: {}", updated.getName());
        return toDTO(updated);
    }

    @Transactional
    public void deleteIngredient(Long id) {
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        ingredientRepository.delete(ingredient);
        log.info("Deleted ingredient: {}", ingredient.getName());
    }

    private IngredientDTO toDTO(Ingredient ingredient) {
        return IngredientDTO.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .description(ingredient.getDescription())
                .unit(ingredient.getUnit())
                .costPerUnit(ingredient.getCostPerUnit())
                .currentStock(ingredient.getCurrentStock())
                .minimumStock(ingredient.getMinimumStock())
                .supplier(ingredient.getSupplier())
                .category(ingredient.getCategory())
                .isActive(ingredient.getIsActive())
                .isLowStock(ingredient.isLowStock())
                .createdAt(ingredient.getCreatedAt())
                .updatedAt(ingredient.getUpdatedAt())
                .build();
    }
}
