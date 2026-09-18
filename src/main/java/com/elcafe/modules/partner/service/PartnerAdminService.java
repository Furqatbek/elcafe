package com.elcafe.modules.partner.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.partner.dto.CreatePartnerRequest;
import com.elcafe.modules.partner.dto.PartnerAdminResponse;
import com.elcafe.modules.partner.dto.PartnerGrantRequest;
import com.elcafe.modules.partner.dto.PartnerKeyResponse;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Staff-side management of integration partners: who exists, what key they hold, which venues. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerAdminService {

    private final PartnerRepository partnerRepository;
    private final PartnerRestaurantRepository partnerRestaurantRepository;
    private final RestaurantRepository restaurantRepository;
    private final PartnerAccessService partnerAccessService;

    @Transactional(readOnly = true)
    public List<PartnerAdminResponse> listPartners() {
        return partnerRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Creates the partner and returns its key — the only moment the raw key exists outside the
     * partner's own configuration.
     */
    @Transactional
    public PartnerKeyResponse createPartner(CreatePartnerRequest request) {
        if (partnerRepository.existsBySlug(request.getSlug())) {
            throw new BadRequestException("A partner with this slug already exists: " + request.getSlug());
        }

        String rawKey = partnerAccessService.generateApiKey();

        Partner partner = partnerRepository.save(Partner.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .apiKeyHash(partnerAccessService.hashApiKey(rawKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(rawKey))
                .contactEmail(request.getContactEmail())
                .active(true)
                .build());

        log.info("Created integration partner {} (slug={})", partner.getId(), partner.getSlug());
        return PartnerKeyResponse.builder()
                .partnerId(partner.getId())
                .name(partner.getName())
                .slug(partner.getSlug())
                .apiKey(rawKey)
                .build();
    }

    /**
     * Issues a new key and invalidates the old one immediately. There is deliberately no grace period
     * where both work: rotation is what you reach for when a key may have leaked, and a window in which
     * the leaked key still opens the door is the one thing rotation must not leave you with.
     */
    @Transactional
    public PartnerKeyResponse rotateKey(Long partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));

        String rawKey = partnerAccessService.generateApiKey();
        partner.setApiKeyHash(partnerAccessService.hashApiKey(rawKey));
        partner.setApiKeyPrefix(partnerAccessService.prefixOf(rawKey));
        partnerRepository.save(partner);

        log.warn("Rotated API key for partner {} (slug={}) — the previous key is now dead",
                partner.getId(), partner.getSlug());
        return PartnerKeyResponse.builder()
                .partnerId(partner.getId())
                .name(partner.getName())
                .slug(partner.getSlug())
                .apiKey(rawKey)
                .build();
    }

    /**
     * Deactivating is the kill switch: authentication filters on {@code active}, so every venue goes
     * dark at once without touching the grants, and reactivating restores exactly what was there.
     */
    @Transactional
    public PartnerAdminResponse setActive(Long partnerId, boolean active) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));
        partner.setActive(active);
        partnerRepository.save(partner);
        log.info("Partner {} (slug={}) set active={}", partner.getId(), partner.getSlug(), active);
        return toResponse(partner);
    }

    /** Grant or update a partner's access to one venue. Idempotent — re-granting updates in place. */
    @Transactional
    public PartnerAdminResponse grantRestaurant(Long partnerId, Long restaurantId, PartnerGrantRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        PartnerRestaurant grant = partnerRestaurantRepository
                .findByPartnerIdAndRestaurantId(partnerId, restaurantId)
                .orElseGet(() -> PartnerRestaurant.builder()
                        .partnerId(partnerId)
                        .restaurantId(restaurantId)
                        .build());

        grant.setCanReadMenu(Boolean.TRUE.equals(request.getCanReadMenu()));
        grant.setCanPushOrders(Boolean.TRUE.equals(request.getCanPushOrders()));
        grant.setActive(true);
        partnerRestaurantRepository.save(grant);

        log.info("Partner {} granted restaurant {} (menu={}, orders={})",
                partner.getSlug(), restaurant.getId(), grant.getCanReadMenu(), grant.getCanPushOrders());
        return toResponse(partner);
    }

    /**
     * Revokes a venue by deactivating the grant rather than deleting the row, so the history of who was
     * once connected survives and restoring access is one flag rather than a re-grant from memory.
     */
    @Transactional
    public PartnerAdminResponse revokeRestaurant(Long partnerId, Long restaurantId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));

        partnerRestaurantRepository.findByPartnerIdAndRestaurantId(partnerId, restaurantId)
                .ifPresent(grant -> {
                    grant.setActive(false);
                    partnerRestaurantRepository.save(grant);
                    log.info("Partner {} revoked from restaurant {}", partner.getSlug(), restaurantId);
                });
        return toResponse(partner);
    }

    private PartnerAdminResponse toResponse(Partner partner) {
        List<PartnerRestaurant> rows = partnerRestaurantRepository.findByPartnerId(partner.getId());

        // Name only the venues this partner actually touches. Loading every restaurant to label a
        // handful of grants would scale with the size of the platform rather than with the answer.
        Map<Long, String> restaurantNames = restaurantRepository.findAllById(
                        rows.stream().map(PartnerRestaurant::getRestaurantId).distinct().toList()).stream()
                .collect(Collectors.toMap(Restaurant::getId, Restaurant::getName, (a, b) -> a));

        List<PartnerAdminResponse.Grant> grants = rows.stream()
                .map(grant -> PartnerAdminResponse.Grant.builder()
                        .restaurantId(grant.getRestaurantId())
                        .restaurantName(restaurantNames.get(grant.getRestaurantId()))
                        .canReadMenu(grant.getCanReadMenu())
                        .canPushOrders(grant.getCanPushOrders())
                        .active(grant.getActive())
                        .build())
                .sorted(java.util.Comparator.comparing(
                        PartnerAdminResponse.Grant::getRestaurantId,
                        java.util.Comparator.nullsLast(Long::compareTo)))
                .toList();

        return PartnerAdminResponse.builder()
                .id(partner.getId())
                .name(partner.getName())
                .slug(partner.getSlug())
                .apiKeyPrefix(partner.getApiKeyPrefix())
                .contactEmail(partner.getContactEmail())
                .active(partner.getActive())
                .createdAt(partner.getCreatedAt())
                .restaurants(grants)
                .build();
    }
}
