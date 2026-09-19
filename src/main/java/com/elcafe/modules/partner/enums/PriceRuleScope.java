package com.elcafe.modules.partner.enums;

/**
 * What a price override applies to. Resolution runs most-specific first, so a rule on a product beats
 * one on its category, and a rule on a variant beats both.
 */
public enum PriceRuleScope {
    CATEGORY,
    PRODUCT,
    VARIANT
}
