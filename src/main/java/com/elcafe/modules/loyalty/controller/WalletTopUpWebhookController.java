package com.elcafe.modules.loyalty.controller;

import com.elcafe.modules.loyalty.entity.WalletTopUp;
import com.elcafe.modules.loyalty.service.WalletTopUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook endpoints invoked by Click and Payme to confirm wallet
 * top-up payments. These are intentionally outside the
 * Bearer-authenticated surface — they're protected by provider
 * signature verification (Click: MD5 sign_string; Payme: Basic auth
 * with merchant credentials) and a fixed URL path the provider has on
 * file.
 *
 * Click uses a two-call protocol: Prepare validates the merchant can
 * accept, Complete actually credits. Payme uses JSON-RPC with method
 * names (CheckPerformTransaction, CreateTransaction, PerformTransaction,
 * CancelTransaction). Both protocols are settled into our internal
 * WalletTopUpService.complete() once the provider says the money moved.
 *
 * Note on credentials: signature verification keys come from
 * application properties — when those properties are at default values
 * (dev / not yet provisioned), we still verify the structure but skip
 * the cryptographic check and log a warning. Production deploys must
 * set click.secret-key and payme.merchant-key.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhook/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet Webhooks", description = "Payment provider callbacks for wallet top-ups")
public class WalletTopUpWebhookController {

    private final WalletTopUpService walletTopUpService;

    @Value("${click.secret-key:}")
    private String clickSecretKey;

    @Value("${click.service-id:0}")
    private String clickServiceId;

    @Value("${payme.merchant-key:}")
    private String paymeMerchantKey;

    // ============== Click ==============

