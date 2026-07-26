package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.enums.InstagramInboundKind;
import com.elcafe.modules.instagram.service.InstagramApiClient;
import com.elcafe.modules.instagram.service.InstagramBotService;
import com.elcafe.modules.instagram.service.InstagramWebhookDedupService;
import com.elcafe.modules.instagram.service.InstagramWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end fail-CLOSED proof for the Instagram webhook POST — the endpoint the "anyone reaching the
 * POST forges messaging events" finding names.
 *
 * <p>Drives the real {@link InstagramWebhookController} over the real {@link InstagramWebhookService}
 * (only {@link InstagramBotService}/{@link InstagramApiClient} are mocked), so the actual HMAC check
 * runs. Every rejection path must return 403 <em>and</em> reach no processing: no
 * {@code handleIncomingMessage}, so no subscriber row is created, no phone/address injected, no Graph
 * call fired on the merchant's token. The one accept path proves a genuinely-signed delivery still 200s
 * — this is a guard against fail-closed, not a blanket deny.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookControllerFailClosedTest {

    private static final String ACCOUNT_ID = "17841400000000000";
    private static final String SECRET = "super-secret-meta-app-secret";
    private static final byte[] BODY = ("{\"object\":\"instagram\",\"entry\":[{\"id\":\"" + ACCOUNT_ID
            + "\",\"messaging\":[{\"sender\":{\"id\":\"igsid-1\"},\"message\":{\"text\":\"hi\"}}]}]}")
            .getBytes(StandardCharsets.UTF_8);

    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient apiClient;
    @Mock private InstagramWebhookDedupService dedupService;

    private InstagramWebhookController controller;

    @BeforeEach
    void setUp() {
        InstagramWebhookService webhookService =
                new InstagramWebhookService(botService, apiClient, dedupService);
        controller = new InstagramWebhookController(webhookService, botService, new ObjectMapper());
    }

    private static String sign(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private InstagramBotConfig config(String appSecret) {
        return InstagramBotConfig.builder()
                .restaurantId(7L).instagramAccountId(ACCOUNT_ID)
                .appSecret(appSecret).isActive(true)
                .build();
    }

    private void assertRejectedWithoutProcessing(ResponseEntity<Void> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(botService, never()).handleIncomingMessage(any(), any(), any(),
                any(InstagramInboundKind.class), any(), any());
    }

    @Test
    @DisplayName("a config with NO app secret rejects the POST 403 — never processed")
    void noAppSecretIsRejectedAtTheEndpoint() throws Exception {
        // The exact attack: the receiving account's config has no app secret (blank stores null,
        // clearCredentials nulls it). The old code returned true here and processed the forged body.
        when(botService.getConfigByInstagramAccountId(ACCOUNT_ID)).thenReturn(config(null));

        assertRejectedWithoutProcessing(controller.receive(sign(BODY, SECRET), BODY));
    }

    @Test
    @DisplayName("an unknown receiving account rejects the POST 403 — never processed")
    void unknownAccountIsRejected() throws Exception {
        when(botService.getConfigByInstagramAccountId(ACCOUNT_ID)).thenReturn(null);

        assertRejectedWithoutProcessing(controller.receive(sign(BODY, SECRET), BODY));
    }

    @Test
    @DisplayName("a missing X-Hub-Signature-256 header rejects the POST 403 — never processed")
    void missingSignatureIsRejected() {
        when(botService.getConfigByInstagramAccountId(ACCOUNT_ID)).thenReturn(config(SECRET));

        assertRejectedWithoutProcessing(controller.receive(null, BODY));
    }

    @Test
    @DisplayName("a signature forged under the wrong secret rejects the POST 403 — never processed")
    void forgedSignatureIsRejected() throws Exception {
        when(botService.getConfigByInstagramAccountId(ACCOUNT_ID)).thenReturn(config(SECRET));

        assertRejectedWithoutProcessing(controller.receive(sign(BODY, "attacker-secret"), BODY));
    }

    @Test
    @DisplayName("a genuinely-signed POST is accepted 200 — fail-closed, not deny-all")
    void genuinelySignedPostIsAccepted() throws Exception {
        when(botService.getConfigByInstagramAccountId(ACCOUNT_ID)).thenReturn(config(SECRET));

        ResponseEntity<Void> response = controller.receive(sign(BODY, SECRET), BODY);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
