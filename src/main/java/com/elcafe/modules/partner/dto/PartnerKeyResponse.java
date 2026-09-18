package com.elcafe.modules.partner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The one and only time a raw API key is ever returned.
 *
 * <p>Only the SHA-256 of it is stored, so this response cannot be reproduced — not by another API call,
 * not by a database query, not by us. The UI must show it to the operator and make them copy it before
 * they navigate away; losing it means rotating to a new key, which invalidates the old one everywhere
 * the partner has installed it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerKeyResponse {

    private Long partnerId;

    private String name;

    private String slug;

    /** The raw key. Shown once. Never stored, never retrievable. */
    private String apiKey;
}
