package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.entity.PurchaseOrderItem;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderItemRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final AccountRepository accountRepository;
    private final JournalService journalService;
    private final InventoryService inventoryService;

    @Transactional
    public PurchaseOrder createPurchaseOrder(PurchaseOrder purchaseOrder, List<PurchaseOrderItem> items) {
        log.info("Creating purchase order for restaurant: {}", purchaseOrder.getRestaurant().getId());

        // Generate PO number
        String poNumber = generatePoNumber(purchaseOrder.getRestaurant().getId());
        purchaseOrder.setPoNumber(poNumber);
        purchaseOrder.setStatus(PurchaseOrder.Status.DRAFT);
        purchaseOrder.setPaymentStatus(PurchaseOrder.PaymentStatus.UNPAID);

        PurchaseOrder savedPo = purchaseOrderRepository.save(purchaseOrder);

        // Add items
        for (PurchaseOrderItem item : items) {
            item.setPurchaseOrder(savedPo);
            savedPo.addItem(item);
        }

        purchaseOrderRepository.save(savedPo);

        log.info("Purchase order created: {}", poNumber);
        return savedPo;
    }

    @Transactional
    public PurchaseOrder approvePurchaseOrder(Long poId, String approvedBy) {
        log.info("Approving purchase order: {}", poId);

        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        if (po.getStatus() != PurchaseOrder.Status.DRAFT &&
            po.getStatus() != PurchaseOrder.Status.PENDING_APPROVAL) {
            throw new RuntimeException("Only draft or pending orders can be approved");
        }

        po.setStatus(PurchaseOrder.Status.APPROVED);
        po.setApprovedBy(approvedBy);
        po.setApprovedAt(LocalDateTime.now());

        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder receivePurchaseOrder(Long poId, LocalDate actualDeliveryDate,
                                             String receivedBy, List<ReceivedItem> receivedItems) {
        log.info("Receiving purchase order: {}", poId);

        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        if (po.getStatus() != PurchaseOrder.Status.APPROVED &&
            po.getStatus() != PurchaseOrder.Status.ORDERED) {
            throw new RuntimeException("Only approved/ordered POs can be received");
        }

        po.setActualDeliveryDate(actualDeliveryDate);
        po.setReceivedBy(receivedBy);
        po.setReceivedAt(LocalDateTime.now());

        boolean fullyReceived = true;

        // Update received quantities and add to inventory
        for (ReceivedItem receivedItem : receivedItems) {
            PurchaseOrderItem item = purchaseOrderItemRepository.findById(receivedItem.getItemId())
                    .orElseThrow(() -> new RuntimeException("Purchase order item not found"));

            item.setReceivedQuantity(
                    (item.getReceivedQuantity() != null ? item.getReceivedQuantity() : BigDecimal.ZERO)
                            .add(receivedItem.getReceivedQuantity())
            );

            purchaseOrderItemRepository.save(item);

            // Add to inventory if linked to an ingredient
            if (item.getIngredient() != null) {
                inventoryService.addStock(
                        item.getIngredient().getId(),
                        receivedItem.getReceivedQuantity(),
                        "PO Receipt: " + po.getPoNumber(),
                        receivedBy
                );
            }

            // Check if fully received
            if (item.getReceivedQuantity().compareTo(item.getQuantity()) < 0) {
                fullyReceived = false;
            }
        }

        po.setStatus(fullyReceived ? PurchaseOrder.Status.RECEIVED : PurchaseOrder.Status.PARTIALLY_RECEIVED);

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);

        // Create journal entry for inventory and accounts payable
        if (fullyReceived) {
            createPurchaseJournalEntry(savedPo, receivedBy);
        }

        log.info("Purchase order received: {}", po.getPoNumber());
        return savedPo;
    }

    @Transactional
    public PurchaseOrder recordPayment(Long poId, LocalDate paymentDate, BigDecimal amount,
                                      String paymentMethod, String recordedBy) {
        log.info("Recording payment for purchase order: {}", poId);

        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        BigDecimal currentPaid = po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(amount);

        if (newPaidAmount.compareTo(po.getTotalAmount()) > 0) {
            throw new RuntimeException("Payment amount exceeds total amount due");
        }

        po.setPaidAmount(newPaidAmount);
        po.updatePaymentStatus();

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);

        // Create journal entry for payment
        createPaymentJournalEntry(savedPo, amount, paymentMethod, paymentDate, recordedBy);

        log.info("Payment recorded for PO: {}, amount: {}", po.getPoNumber(), amount);
        return savedPo;
    }

    public List<PurchaseOrder> getPurchaseOrdersByRestaurant(Long restaurantId) {
        return purchaseOrderRepository.findByRestaurantId(restaurantId);
    }

    public PurchaseOrder getPurchaseOrderById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));
    }

    public List<PurchaseOrder> getUnpaidOrders(Long restaurantId) {
        return purchaseOrderRepository.findUnpaidOrders(restaurantId);
    }

    private void createPurchaseJournalEntry(PurchaseOrder po, String recordedBy) {
        try {
            // Debit: Inventory, Credit: Accounts Payable
            Account inventoryAccount = accountRepository.findByRestaurantIdAndCategory(
                    po.getRestaurant().getId(), Account.AccountCategory.INVENTORY
            ).stream().findFirst().orElse(null);

            Account apAccount = accountRepository.findByRestaurantIdAndCategory(
                    po.getRestaurant().getId(), Account.AccountCategory.ACCOUNTS_PAYABLE
            ).stream().findFirst().orElse(null);

            if (inventoryAccount != null && apAccount != null) {
                journalService.createJournalEntry(
                        po.getRestaurant().getId(),
                        po.getActualDeliveryDate(),
                        "Purchase Order: " + po.getPoNumber(),
                        "PURCHASE_ORDER",
                        po.getId(),
                        inventoryAccount.getId(),
                        apAccount.getId(),
                        po.getTotalAmount(),
                        recordedBy
                );
            }
        } catch (Exception e) {
            log.warn("Failed to create purchase journal entry: {}", e.getMessage());
        }
    }

    private void createPaymentJournalEntry(PurchaseOrder po, BigDecimal amount,
                                          String paymentMethod, LocalDate paymentDate,
                                          String recordedBy) {
        try {
            // Debit: Accounts Payable, Credit: Cash/Bank
            Account apAccount = accountRepository.findByRestaurantIdAndCategory(
                    po.getRestaurant().getId(), Account.AccountCategory.ACCOUNTS_PAYABLE
            ).stream().findFirst().orElse(null);

            Account.AccountCategory paymentCategory = paymentMethod.equals("CASH")
                    ? Account.AccountCategory.CASH
                    : Account.AccountCategory.BANK;

            Account paymentAccount = accountRepository.findByRestaurantIdAndCategory(
                    po.getRestaurant().getId(), paymentCategory
            ).stream().findFirst().orElse(null);

            if (apAccount != null && paymentAccount != null) {
                journalService.createJournalEntry(
                        po.getRestaurant().getId(),
                        paymentDate,
                        "Payment for PO: " + po.getPoNumber(),
                        "PO_PAYMENT",
                        po.getId(),
                        apAccount.getId(),
                        paymentAccount.getId(),
                        amount,
                        recordedBy
                );
            }
        } catch (Exception e) {
            log.warn("Failed to create payment journal entry: {}", e.getMessage());
        }
    }

    private String generatePoNumber(Long restaurantId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = purchaseOrderRepository.findByRestaurantId(restaurantId).stream()
                .filter(po -> po.getPoNumber().startsWith("PO-" + datePrefix))
                .count();
        return String.format("PO-%s-%04d", datePrefix, count + 1);
    }

    public static class ReceivedItem {
        public Long itemId;
        public BigDecimal receivedQuantity;
        public String notes;
    }
}
