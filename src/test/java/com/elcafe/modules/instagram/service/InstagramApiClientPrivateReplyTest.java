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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Guards {@link InstagramApiClient#sendPrivateReply} — the Meta "private reply" send used by the
 * "comment {keyword} and we'll DM you" growth flow (V173). Mirrors
 * {@link InstagramApiClientPathInjectionTest}'s style: {@code commentId} and
 * {@code instagramAccountId} both arrive off the (public) webhook, so both are validated against
 * {@code ^[0-9_]{1,40}$} and the URL is built with {@code UriComponentsBuilder} — a malformed id is
 * rejected BEFORE any HTTP call, exactly like {@code replyToComment} and {@code sendMessage}.
 *
 * <p>Unlike {@code replyToComment}, {@code commentId} here lands in the JSON body
 * ({@code recipient.comment_id}), not the URL path — but the client validates it identically anyway
 * (see the method's own javadoc), and this suite pins that too: a malformed id must never reach the
 * wire even though, for this endpoint, the injection vector it guards against does not technically
 * apply to the id's own placement.
 *
 * <p>{@link MockRestServiceServer} makes "never reached the wire" assertable: with no expectation set,
 * the mock server fails the test if the client attempts ANY request.
 */
class InstagramApiClientPrivateReplyTest {

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
        InstagramSendResult result = client.sendPrivateReply(
                config("17841400000000000"), "me/subscribed_apps?access_token=", "gotcha");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();   // no request was made
    }

    @Test
    @DisplayName("a malformed instagram account id is rejected before any HTTP call")
    void maliciousAccountIdNeverReachesTheWire() {
        InstagramSendResult result = client.sendPrivateReply(
                config("me/subscribed_apps?access_token="), "17841999", "gotcha");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();
    }

    @Test
    @DisplayName("a well-formed private reply hits exactly {accountId}/messages with recipient.comment_id "
            + "and the token in a bearer header")
    void wellFormedPrivateReplyBuildsTheExpectedRequest() {
        server.expect(requestTo(GRAPH + "/17841400000000000/messages"))
                .andExpect(method(POST))
                // Token travels in the Authorization header, never the query string.
                .andExpect(header("Authorization", "Bearer merchant-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"recipient\":{\"comment_id\":\"17841999\"},\"message\":{\"text\":\"Mana promo kod: ABC123\"}}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        InstagramSendResult result = client.sendPrivateReply(
                config("17841400000000000"), "17841999", "Mana promo kod: ABC123");

        assertThat(result.delivered()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("kill switch: a private reply never touches Meta and reports the integration disabled")
    void killSwitchSuppressesPrivateReply() {
        ReflectionTestUtils.setField(client, "enabled", false);

        InstagramSendResult result = client.sendPrivateReply(
                config("17841400000000000"), "17841999", "hi");

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();   // no request was made
    }
}