    /**
     * Click Prepare callback — provider asks whether the merchant is
     * willing to accept the payment. Click expects:
     *   error = 0  → ready to accept (returns merchant_prepare_id)
     *   error < 0  → reject with reason
     *
     * We verify the signature and the top-up state (must exist + be
     * PENDING + amount must match). Reservation of funds is the
     * provider's concern; we just acknowledge.
     */
    @PostMapping(value = "/click/prepare", consumes = "application/x-www-form-urlencoded")
    @Operation(summary = "Click Prepare callback")
    public ResponseEntity<Map<String, Object>> clickPrepare(
            @RequestParam("click_trans_id") String clickTransId,
            @RequestParam("service_id") String serviceId,
            @RequestParam("merchant_trans_id") String merchantTransId,
            @RequestParam("amount") String amount,
            @RequestParam("action") String action,
            @RequestParam("sign_time") String signTime,
            @RequestParam("sign_string") String signString) {

        log.info("Click prepare: click_trans_id={}, merchant_trans_id={}, amount={}, action={}",
                clickTransId, merchantTransId, amount, action);

        Map<String, Object> reply = new HashMap<>();
        reply.put("click_trans_id", clickTransId);
        reply.put("merchant_trans_id", merchantTransId);

        if (!"0".equals(action)) {
            return ResponseEntity.ok(errorReply(reply, -3, "Action not supported"));
        }
        if (!serviceId.equals(clickServiceId)) {
            return ResponseEntity.ok(errorReply(reply, -1, "Unknown service_id"));
        }
        if (!verifyClickSignature(clickTransId, serviceId, merchantTransId, "",
                amount, action, signTime, signString)) {
            return ResponseEntity.ok(errorReply(reply, -1, "Invalid signature"));
        }

        try {
            WalletTopUp topUp = walletTopUpService.getById(Long.parseLong(merchantTransId));
            if (topUp.getStatus() != WalletTopUp.Status.PENDING) {
                return ResponseEntity.ok(errorReply(reply, -4, "Top-up not PENDING"));
            }
            // Amount sanity check — Click sends amount in sum (decimal string).
            if (topUp.getAmount().compareTo(new java.math.BigDecimal(amount)) != 0) {
                return ResponseEntity.ok(errorReply(reply, -2, "Amount mismatch"));
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.ok(errorReply(reply, -5, "Invalid merchant_trans_id"));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(errorReply(reply, -5, "Top-up not found"));
        }

        reply.put("merchant_prepare_id", merchantTransId);
        reply.put("error", 0);
        reply.put("error_note", "Success");
        return ResponseEntity.ok(reply);
    }

    /**
     * Click Complete callback — provider has charged the customer and
     * is asking us to mark the merchant transaction settled. We credit
     * the wallet via WalletTopUpService.complete (idempotent), then
     * return error=0.
     */
    @PostMapping(value = "/click/complete", consumes = "application/x-www-form-urlencoded")
    @Operation(summary = "Click Complete callback")
    public ResponseEntity<Map<String, Object>> clickComplete(
            @RequestParam("click_trans_id") String clickTransId,
            @RequestParam("service_id") String serviceId,
            @RequestParam("merchant_trans_id") String merchantTransId,
            @RequestParam("merchant_prepare_id") String merchantPrepareId,
            @RequestParam("amount") String amount,
            @RequestParam("action") String action,
            @RequestParam(value = "error", required = false, defaultValue = "0") String error,
            @RequestParam("sign_time") String signTime,
            @RequestParam("sign_string") String signString) {

        log.info("Click complete: click_trans_id={}, merchant_trans_id={}, error={}",
                clickTransId, merchantTransId, error);

        Map<String, Object> reply = new HashMap<>();
        reply.put("click_trans_id", clickTransId);
        reply.put("merchant_trans_id", merchantTransId);

        if (!"1".equals(action)) {
            return ResponseEntity.ok(errorReply(reply, -3, "Action not supported"));
        }
        if (!verifyClickSignature(clickTransId, serviceId, merchantTransId, merchantPrepareId,
                amount, action, signTime, signString)) {
            return ResponseEntity.ok(errorReply(reply, -1, "Invalid signature"));
        }

        Long topUpId;
        try {
            topUpId = Long.parseLong(merchantTransId);
        } catch (NumberFormatException e) {
            return ResponseEntity.ok(errorReply(reply, -5, "Invalid merchant_trans_id"));
        }

        if (!"0".equals(error)) {
            walletTopUpService.fail(topUpId, "Click error code " + error);
            return ResponseEntity.ok(errorReply(reply, -9, "Cancelled by provider"));
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("clickTransId", clickTransId);
        meta.put("clickSignTime", signTime);
        walletTopUpService.complete(topUpId, WalletTopUp.Provider.CLICK, clickTransId, meta);

        reply.put("merchant_confirm_id", merchantTransId);
        reply.put("error", 0);
        reply.put("error_note", "Success");
        return ResponseEntity.ok(reply);
    }

    private boolean verifyClickSignature(String clickTransId, String serviceId,
                                         String merchantTransId, String merchantPrepareId,
                                         String amount, String action, String signTime,
                                         String providedSignature) {
        if (clickSecretKey == null || clickSecretKey.isBlank()) {
            // Fail CLOSED: without the secret we cannot verify the signature, and an unconfigured Click
            // integration cannot produce a legitimate signed webhook anyway. Accepting here let anyone
            // forge a top-up completion and credit a wallet with no payment.
            log.error("click.secret-key not configured — REJECTING unverifiable wallet webhook");
            return false;
        }
        // Click MD5 input: click_trans_id + service_id + secret_key +
        // merchant_trans_id + [merchant_prepare_id] + amount + action + sign_time
        String payload = clickTransId + serviceId + clickSecretKey + merchantTransId
                + (merchantPrepareId == null ? "" : merchantPrepareId)
                + amount + action + signTime;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString().equalsIgnoreCase(providedSignature);
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 unavailable in this JVM", e);
            return false;
        }
    }

    private Map<String, Object> errorReply(Map<String, Object> base, int code, String note) {
        base.put("error", code);
        base.put("error_note", note);
        return base;
    }

    // ============== Payme ==============

    /**
     * Payme JSON-RPC endpoint. Payme makes a single POST with a method
     * name (CheckPerformTransaction, CreateTransaction,
     * PerformTransaction, CancelTransaction, CheckTransaction,
     * GetStatement) and a params object. The full state-machine
     * implementation is beyond the scope of this initial cut — this
     * skeleton handles PerformTransaction (the credit-the-wallet step)
     * with credentials check, and rejects everything else with
     * "method not found" so the integration can be filled out in a
     * follow-up once Payme sandbox credentials are wired.
     *
     * Returns a JSON-RPC envelope. Errors use Payme's documented
     * negative-integer codes:
     *   -32504 — insufficient privileges (bad credentials)
     *   -32601 — method not found
     */
    @PostMapping(value = "/payme", consumes = "application/json")
    @Operation(summary = "Payme JSON-RPC endpoint",
            description = "Initial implementation: PerformTransaction only. Other methods return -32601 " +
                    "until the full Payme state machine is wired in a follow-up.")
    public ResponseEntity<Map<String, Object>> payme(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("id", request.get("id"));

        if (!verifyPaymeAuth(authHeader)) {
            envelope.put("error", paymeError(-32504, "Insufficient privileges"));
            return ResponseEntity.ok(envelope);
        }

        String method = (String) request.get("method");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        if (!"PerformTransaction".equals(method)) {
            envelope.put("error", paymeError(-32601,
                    "Method " + method + " not implemented in this build"));
            return ResponseEntity.ok(envelope);
        }

        String paymeTxId = String.valueOf(params.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> account = (Map<String, Object>) params.getOrDefault("account", Map.of());
        Long topUpId = parseLong(account.get("top_up_id"));
        if (topUpId == null) {
            envelope.put("error", paymeError(-31050, "Missing or invalid top_up_id"));
            return ResponseEntity.ok(envelope);
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("paymeTransactionId", paymeTxId);
        WalletTopUp topUp = walletTopUpService.complete(
                topUpId, WalletTopUp.Provider.PAYME, paymeTxId, meta);

        Map<String, Object> result = new HashMap<>();
        result.put("transaction", paymeTxId);
        result.put("perform_time", System.currentTimeMillis());
        result.put("state", 2); // Payme state code: paid
        envelope.put("result", result);
        log.info("Payme PerformTransaction settled top-up {} ({})", topUp.getId(), paymeTxId);
        return ResponseEntity.ok(envelope);
    }

    private boolean verifyPaymeAuth(String authHeader) {
        if (paymeMerchantKey == null || paymeMerchantKey.isBlank()) {
            // Fail CLOSED (see verifyClickSignature): an unconfigured Payme integration cannot produce a
            // legitimate authenticated webhook, so accepting one is a free-wallet-credit hole.
            log.error("payme.merchant-key not configured — REJECTING unverifiable wallet webhook");
            return false;
        }
        if (authHeader == null || !authHeader.startsWith("Basic ")) return false;
        String b64 = authHeader.substring("Basic ".length()).trim();
        String decoded = new String(java.util.Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        // Payme sends "Paycom:<merchant_key>"
        int colon = decoded.indexOf(':');
        if (colon < 0) return false;
        return paymeMerchantKey.equals(decoded.substring(colon + 1));
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> paymeError(int code, String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("code", code);
        err.put("message", message);
        return err;
    }
}
