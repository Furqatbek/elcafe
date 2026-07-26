package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the webhook's fail-CLOSED contract.
 *
 * <p>This used to be the only fail-open webhook receiver in the codebase: {@code verifySignature}
 * returned {@code true} whenever no app secret was configured, so anyone able to reach the endpoint
 * could forge Instagram events for any user. These tests exist so that behaviour can never come back
 * silently — every rejection path is asserted, and the accept path is proved against a real HMAC.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookServiceSignatureTest {

    private static final String SECRET = "super-secret-meta-app-secret";

    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient  apiClient;
    @Mock private InstagramWebhookDedupService dedupService;
    @Mock private InstagramMessageLogger messageLogger;

    @InjectMocks private InstagramWebhookService service;

    private InstagramBotConfig config(String appSecret) {
        return InstagramBotConfig.builder()
                .restaurantId(7L)
                .appSecret(appSecret)
                .isActive(true)
                .build();
    }

    /** The signature Meta would send for this body under this secret. */
    private static String sign(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    void acceptsAGenuineSignature() throws Exception {
        byte[] body = "{\"object\":\"instagram\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(service.verifySignature(body, sign(body, SECRET), config(SECRET))).isTrue();
    }

    @Test
    void rejectsWhenNoAppSecretIsConfigured() throws Exception {
        // The regression that matters: previously this returned true and let the payload through.
        byte[] body = "{\"object\":\"instagram\"}".getBytes(StandardCharsets.UTF_8);
        String anySignature = sign(body, SECRET);

        assertThat(service.verifySignature(body, anySignature, config(null))).isFalse();
        assertThat(service.verifySignature(body, anySignature, config("   "))).isFalse();
    }

    @Test
    void rejectsWhenThereIsNoConfigForTheAccount() throws Exception {
        byte[] body = "{\"object\":\"instagram\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(service.verifySignature(body, sign(body, SECRET), null)).isFalse();
    }

    @Test
    void rejectsATamperedBody() throws Exception {
        byte[] signedBody = "{\"object\":\"instagram\",\"amount\":1}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(signedBody, SECRET);
        byte[] tampered = "{\"object\":\"instagram\",\"amount\":9}".getBytes(StandardCharsets.UTF_8);

        assertThat(service.verifySignature(tampered, signature, config(SECRET))).isFalse();
    }

    @Test
    void rejectsASignatureMadeWithTheWrongSecret() throws Exception {
        byte[] body = "{\"object\":\"instagram\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(service.verifySignature(body, sign(body, "someone-elses-secret"), config(SECRET)))
                .isFalse();
    }

    @Test
    void rejectsMissingOrMalformedHeader() throws Exception {
        byte[] body = "{\"object\":\"instagram\"}".getBytes(StandardCharsets.UTF_8);
        InstagramBotConfig cfg = config(SECRET);
        String valid = sign(body, SECRET);

        assertThat(service.verifySignature(body, null, cfg)).isFalse();
        assertThat(service.verifySignature(body, "", cfg)).isFalse();
        // Right digest, wrong/absent algorithm prefix — Meta always sends "sha256=".
        assertThat(service.verifySignature(body, valid.substring("sha256=".length()), cfg)).isFalse();
        assertThat(service.verifySignature(body, "sha1=" + valid, cfg)).isFalse();
    }

    @Test
    void resolvesTheTenantAccountFromTheFirstEntry() {
        Map<String, Object> payload = Map.of(
                "object", "instagram",
                "entry", List.of(Map.of("id", "17841400000000000", "messaging", List.of())));

        assertThat(service.resolveAccountId(payload)).isEqualTo("17841400000000000");
    }

    @Test
    void resolvesNullWhenThePayloadCarriesNoAccount() {
        assertThat(service.resolveAccountId(null)).isNull();
        assertThat(service.resolveAccountId(Map.of("object", "instagram"))).isNull();
        assertThat(service.resolveAccountId(Map.of("object", "instagram", "entry", List.of()))).isNull();
    }
}
