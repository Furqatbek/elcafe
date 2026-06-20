package com.elcafe.modules.loyalty.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.dto.CreateTopUpRequest;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.WalletTopUp;
import com.elcafe.modules.loyalty.repository.WalletTopUpRepository;
import com.elcafe.modules.loyalty.service.topup.WalletTopUpPaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates customer-initiated wallet top-ups end to end:
 *
 *   create()    — record a PENDING top-up and hand the customer a
 *                 provider checkout URL.
 *   complete()  — webhook (or admin manual confirm) settles the
 *                 top-up: idempotently credit the wallet via
 *                 BonusService, mark COMPLETED, link the resulting
 *                 BonusTransaction. Safe to retry.
 *   fail()      — webhook flips a PENDING top-up to FAILED with a
 *                 reason. No wallet movement.
 *   cancel()    — customer-initiated cancel on a PENDING top-up.
 */
@Slf4j
@Service
public class WalletTopUpService {

    private final WalletTopUpRepository walletTopUpRepository;
    private final CustomerRepository customerRepository;
    private final LoyaltyService loyaltyService;
    private final BonusService bonusService;
    private final Map<WalletTopUp.Provider, WalletTopUpPaymentProvider> providers;

    public WalletTopUpService(WalletTopUpRepository walletTopUpRepository,
                              CustomerRepository customerRepository,
                              LoyaltyService loyaltyService,
                              BonusService bonusService,
                              List<WalletTopUpPaymentProvider> providers) {
        this.walletTopUpRepository = walletTopUpRepository;
        this.customerRepository = customerRepository;
        this.loyaltyService = loyaltyService;
        this.bonusService = bonusService;
        Map<WalletTopUp.Provider, WalletTopUpPaymentProvider> map = new java.util.EnumMap<>(WalletTopUp.Provider.class);
        for (WalletTopUpPaymentProvider p : providers) map.put(p.provider(), p);
        this.providers = map;
    }

    @Transactional
    public WalletTopUp create(Long customerId, CreateTopUpRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }

        WalletTopUpPaymentProvider providerImpl = providers.get(request.getProvider());
        if (providerImpl == null) {
            throw new IllegalArgumentException("Unsupported top-up provider: " + request.getProvider());
        }

        WalletTopUp topUp = WalletTopUp.builder()
                .customer(customer)
                .restaurantId(customer.getRestaurantId()) // §3.7: top-up belongs to the customer's tenant
                .amount(request.getAmount())
                .status(WalletTopUp.Status.PENDING)
                .provider(request.getProvider())
                .idempotencyKey(UUID.randomUUID().toString())
                .build();

        // Persist first so the provider can embed the row id in the
        // checkout URL — Click and Payme both rely on a merchant-side
        // reference to tie callbacks back to the right top-up.
        topUp = walletTopUpRepository.save(topUp);
        topUp.setPaymentUrl(providerImpl.buildCheckoutUrl(topUp));
        topUp = walletTopUpRepository.save(topUp);

