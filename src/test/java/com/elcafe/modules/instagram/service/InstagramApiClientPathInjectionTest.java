package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Guards the Graph path-injection fix: {@code commentId} and {@code instagramAccountId} arrive straight
 * off the (public) webhook, so a value like {@code me/subscribed_apps?access_token=} must never be
 * concatenated into the request URL — it would re-target the POST at a different Graph edge while still
 * carrying the merchant's token. The client validates both against {@code ^[0-9_]{1,40}$} and builds the
 * URL with {@code UriComponentsBuilder}, so a malformed id is rejected BEFORE any HTTP call.
 *
 * <p>{@link MockRestServiceServer} makes that assertable: with no expectation set, the mock server fails
 * the test if the client attempts <em>any</em> request — so a malicious id that reached the wire would
 * turn this red. Remove the validation and these go red; that is the regression this pins.
 */
class InstagramApiClientPathInjectionTest {

    private static final String BASE = "https://graph.test";
    private static final String VERSION = "v19.0";
    private static final String GRAPH = BASE + "/" + VERSION;

    private InstagramApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new InstagramApiClient(new RestTemplateBuilder(), BASE, VERSION);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    private InstagramBotConfig config(String accountId) {
        return InstagramBotConfig.builder()
                .instagramAccountId(accountId)
                .accessToken("merchant-token")
                .build();
    }

    @Test
    @DisplayName("a comment id carrying a path/query escape is rejected before any HTTP call")
    void maliciousCommentIdNeverReachesTheWire() {
        // No server expectation: any request the client attempts fails the test.
        InstagramSendResult result = client.replyToComment(
                config("17841400000000000"), "me/subscribed_apps?access_token=", "gotcha");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();   // no request was made
    }

    @Test
    @DisplayName("a malformed instagram account id is rejected before any DM HTTP call")
    void maliciousAccountIdNeverReachesTheWire() {
        InstagramSendResult result = client.sendMessage(
                config("me/subscribed_apps?access_token="), "igsid-1", "gotcha");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();
    }

    @Test
    @DisplayName("a well-formed comment reply hits exactly {id}/replies with the token in a bearer header")
    void wellFormedCommentReplyBuildsASafeUrl() {
        server.expect(requestTo(GRAPH + "/17841999/replies"))
                .andExpect(method(POST))
                // Token travels in the Authorization header, never the query string.
                .andExpect(header("Authorization", "Bearer merchant-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        InstagramSendResult result = client.replyToComment(config("17841400000000000"), "17841999", "hi");

        assertThat(result.delivered()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("a well-formed DM hits exactly {accountId}/messages with the token in a bearer header")
    void wellFormedDmBuildsASafeUrl() {
        server.expect(requestTo(GRAPH + "/17841400000000000/messages"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer merchant-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        InstagramSendResult result = client.sendMessage(config("17841400000000000"), "igsid-1", "hi");

        assertThat(result.delivered()).isTrue();
        server.verify();
    }

    // -------------------------------------------------------------------------
    // Photo DMs (V176 campaign promo image) — same {accountId}/messages edge, image attachment body.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a well-formed photo DM hits {accountId}/messages with an image attachment and a bearer token")
    void wellFormedPhotoBuildsAnImageAttachment() {
        server.expect(requestTo(GRAPH + "/17841400000000000/messages"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer merchant-token"))
                .andExpect(jsonPath("$.message.attachment.type").value("image"))
                .andExpect(jsonPath("$.message.attachment.payload.url").value("https://cdn.example/p.jpg"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        InstagramSendResult result = client.sendPhoto(
                config("17841400000000000"), "igsid-1", "https://cdn.example/p.jpg");

        assertThat(result.delivered()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("a malformed instagram account id is rejected before any photo HTTP call")
    void maliciousAccountIdNeverReachesTheWireForPhoto() {
        InstagramSendResult result = client.sendPhoto(
                config("me/subscribed_apps?access_token="), "igsid-1", "https://cdn.example/p.jpg");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();
    }

    @Test
    @DisplayName("kill switch: a photo DM never touches Meta")
    void killSwitchSuppressesPhoto() {
        ReflectionTestUtils.setField(client, "enabled", false);

        InstagramSendResult result = client.sendPhoto(
                config("17841400000000000"), "igsid-1", "https://cdn.example/p.jpg");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();
    }

    // -------------------------------------------------------------------------
    // Kill switch (instagram.enabled=false) — same MockRestServiceServer guarantee: nothing reaches Meta.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("kill switch: a DM never touches Meta and reports the integration disabled")
    void killSwitchSuppressesDm() {
        ReflectionTestUtils.setField(client, "enabled", false);

        InstagramSendResult result = client.sendMessage(config("17841400000000000"), "igsid-1", "hi");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();   // no request was made
    }

    @Test
    @DisplayName("kill switch: a comment reply never touches Meta")
    void killSwitchSuppressesCommentReply() {
        ReflectionTestUtils.setField(client, "enabled", false);

        InstagramSendResult result = client.replyToComment(config("17841400000000000"), "17841999", "hi");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();
    }
}
