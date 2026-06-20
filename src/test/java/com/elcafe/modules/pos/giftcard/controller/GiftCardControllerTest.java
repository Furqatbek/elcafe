package com.elcafe.modules.pos.giftcard.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.pos.giftcard.dto.*;
import com.elcafe.modules.pos.giftcard.entity.GiftCard;
import com.elcafe.modules.pos.giftcard.entity.GiftCardType;
import com.elcafe.modules.pos.giftcard.enums.GiftCardStatus;
import com.elcafe.modules.pos.giftcard.service.GiftCardService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GiftCardControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private GiftCardService giftCardService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private GiftCardController controller;
    private final String BASE = "/api/v1/restaurants/1/pos/gift-cards";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }

    @Test @DisplayName("POST /types") void createType() throws Exception {
        CreateGiftCardTypeRequest req = new CreateGiftCardTypeRequest(); req.setName("Standard");
        Restaurant r = new Restaurant(); r.setId(1L);
        when(giftCardService.createGiftCardType(eq(1L), any()))
                .thenReturn(GiftCardType.builder().id(1L).restaurant(r).name("Standard").build());
        mockMvc.perform(post(BASE + "/types").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /types") void getTypes() throws Exception {
        when(giftCardService.getGiftCardTypes(1L)).thenReturn(List.of());
        mockMvc.perform(get(BASE + "/types")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST / — issue") void issue() throws Exception {
        IssueGiftCardRequest req = new IssueGiftCardRequest(); req.setAmount(new BigDecimal("50000"));
        Restaurant r = new Restaurant(); r.setId(1L);
        when(giftCardService.issueGiftCard(eq(1L), any(), eq(1L)))
                .thenReturn(GiftCard.builder().id(1L).restaurant(r).cardNumber("GC-001")
                        .initialBalance(new BigDecimal("50000")).currentBalance(new BigDecimal("50000"))
                        .status(GiftCardStatus.ACTIVE).build());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)).param("operatorId", "1"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET / — list") void list() throws Exception {
        when(giftCardService.listGiftCards(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get(BASE)).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /balance/{card}") void balance() throws Exception {
        when(giftCardService.checkBalance(1L, "GC-001"))
                .thenReturn(GiftCardBalanceResponse.builder().cardNumber("GC-001")
                        .currentBalance(new BigDecimal("50000")).isValid(true).build());
        mockMvc.perform(get(BASE + "/balance/GC-001")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /redeem") void redeem() throws Exception {
        RedeemGiftCardRequest req = new RedeemGiftCardRequest();
        req.setCardNumberOrBarcode("GC-001"); req.setAmount(new BigDecimal("10000"));
        when(giftCardService.redeemGiftCard(eq(1L), any(), any(), any(), eq(1L)))
                .thenReturn(RedeemResult.builder().amountRedeemed(new BigDecimal("10000"))
                        .remainingBalance(new BigDecimal("40000")).cardNumber("GC-001").build());
        mockMvc.perform(post(BASE + "/redeem").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)).param("operatorId", "1"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("POST /{card}/reload") void reload() throws Exception {
        Restaurant r = new Restaurant(); r.setId(1L);
        when(giftCardService.reloadGiftCard(eq(1L), eq("GC-001"), any(), eq(1L)))
                .thenReturn(GiftCard.builder().id(1L).restaurant(r).cardNumber("GC-001")
                        .currentBalance(new BigDecimal("100000")).status(GiftCardStatus.ACTIVE).build());
        mockMvc.perform(post(BASE + "/GC-001/reload").param("amount", "50000").param("operatorId", "1"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{card}/transactions") void transactions() throws Exception {
        when(giftCardService.getTransactionHistory(eq(1L), eq("GC-001"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get(BASE + "/GC-001/transactions")).andExpect(status().isOk());
    }
}
