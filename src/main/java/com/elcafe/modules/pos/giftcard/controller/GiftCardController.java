package com.elcafe.modules.pos.giftcard.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.pos.giftcard.dto.*;
import com.elcafe.modules.pos.giftcard.entity.GiftCard;
import com.elcafe.modules.pos.giftcard.entity.GiftCardTransaction;
import com.elcafe.modules.pos.giftcard.entity.GiftCardType;
import com.elcafe.modules.pos.giftcard.service.GiftCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/pos/gift-cards")
@RequiredArgsConstructor
@Tag(name = "Gift Cards", description = "Gift card management and redemption")
public class GiftCardController {

    private final GiftCardService giftCardService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    // Gift Card Types
    @PostMapping("/types")
    @Operation(summary = "Create a gift card type")
    public ResponseEntity<GiftCardType> createType(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateGiftCardTypeRequest request) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(giftCardService.createGiftCardType(restaurantId, request));
    }

    @GetMapping("/types")
    @Operation(summary = "Get all gift card types")
    public ResponseEntity<List<GiftCardType>> getTypes(
            @PathVariable Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(giftCardService.getGiftCardTypes(restaurantId));
    }

    // Gift Cards
    @PostMapping
    @Operation(summary = "Issue a new gift card")
    public ResponseEntity<GiftCard> issueGiftCard(
            @PathVariable Long restaurantId,
            @Valid @RequestBody IssueGiftCardRequest request,
            @RequestParam Long operatorId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(giftCardService.issueGiftCard(restaurantId, request, operatorId));
    }

    @GetMapping
    @Operation(summary = "List all gift cards")
    public ResponseEntity<Page<GiftCard>> listGiftCards(
            @PathVariable Long restaurantId,
            Pageable pageable) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(giftCardService.listGiftCards(restaurantId, pageable));
    }

    @GetMapping("/balance/{cardNumberOrBarcode}")
    @Operation(summary = "Check gift card balance")
    public ResponseEntity<GiftCardBalanceResponse> checkBalance(
            @PathVariable Long restaurantId,
            @PathVariable String cardNumberOrBarcode) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(giftCardService.checkBalance(restaurantId, cardNumberOrBarcode));
    }

    @PostMapping("/redeem")
    @Operation(summary = "Redeem gift card (without order)")
    public ResponseEntity<RedeemResult> redeemGiftCard(
            @PathVariable Long restaurantId,
            @Valid @RequestBody RedeemGiftCardRequest request,
            @RequestParam Long operatorId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(giftCardService.redeemGiftCard(
            restaurantId, request, null, null, operatorId));
    }

    @PostMapping("/{cardNumberOrBarcode}/reload")
    @Operation(summary = "Reload a gift card")
    public ResponseEntity<GiftCard> reloadGiftCard(
            @PathVariable Long restaurantId,
            @PathVariable String cardNumberOrBarcode,
            @RequestParam BigDecimal amount,
            @RequestParam Long operatorId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(giftCardService.reloadGiftCard(
            restaurantId, cardNumberOrBarcode, amount, operatorId));
    }

    @GetMapping("/{cardNumberOrBarcode}/transactions")
    @Operation(summary = "Get gift card transaction history")
    public ResponseEntity<Page<GiftCardTransaction>> getTransactions(
            @PathVariable Long restaurantId,
            @PathVariable String cardNumberOrBarcode,
            Pageable pageable) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(giftCardService.getTransactionHistory(
            restaurantId, cardNumberOrBarcode, pageable));
    }
}
