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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletTopUpWebhookControllerTest {

    private static final String SECRET = "click-secret";
    private static final String PAYME_KEY = "payme-key";
    private static final String SIGN_TIME = "2026-01-01 00:00:00";

    @Mock private WalletTopUpService walletTopUpService;
    @InjectMocks private WalletTopUpWebhookController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        // Secrets ARE configured now — the webhooks fail CLOSED without them, so the happy-path tests
        // must present a valid signature / auth. The blank-key rejection is covered by its own tests.
        ReflectionTestUtils.setField(controller, "clickServiceId", "1234");
        ReflectionTestUtils.setField(controller, "clickSecretKey", SECRET);
        ReflectionTestUtils.setField(controller, "paymeMerchantKey", PAYME_KEY);
    }

    /** Mirrors the production Click MD5: join(parts) → md5 hex. */
    private static String clickSign(String... parts) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String paymeAuth() {
        return "Basic " + Base64.getEncoder().encodeToString(("Paycom:" + PAYME_KEY).getBytes(StandardCharsets.UTF_8));
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
        // prepare signs with an empty merchant_prepare_id
        String sign = clickSign("clk-1", "1234", SECRET, "101", "", "50000", "0", SIGN_TIME);

        mockMvc.perform(post("/api/v1/webhook/wallet/click/prepare")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("amount", "50000")
                        .param("action", "0")
                        .param("sign_time", SIGN_TIME)
                        .param("sign_string", sign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0))
                .andExpect(jsonPath("$.merchant_prepare_id").value("101"));
    }

    @Test
    @DisplayName("Click prepare rejects amount mismatch")
    void clickPrepare_amountMismatch() throws Exception {
        when(walletTopUpService.getById(101L)).thenReturn(pendingTopUp());
        String sign = clickSign("clk-1", "1234", SECRET, "101", "", "999", "0", SIGN_TIME);

        mockMvc.perform(post("/api/v1/webhook/wallet/click/prepare")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("amount", "999")
                        .param("action", "0")
                        .param("sign_time", SIGN_TIME)
                        .param("sign_string", sign))
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
        String sign = clickSign("clk-1", "1234", SECRET, "101", "101", "50000", "1", SIGN_TIME);

        mockMvc.perform(post("/api/v1/webhook/wallet/click/complete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("merchant_prepare_id", "101")
                        .param("amount", "50000")
                        .param("action", "1")
                        .param("error", "0")
                        .param("sign_time", SIGN_TIME)
                        .param("sign_string", sign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0))
                .andExpect(jsonPath("$.merchant_confirm_id").value("101"));

        verify(walletTopUpService).complete(eq(101L), eq(WalletTopUp.Provider.CLICK),
                eq("clk-1"), any());
    }

    @Test
    @DisplayName("Click complete forwarded error code triggers fail()")
    void clickComplete_providerError() throws Exception {
        String sign = clickSign("clk-1", "1234", SECRET, "101", "101", "50000", "1", SIGN_TIME);

        mockMvc.perform(post("/api/v1/webhook/wallet/click/complete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("merchant_prepare_id", "101")
                        .param("amount", "50000")
                        .param("action", "1")
                        .param("error", "-9")
                        .param("sign_time", SIGN_TIME)
                        .param("sign_string", sign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(-9));

        verify(walletTopUpService).fail(eq(101L), anyString());
    }

    @Test
    @DisplayName("Click complete is REJECTED (invalid signature) when the secret is not configured — fail closed")
    void clickComplete_blankSecret_rejected() throws Exception {
        ReflectionTestUtils.setField(controller, "clickSecretKey", "");

        mockMvc.perform(post("/api/v1/webhook/wallet/click/complete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "clk-1")
                        .param("service_id", "1234")
                        .param("merchant_trans_id", "101")
                        .param("merchant_prepare_id", "101")
                        .param("amount", "50000")
                        .param("action", "1")
                        .param("error", "0")
                        .param("sign_time", SIGN_TIME)
                        .param("sign_string", "anything"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(-1)); // Invalid signature

        verify(walletTopUpService, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Payme PerformTransaction settles the top-up")
    void payme_perform() throws Exception {
        WalletTopUp settled = pendingTopUp();
        settled.setStatus(WalletTopUp.Status.COMPLETED);
        // The top-up must be looked up and found PENDING before anything is credited.
        when(walletTopUpService.getById(101L)).thenReturn(pendingTopUp());
        when(walletTopUpService.complete(eq(101L), eq(WalletTopUp.Provider.PAYME),
                eq("payme-tx-1"), any())).thenReturn(settled);

        String body = """
                { "id": 1, "method": "PerformTransaction",
                  "params": { "id": "payme-tx-1", "account": { "top_up_id": "101" } } }
                """;

        mockMvc.perform(post("/api/v1/webhook/wallet/payme")
                        .header("Authorization", paymeAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.transaction").value("payme-tx-1"))
                .andExpect(jsonPath("$.result.state").value(2));
    }

    @Test
    @DisplayName("Payme PerformTransaction refuses to re-settle a top-up that is not PENDING")
    void payme_perform_alreadySettled_rejected() throws Exception {
        WalletTopUp done = pendingTopUp();
        done.setStatus(WalletTopUp.Status.COMPLETED);
        when(walletTopUpService.getById(101L)).thenReturn(done);

        String body = """
                { "id": 1, "method": "PerformTransaction",
                  "params": { "id": "payme-tx-1", "account": { "top_up_id": "101" } } }
                """;

        mockMvc.perform(post("/api/v1/webhook/wallet/payme")
                        .header("Authorization", paymeAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-31008));

        verify(walletTopUpService, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Payme PerformTransaction refuses an amount that does not match the top-up")
    void payme_perform_amountMismatch_rejected() throws Exception {
        when(walletTopUpService.getById(101L)).thenReturn(pendingTopUp()); // 50000 sum = 5000000 tiyin

        String body = """
                { "id": 1, "method": "PerformTransaction",
                  "params": { "id": "payme-tx-1", "amount": 100,
                              "account": { "top_up_id": "101" } } }
                """;

        mockMvc.perform(post("/api/v1/webhook/wallet/payme")
                        .header("Authorization", paymeAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-31001));

        verify(walletTopUpService, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Payme unknown method returns -32601")
    void payme_methodNotFound() throws Exception {
        String body = """
                { "id": 1, "method": "CreateTransaction",
                  "params": { "id": "payme-tx-1" } }
                """;

        mockMvc.perform(post("/api/v1/webhook/wallet/payme")
                        .header("Authorization", paymeAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    @DisplayName("Payme is REJECTED (-32504) when the merchant key is not configured — fail closed")
    void payme_blankKey_rejected() throws Exception {
        ReflectionTestUtils.setField(controller, "paymeMerchantKey", "");

        String body = """
                { "id": 1, "method": "PerformTransaction",
                  "params": { "id": "payme-tx-1", "account": { "top_up_id": "101" } } }
                """;

        mockMvc.perform(post("/api/v1/webhook/wallet/payme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32504));

        verify(walletTopUpService, never()).complete(any(), any(), any(), any());
    }
}
