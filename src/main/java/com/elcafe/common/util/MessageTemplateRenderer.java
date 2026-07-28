package com.elcafe.common.util;

import java.util.Map;

/**
 * The one place channel message templates turn placeholders into text.
 *
 * <p>Every per-tenant channel — SMS, Telegram, Instagram, and whatever comes next — stores a template
 * body containing {@code {name}}-style placeholders and renders it against a map of values. That
 * substitution used to be copy-pasted onto each channel's template entity, which is how the channels
 * drifted apart: Instagram guarded a null placeholder map while SMS and Telegram did not, so the same
 * "preview with an empty body" request threw a {@link NullPointerException} on two channels and
 * rendered fine on the third. Centralising it makes the rule one rule.
 *
 * <p>Contract (matches the previous per-entity behaviour, minus the null-map crash):
 * <ul>
 *   <li>a {@code null} template is returned as-is (the caller had no body to render);</li>
 *   <li>a {@code null} or empty placeholder map returns the template untouched;</li>
 *   <li>each {@code {key}} occurrence is replaced by its value, and a {@code null} value renders as
 *       the empty string (a missing value must never print the literal text "null").</li>
 * </ul>
 * Substitution is one left-to-right pass of literal {@link String#replace} calls (no regex): each
 * value is inserted as-is, and the template is never parsed as a nested template.
 */
public final class MessageTemplateRenderer {

    private MessageTemplateRenderer() {
    }

    /**
     * Render {@code template} by replacing every {@code {key}} with its value from {@code placeholders}.
     *
     * @param template     the template body (may be {@code null})
     * @param placeholders key → value substitutions (may be {@code null} or empty)
     * @return the rendered text, or {@code template} unchanged when there is nothing to substitute
     */
    public static String render(String template, Map<String, String> placeholders) {
        if (template == null || placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            rendered = rendered.replace("{" + entry.getKey() + "}", value);
        }
        return rendered;
    }
}
