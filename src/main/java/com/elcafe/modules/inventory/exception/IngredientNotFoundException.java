package com.elcafe.modules.inventory.exception;

/**
 * Exception thrown when an ingredient cannot be found.
 */
public class IngredientNotFoundException extends RuntimeException {

    public IngredientNotFoundException(Long ingredientId) {
        super(String.format("Ingredient not found with id: %d", ingredientId));
    }

    public IngredientNotFoundException(String message) {
        super(message);
    }

    public IngredientNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
