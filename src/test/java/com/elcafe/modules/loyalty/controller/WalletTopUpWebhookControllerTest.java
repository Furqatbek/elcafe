package com.elcafe.modules.loyalty.controller;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.loyalty.entity.WalletTopUp;
import com.elcafe.modules.loyalty.service.WalletTopUpService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletTopUpWebhookControllerTest {

    @Mock private WalletTopUpService walletTopUpService;
    @InjectMocks private WalletTopUpWebhookController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        // Configure service-id so the prepare check passes; leave secret blank
        // so signature verification short-circuits to true (warning path).
        ReflectionTestUtils.setField(controller, "clickServiceId", "1234");
        ReflectionTestUtils.setField(controller, "clickSecretKey", "");
        ReflectionTestUtils.setField(controller, "paymeMerchantKey", "");
    }

    private WalletTopUp pendingTopUp() {
        Customer c = Customer.builder().id(7L).qrCode("CST-A").build();
        return WalletTopUp.builder().id(101L).customer(c)
                .amount(new BigDecimal("50000"))
                .status(WalletTopUp.Status.PENDING)
                .provider(WalletTopUp.Provider.CLICK)
                .build();
    }

    @Test
    @DisplayName("Click prepare returns success when top-up is PENDING and amount matches")
    void clickPrepare_success() throws Exception {
        when(walletTopUpService.getById(101L)).thenReturn(pendingTopUp());

        mockMvc.perform(post("/api/v1/webhook/wallet/click/prepare")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("amount", "50000")
                        .param("action", "0")
                        .param("sign_time", "2026-01-01 00:00:00")
                        .param("sign_string", "any"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0))
                .andExpect(jsonPath("$.merchant_prepare_id").value("101"));
    }

    @Test
    @DisplayName("Click prepare rejects amount mismatch")
    void clickPrepare_amountMismatch() throws Exception {
        when(walletTopUpService.getById(101L)).thenReturn(pendingTopUp());

        mockMvc.perform(post("/api/v1/webhook/wallet/click/prepare")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("amount", "999")
                        .param("action", "0")
                        .param("sign_time", "2026-01-01 00:00:00")
                        .param("sign_string", "any"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(-2));
    }

    @Test
    @DisplayName("Click complete settles the top-up via service")
    void clickComplete_settles() throws Exception {
        WalletTopUp settled = pendingTopUp();
        settled.setStatus(WalletTopUp.Status.COMPLETED);
        when(walletTopUpService.complete(eq(101L), eq(WalletTopUp.Provider.CLICK),
                eq("clk-1"), any())).thenReturn(settled);

        mockMvc.perform(post("/api/v1/webhook/wallet/click/complete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("merchant_prepare_id", "101")
                        .param("amount", "50000")
                        .param("action", "1")
                        .param("error", "0")
                        .param("sign_time", "2026-01-01 00:00:00")
                        .param("sign_string", "any"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0))
                .andExpect(jsonPath("$.merchant_confirm_id").value("101"));

        verify(walletTopUpService).complete(eq(101L), eq(WalletTopUp.Provider.CLICK),
                eq("clk-1"), any());
    }

    @Test
    @DisplayName("Click complete forwarded error code triggers fail()")
    void clickComplete_providerError() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/wallet/click/complete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("merchant_prepare_id", "101")
                        .param("amount", "50000")
                        .param("action", "1")
                        .param("error", "-9")
                        .param("sign_time", "2026-01-01 00:00:00")
                        .param("sign_string", "any"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(-9));

        verify(walletTopUpService).fail(eq(101L), anyString());
    }

    @Test
    @DisplayName("Payme PerformTransaction settles the top-up")
    void payme_perform() throws Exception {
        WalletTopUp settled = pendingTopUp();
        settled.setStatus(WalletTopUp.Status.COMPLETED);
        when(walletTopUpService.complete(eq(101L), eq(WalletTopUp.Provider.PAYME),
                eq("payme-tx-1"), any())).thenReturn(settled);

        String body = """
                { "id": 1, "method": "PerformTransaction",
                  "params": { "id": "payme-tx-1", "account": { "top_up_id": "101" } } }
                """;

        mockMvc.perform(post("/api/v1/webhook/wallet/payme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.transaction").value("payme-tx-1"))
                .andExpect(jsonPath("$.result.state").value(2));
    }

    @Test
    @DisplayName("Payme unknown method returns -32601")
    void payme_methodNotFound() throws Exception {
        String body = """
                { "id": 1, "method": "CreateTransaction",
                  "params": { "id": "payme-tx-1" } }
                """;

        mockMvc.perform(post("/api/v1/webhook/wallet/payme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }
}
