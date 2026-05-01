package com.elcafe.modules.menu.enums;

public enum QuantityMode {
    PER_ITEM,   // Multiply by item quantity (3 soups → 3 bowls)
    PER_ORDER,  // Add once regardless of quantity (3 soups → 1 bag)
    FIXED       // Always add this exact quantity
}
