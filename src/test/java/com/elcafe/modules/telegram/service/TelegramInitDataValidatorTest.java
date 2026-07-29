package com.elcafe.modules.telegram.service;

import com.elcafe.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Signs {@code initData} exactly as a Telegram client does — {@code secret = HMAC("WebAppData", token)},
 * {@code hash = HMAC(secret, data_check_string)} — so a passing round-trip proves the validator matches
 * Telegram, and the negative cases prove it rejects anything not signed by the same bot token or gone stale.
 */
class TelegramInitDataValidatorTest {

    private static final String BOT_TOKEN = "123456:AAExampleTokenForTestsOnly_not_a_real_one";
    private static final String OTHER_TOKEN = "999999:BBSomeOtherBotEntirelyDifferentToken____";

    private TelegramInitDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TelegramInitDataValidator();
        ReflectionTestUtils.setField(validator, "authTtlSeconds", 86400L);
    }

    @Test
    @DisplayName("a payload signed by the bot token validates and yields the Telegram user")
    void validSignature_authenticatesUser() {
        Map<String, String> fields = freshFields(Instant.now().getEpochSecond());
        String initData = sign(BOT_TOKEN, fields);

        TelegramInitDataValidator.ValidatedTelegramUser user = validator.validate(initData, BOT_TOKEN);

        assertThat(user.telegramUserId()).isEqualTo(42L);
        assertThat(user.firstName()).isEqualTo("Ali");
        assertThat(user.username()).isEqualTo("ali_test");
        assertThat(user.languageCode()).isEqualTo("uz");
    }

    @Test
    @DisplayName("a payload signed by a DIFFERENT bot token is rejected (cross-tenant / forged)")
    void wrongToken_isRejected() {
        String initData = sign(OTHER_TOKEN, freshFields(Instant.now().getEpochSecond()));
        assertThatThrownBy(() -> validator.validate(initData, BOT_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("tampering with a signed field after signing breaks the hash")
    void tamperedField_isRejected() {
        long authDate = Instant.now().getEpochSecond();
        Map<String, String> fields = freshFields(authDate);
        String validHash = computeHash(BOT_TOKEN, fields);

        // Keep the original hash but swap the user for a different identity.
        Map<String, String> tampered = new LinkedHashMap<>(fields);
        tampered.put("user", "{\"id\":9999,\"first_name\":\"Mallory\"}");
        String forged = buildInitData(tampered, validHash);

        assertThatThrownBy(() -> validator.validate(forged, BOT_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("a correctly-signed but stale payload is rejected on freshness")
    void staleAuthDate_isRejected() {
        long stale = Instant.now().getEpochSecond() - 200_000; // > 86400s TTL
        String initData = sign(BOT_TOKEN, freshFields(stale));
        assertThatThrownBy(() -> validator.validate(initData, BOT_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("no hash field → rejected")
    void missingHash_isRejected() {
        String initData = buildInitData(freshFields(Instant.now().getEpochSecond()), null);
        assertThatThrownBy(() -> validator.validate(initData, BOT_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("hash");
    }

    @Test
    @DisplayName("blank initData / blank token → rejected, never NPEs")
    void blankInputs_areRejected() {
        assertThatThrownBy(() -> validator.validate("", BOT_TOKEN)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> validator.validate(sign(BOT_TOKEN, freshFields(Instant.now().getEpochSecond())), ""))
                .isInstanceOf(UnauthorizedException.class);
    }

    // --- helpers: mirror the client side of Telegram's scheme ---

    private Map<String, String> freshFields(long authDate) {
        // Decoded (raw) field values, as they appear inside the data-check-string.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("query_id", "AAHdF6IQAAAAAN0XohDhrOrc");
        fields.put("user", "{\"id\":42,\"first_name\":\"Ali\",\"last_name\":\"Valiyev\","
                + "\"username\":\"ali_test\",\"language_code\":\"uz\"}");
        fields.put("auth_date", String.valueOf(authDate));
        return fields;
    }

    private String sign(String token, Map<String, String> decodedFields) {
        return buildInitData(decodedFields, computeHash(token, decodedFields));
    }

    private String computeHash(String token, Map<String, String> decodedFields) {
        // data_check_string: all fields except hash/signature, sorted by key, key=value joined with '\n'.
        Map<String, String> sorted = new TreeMap<>(decodedFields);
        sorted.remove("hash");
        sorted.remove("signature");
        StringBuilder dcs = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) dcs.append('\n');
            dcs.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        byte[] hash = hmac(secret, dcs.toString().getBytes(StandardCharsets.UTF_8));
        return toHex(hash);
    }

    private String buildInitData(Map<String, String> decodedFields, String hash) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : decodedFields.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=')
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        if (hash != null) {
            if (sb.length() > 0) sb.append('&');
            sb.append("hash=").append(hash);
        }
        return sb.toString();
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
