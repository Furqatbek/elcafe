package com.elcafe.modules.partner.service;

import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues, verifies and authorizes partner API keys.
 *
 * <p>Two separate questions live here and must not be confused. {@link #authenticate} answers "which
 * partner is this?" from the presented key. {@link #requireMenuAccess} and {@link #requireOrderAccess}
 * answer "may that partner touch this venue?" — and the answer is always a
 * {@link PartnerRestaurant} grant, never the mere fact that the key was valid. A partner with a
 * perfectly good key and no grant for restaurant 7 must be refused restaurant 7.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerAccessService {

    /**
     * Prefix on every issued key. Purely a readability aid — it makes a leaked key recognizable in a
     * log or a paste, so it can be rotated rather than puzzled over.
     */
    public static final String KEY_PREFIX = "elc_";

    /** 32 bytes of CSPRNG output. Far beyond guessing, which is why the stored hash can be a plain digest. */
    private static final int KEY_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PartnerRepository partnerRepository;
    private final PartnerRestaurantRepository partnerRestaurantRepository;

    /**
     * Mint a new raw API key. The caller must hand this to the operator immediately and keep only
     * {@link #hashApiKey(String)} of it — we never store anything that can authenticate.
     */
    public String generateApiKey() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 of the raw key, hex-encoded.
     *
     * <p>A plain digest rather than bcrypt is the right primitive here: the input is 256 bits of our own
     * randomness, so there is no dictionary to mount and no work factor worth paying on every request.
     * It also means verification is a single indexed lookup on {@code api_key_hash} — and because an
     * attacker would need a preimage to produce a matching hash, that lookup leaks nothing by timing,
     * unlike comparing raw secrets row by row.
     */
    public String hashApiKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; if it is missing the JVM is unusable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** The visible fragment kept alongside the hash so an operator can tell two keys apart. */
    public String prefixOf(String rawKey) {
        int end = Math.min(rawKey.length(), KEY_PREFIX.length() + 6);
        return rawKey.substring(0, end);
    }

    /**
     * Resolve a presented key to its partner. Empty for an unknown key, and equally empty for a known
     * but deactivated one — a revoked partner must be indistinguishable from a forged key.
     */
    @Transactional(readOnly = true)
    public Optional<Partner> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return partnerRepository.findByApiKeyHash(hashApiKey(rawKey))
                .filter(partner -> Boolean.TRUE.equals(partner.getActive()));
    }

    /** Reload the authenticated partner. The principal carries only identity, not the live record. */
    @Transactional(readOnly = true)
    public Partner requirePartner(Long partnerId) {
        return partnerRepository.findById(partnerId)
                .filter(partner -> Boolean.TRUE.equals(partner.getActive()))
                .orElseThrow(() -> new AccessDeniedException("Partner is not active"));
    }

    /** The partner may read this venue's menu, or this throws. */
    @Transactional(readOnly = true)
    public PartnerRestaurant requireMenuAccess(Long partnerId, Long restaurantId) {
        return requireGrant(partnerId, restaurantId, PartnerRestaurant::getCanReadMenu, "read the menu of");
    }

    /** The partner may push orders to this venue, or this throws. */
    @Transactional(readOnly = true)
    public PartnerRestaurant requireOrderAccess(Long partnerId, Long restaurantId) {
        return requireGrant(partnerId, restaurantId, PartnerRestaurant::getCanPushOrders, "push orders to");
    }

    private PartnerRestaurant requireGrant(Long partnerId, Long restaurantId,
                                           java.util.function.Function<PartnerRestaurant, Boolean> capability,
                                           String action) {
        PartnerRestaurant grant = partnerRestaurantRepository
                .findByPartnerIdAndRestaurantId(partnerId, restaurantId)
                .filter(g -> Boolean.TRUE.equals(g.getActive()))
                .orElseThrow(() -> {
                    log.warn("Partner {} denied: no active grant for restaurant {}", partnerId, restaurantId);
                    // Deliberately the same message whether the grant is missing, inactive, or the
                    // restaurant does not exist — a partner must not be able to enumerate our venues.
                    return new AccessDeniedException("Partner is not authorized for this restaurant");
                });

        if (!Boolean.TRUE.equals(capability.apply(grant))) {
            log.warn("Partner {} denied: not permitted to {} restaurant {}", partnerId, action, restaurantId);
            throw new AccessDeniedException("Partner is not authorized for this restaurant");
        }
        return grant;
    }
}
