package com.elcafe.modules.telegram.service;

import com.elcafe.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Validates the {@code initData} a Telegram Mini App forwards from {@code Telegram.WebApp.initData}.
 *
 * <p>This is the trust anchor for the whole Mini-App ordering surface: the bot token signs {@code initData},
 * so a passing check proves both <em>which tenant</em> (the token belongs to one restaurant) and
 * <em>which Telegram user</em> is calling, without the client asserting either. It is the same
 * signature-verification discipline the payment webhooks use — reject on any mismatch, compare in
 * constant time.
 *
 * <p>Algorithm (Telegram "Validating data received via the Mini App"):
 * <pre>
 *   secret_key   = HMAC_SHA256(key = "WebAppData", message = bot_token)
 *   check_hash   = HMAC_SHA256(key = secret_key,  message = data_check_string)
 *   valid        = constantTimeEquals(hex(check_hash), received "hash")
 * </pre>
 * where {@code data_check_string} is every received field except {@code hash} (and {@code signature},
 * which belongs to the separate Ed25519 third-party scheme and is never part of the HMAC input), each
 * rendered as {@code key=value}, sorted by key, joined with {@code '\n'}.
 */
@Slf4j
@Component
public class TelegramInitDataValidator {

    /** The literal HMAC key Telegram specifies for deriving the per-bot secret from the bot token. */
    private static final byte[] WEB_APP_DATA_KEY = "WebAppData".getBytes(StandardCharsets.UTF_8);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * How old {@code auth_date} may be before the payload is refused, in seconds (default 24h). A signed
     * {@code initData} is otherwise replayable forever; a bounded window limits a captured payload to a
     * short reuse horizon.
     */
    @Value("${app.telegram.miniapp.auth-ttl-seconds:86400}")
    private long authTtlSeconds;

    /**
     * Verify {@code initData} against {@code botToken} and return the authenticated Telegram user.
     *
     * @throws UnauthorizedException if the payload is malformed, the signature does not match, or it is stale.
     */
    public ValidatedTelegramUser validate(String initData, String botToken) {
        if (initData == null || initData.isBlank()) {
            throw new UnauthorizedException("Telegram initData is missing");
        }
        if (botToken == null || botToken.isBlank()) {
            // A restaurant with no usable bot token cannot have signed anything — fail closed.
            throw new UnauthorizedException("Telegram bot is not configured for this restaurant");
        }

        Map<String, String> fields = parseFields(initData);

        String providedHash = fields.remove("hash");
        if (providedHash == null || providedHash.isBlank()) {
            throw new UnauthorizedException("Telegram initData has no hash");
        }
        // Not part of the HMAC scheme — Telegram's Ed25519 third-party validation field.
        fields.remove("signature");

        String dataCheckString = buildDataCheckString(fields);

        byte[] secretKey = hmacSha256(WEB_APP_DATA_KEY, botToken.getBytes(StandardCharsets.UTF_8));
        byte[] computed = hmacSha256(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
        String computedHex = toHex(computed);

        // Constant-time compare — a byte-by-byte early return leaks how much of the hash matched.
        if (!MessageDigest.isEqual(
                computedHex.getBytes(StandardCharsets.UTF_8),
                providedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Telegram initData signature is invalid");
        }

        String authDateRaw = fields.get("auth_date");
        long authDate = parseAuthDate(authDateRaw);
        long ageSeconds = Instant.now().getEpochSecond() - authDate;
        if (ageSeconds > authTtlSeconds) {
            throw new UnauthorizedException("Telegram initData has expired");
        }

        return parseUser(fields.get("user"), authDate);
    }

    private Map<String, String> parseFields(String initData) {
        // Sorted so buildDataCheckString is deterministic; also lets us pull hash/signature out cheaply.
        Map<String, String> fields = new TreeMap<>();
        for (String pair : initData.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = urlDecode(pair.substring(0, eq));
            String value = urlDecode(pair.substring(eq + 1));
            fields.put(key, value);
        }
        return fields;
    }

    private String buildDataCheckString(Map<String, String> fields) {
        // fields is a TreeMap → already alphabetical, which is exactly Telegram's required order.
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) {
                sb.append('\n');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private long parseAuthDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UnauthorizedException("Telegram initData has no auth_date");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Telegram initData auth_date is malformed");
        }
    }

    private ValidatedTelegramUser parseUser(String userJson, long authDate) {
        if (userJson == null || userJson.isBlank()) {
            throw new UnauthorizedException("Telegram initData has no user");
        }
        try {
            JsonNode node = objectMapper.readTree(userJson);
            long id = node.path("id").asLong(0);
            if (id == 0) {
                throw new UnauthorizedException("Telegram initData user has no id");
            }
            return new ValidatedTelegramUser(
                    id,
                    textOrNull(node, "first_name"),
                    textOrNull(node, "last_name"),
                    textOrNull(node, "username"),
                    textOrNull(node, "language_code"),
                    authDate);
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Telegram initData user is malformed");
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(message);
        } catch (Exception e) {
            // A JVM without HmacSHA256 is not a caller error — surface as a server fault, not a 401.
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * The authenticated identity extracted from a verified {@code initData}. Never construct this without
     * a passing {@link #validate} call — its whole value is that the signature vouched for these fields.
     */
    public record ValidatedTelegramUser(
            long telegramUserId,
            String firstName,
            String lastName,
            String username,
            String languageCode,
            long authDate) {
    }
}