        log.info("Created top-up {} for customer {} via {} (amount={})",
                topUp.getId(), customerId, request.getProvider(), request.getAmount());
        return topUp;
    }

    @Transactional(readOnly = true)
    public WalletTopUp getById(Long id) {
        return walletTopUpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WalletTopUp", "id", id));
    }

    @Transactional(readOnly = true)
    public WalletTopUp getOwnedBy(Long topUpId, Long customerId) {
        WalletTopUp topUp = getById(topUpId);
        if (topUp.getCustomer() == null || !Objects.equals(topUp.getCustomer().getId(), customerId)) {
            throw new ResourceNotFoundException("WalletTopUp", "id", topUpId);
        }
        return topUp;
    }

    @Transactional(readOnly = true)
    public Page<WalletTopUp> listForCustomer(Long customerId, Pageable pageable) {
        return walletTopUpRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<WalletTopUp> listAll(WalletTopUp.Status statusFilter, Pageable pageable) {
        return statusFilter == null
                ? walletTopUpRepository.findAllByOrderByCreatedAtDesc(pageable)
                : walletTopUpRepository.findByStatusOrderByCreatedAtDesc(statusFilter, pageable);
    }

    /**
     * Mark a top-up COMPLETED and credit the wallet. Idempotent on both
     * the WalletTopUp row (no-op if already COMPLETED with the same
     * external txn id) and the BonusTransaction (BonusService dedupes
     * by idempotency key).
     *
     * Looks up the top-up by id when provided, otherwise by
     * (provider, externalTransactionId) — webhooks may know one or the
     * other depending on the provider's protocol.
     */
    @Transactional
    public WalletTopUp complete(Long topUpId,
                                WalletTopUp.Provider provider,
                                String externalTransactionId,
                                Map<String, Object> webhookMetadata) {
        WalletTopUp topUp = resolveForCompletion(topUpId, provider, externalTransactionId);

        if (topUp.getStatus() == WalletTopUp.Status.COMPLETED) {
            // Already settled — idempotent return so webhook retries don't error.
            log.info("Top-up {} already COMPLETED, ignoring duplicate callback", topUp.getId());
            return topUp;
        }
        if (topUp.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot complete top-up " + topUp.getId() + " in terminal state " + topUp.getStatus());
        }

        CustomerLoyalty loyalty = loyaltyService.getOrCreateCustomerLoyalty(topUp.getCustomer().getId());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("topUpId", topUp.getId());
        metadata.put("provider", topUp.getProvider().name());
        if (externalTransactionId != null) metadata.put("externalTransactionId", externalTransactionId);
        if (webhookMetadata != null) metadata.putAll(webhookMetadata);

        BonusTransaction tx = bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.TOP_UP,
                topUp.getAmount(),
                null,
                "Wallet top-up #" + topUp.getId() + " via " + topUp.getProvider(),
                "topup-" + topUp.getId(),
                metadata
        );

        topUp.setStatus(WalletTopUp.Status.COMPLETED);
        topUp.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        topUp.setBonusTransaction(tx);
        if (externalTransactionId != null && topUp.getExternalTransactionId() == null) {
            topUp.setExternalTransactionId(externalTransactionId);
        }
        log.info("Completed top-up {}: credited {} to customer {} (txn id {})",
                topUp.getId(), topUp.getAmount(), topUp.getCustomer().getId(), tx.getId());
        return walletTopUpRepository.save(topUp);
    }

    @Transactional
    public WalletTopUp fail(Long topUpId, String reason) {
        WalletTopUp topUp = getById(topUpId);
        if (topUp.getStatus().isTerminal()) {
            log.info("Top-up {} already terminal ({}), ignoring fail()", topUpId, topUp.getStatus());
            return topUp;
        }
        topUp.setStatus(WalletTopUp.Status.FAILED);
        topUp.setFailureReason(reason);
        return walletTopUpRepository.save(topUp);
    }

    @Transactional
    public WalletTopUp cancelByCustomer(Long topUpId, Long customerId) {
        WalletTopUp topUp = getOwnedBy(topUpId, customerId);
        if (topUp.getStatus() != WalletTopUp.Status.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING top-ups can be cancelled (was " + topUp.getStatus() + ")");
        }
        topUp.setStatus(WalletTopUp.Status.CANCELLED);
        return walletTopUpRepository.save(topUp);
    }

    private WalletTopUp resolveForCompletion(Long topUpId, WalletTopUp.Provider provider,
                                             String externalTransactionId) {
        if (topUpId != null) return getById(topUpId);
        if (provider != null && externalTransactionId != null) {
            Optional<WalletTopUp> found = walletTopUpRepository
                    .findByProviderAndExternalTransactionId(provider, externalTransactionId);
            if (found.isPresent()) return found.get();
        }
        throw new ResourceNotFoundException(
                "Could not resolve top-up by id=" + topUpId
                        + " or (provider=" + provider + ", externalTransactionId=" + externalTransactionId + ")");
    }
}
