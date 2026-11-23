package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.repository.ProductionBatchConsumptionRepository;
import com.elcafe.modules.inventory.repository.ProductionBatchRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a sale of a dish served from a production batch posts to the COGS account.
 *
 * <p>It used to cost every line as recipe × quantity, the same as a dish cooked to order. But a batch
 * product's recipe is the whole pot — it is what a new batch is pre-filled from, unscaled — so each
 * plate of osh posted the cost of an entire pot. The pot's own record of what each portion cost was
 * being written all along, and was never read.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchCogsPostingTest {

    private static final Long ORDER_ID = 55L;
    private static final Long POT = 7L;          // osh, cooked in batches
    private static final Long STEAK = 8L;        // cooked to order
    private static final Long REGULAR = 30L;     // a 0.4 kg portion of osh
    private static final Long COGS_ACCOUNT = 10L;
    private static final Long INVENTORY_ACCOUNT = 20L;

    @Mock private JournalService journalService;
    @Mock private AccountRepository accountRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @Mock private ProductionBatchConsumptionRepository productionBatchConsumptionRepository;
    @Mock private ProductionBatchRepository productionBatchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @InjectMocks private RevenueService revenueService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(ORDER_ID, OrderStatus.COMPLETED);

        when(accountRepository.findByRestaurant_IdAndCategory(any(), eq(Account.AccountCategory.COGS)))
                .thenReturn(List.of(account(COGS_ACCOUNT)));
        when(accountRepository.findByRestaurant_IdAndCategory(any(), eq(Account.AccountCategory.INVENTORY)))
                .thenReturn(List.of(account(INVENTORY_ACCOUNT)));

        Ingredient lamb = Ingredient.builder().id(1L).name("Lamb").unit("kg")
                .costPerUnit(new BigDecimal("95000")).build();
        // The whole pot: 3 kg of lamb, 285 000 so'm. Recipe × quantity on this is the bug.
        when(productIngredientRepository.findByProductIdWithIngredients(POT)).thenReturn(List.of(
                ProductIngredient.builder().ingredient(lamb).quantityRequired(new BigDecimal("3.000")).build()));
        // One steak: 0.2 kg of lamb, 19 000 so'm.
        when(productIngredientRepository.findByProductIdWithIngredients(STEAK)).thenReturn(List.of(
                ProductIngredient.builder().ingredient(lamb).quantityRequired(new BigDecimal("0.200")).build()));

        when(productRepository.findById(POT)).thenReturn(Optional.of(
                Product.builder().id(POT).name("Osh").usesProductionBatch(true).build()));
        when(productRepository.findById(STEAK)).thenReturn(Optional.of(
                Product.builder().id(STEAK).name("Steak").usesProductionBatch(false).build()));
        when(productVariantRepository.findById(REGULAR)).thenReturn(Optional.of(
                ProductVariant.builder().id(REGULAR).batchDeductionQuantity(new BigDecimal("0.400")).build()));

        when(productionBatchConsumptionRepository.sumCostByOrderItem(ORDER_ID)).thenReturn(List.of());
        when(productionBatchRepository.getAverageCostPerUnit(POT)).thenReturn(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("a portion taken out of a pot posts what the pot charged for it, not the recipe")
    void portionFromAPot_postsWhatThePotCharged() {
        order.getItems().add(line(101L, POT, 1, REGULAR, null));
        consumed(101L, "12400");   // 0.4 kg from a batch that cost 31 000 so'm/kg

        revenueService.recordCogs(order, LocalDate.now());

        // Before: 285 000 — the cost of a whole pot for one plate.
        assertThat(postedCogs()).isEqualByComparingTo("12400");
        verify(productIngredientRepository, never()).findByProductIdWithIngredients(POT);
    }

    @Test
    @DisplayName("a batch dish costed before it left the pot is priced at the average batch cost, never the pot")
    void costedBeforeItLeftThePot_usesAverageBatchCost() {
        // No consumption to read: COGS is posted at the first of completion or full payment, and a
        // till-paid order or an online order paid before it was accepted gets there before the kitchen
        // takes anything out of the pot. So does a line a waiter adds later, whose consumption is
        // recorded without a link to the order.
        order.getItems().add(line(101L, POT, 2, REGULAR, null));

        revenueService.recordCogs(order, LocalDate.now());

        // 2 × 0.4 kg × 30 000. Before: 2 × 285 000 = 570 000.
        assertThat(postedCogs()).isEqualByComparingTo("24000");
        verify(productIngredientRepository, never()).findByProductIdWithIngredients(POT);
    }

    @Test
    @DisplayName("the estimate measures a portion the same way the pot does: weight sold beats portion size")
    void estimate_usesThePotsPortionRule() {
        order.getItems().add(line(101L, POT, 1, REGULAR, new BigDecimal("0.550")));

        revenueService.recordCogs(order, LocalDate.now());

        // 0.55 kg weighed out, not the 0.4 kg standard portion: the rule consumeForOrder applies.
        assertThat(postedCogs()).isEqualByComparingTo("16500");
    }

    @Test
    @DisplayName("a batch product with no finished batch posts nothing for that line rather than a pot")
    void neverCookedInABatch_postsNothingRatherThanAPot() {
        when(productionBatchRepository.getAverageCostPerUnit(POT)).thenReturn(BigDecimal.ZERO);
        order.getItems().add(line(101L, POT, 1, REGULAR, null));

        revenueService.recordCogs(order, LocalDate.now());

        // No batch cost exists to charge. Zero understates; the recipe would overstate by a whole pot
        // per plate, so it is not used as a fallback.
        verify(journalService, never()).createJournalEntry(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(productIngredientRepository, never()).findByProductIdWithIngredients(POT);
    }

    @Test
    @DisplayName("a dish cooked to order is still costed from its recipe")
    void cookedToOrder_isStillCostedFromItsRecipe() {
        order.getItems().add(line(102L, STEAK, 2, null, null));

        revenueService.recordCogs(order, LocalDate.now());

        assertThat(postedCogs()).isEqualByComparingTo("38000");   // 2 × 0.2 kg × 95 000
    }

    @Test
    @DisplayName("an order with both kinds posts the pot's portion plus the recipe")
    void mixedOrder_addsBoth() {
        order.getItems().add(line(101L, POT, 1, REGULAR, null));
        order.getItems().add(line(102L, STEAK, 1, null, null));
        consumed(101L, "12400");

        revenueService.recordCogs(order, LocalDate.now());

        assertThat(postedCogs()).isEqualByComparingTo("31400");   // 12 400 + 19 000
    }

    @Test
    @DisplayName("what the pot charged wins even if the product has since stopped using batches")
    void consumptionWins_evenAfterTheFlagChanged() {
        // The consumption is what happened. A flag edited between the sale and its costing must not
        // send this plate back to a recipe it was never cooked from.
        when(productRepository.findById(POT)).thenReturn(Optional.of(
                Product.builder().id(POT).name("Osh").usesProductionBatch(false).build()));
        order.getItems().add(line(101L, POT, 1, REGULAR, null));
        consumed(101L, "12400");

        revenueService.recordCogs(order, LocalDate.now());

        assertThat(postedCogs()).isEqualByComparingTo("12400");
        verify(productIngredientRepository, never()).findByProductIdWithIngredients(POT);
    }

    // ---------------------------------------------------------------------------------------------

    private BigDecimal postedCogs() {
        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(journalService).createJournalEntry(any(), any(), any(), eq("ORDER_COGS"), eq(ORDER_ID),
                eq(COGS_ACCOUNT), eq(INVENTORY_ACCOUNT), amount.capture(), any());
        return amount.getValue();
    }

    private void consumed(Long orderItemId, String cost) {
        List<Object[]> rows = List.<Object[]>of(new Object[]{orderItemId, new BigDecimal(cost)});
        when(productionBatchConsumptionRepository.sumCostByOrderItem(ORDER_ID)).thenReturn(rows);
    }

    private static OrderItem line(Long id, Long productId, int quantity, Long variantId, BigDecimal weight) {
        return OrderItem.builder()
                .id(id)
                .productId(productId)
                .productName("item-" + productId)
                .quantity(quantity)
                .variantId(variantId)
                .weightAmount(weight)
                .unitPrice(new BigDecimal("32000"))
                .totalPrice(new BigDecimal("32000").multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    private static Account account(Long id) {
        Account account = new Account();
        account.setId(id);
        account.setBalance(BigDecimal.ZERO);
        return account;
    }
}
