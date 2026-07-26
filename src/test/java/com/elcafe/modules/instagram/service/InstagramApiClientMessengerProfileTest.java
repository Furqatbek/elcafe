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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Guards {@link InstagramApiClient#setPersistentMenu} / {@link InstagramApiClient#setIceBreakers} —
 * the persistent-menu + ice-breaker push that gives a first-time Instagram DM visitor tappable menu
 * options and suggested questions before they ever send a message (Meta renders both with no
 * messaging window required, unlike every send in {@link InstagramApiClientPathInjectionTest} /
 * {@link InstagramApiClientPrivateReplyTest}).
 *
 * <p>Mirrors those two suites' style throughout: {@link MockRestServiceServer} with no expectation set
 * fails the test if the client attempts ANY request, which is what makes "the kill switch suppresses
 * this call" and "a malformed account id never reaches the wire" assertable — both edges POST to the
 * SAME {@code {accountId}/messenger_profile} Graph edge, sharing {@code postToMessengerProfile}'s kill
 * switch + account-id validation, exactly like {@code postToMessagesApi} backs every send.
 */
class InstagramApiClientMessengerProfileTest {

    private static final String BASE = "https://graph.test";
    private static final String VERSION = "v19.0";
    private static final String GRAPH = BASE + "/" + VERSION;

    private static final List<Map<String, String>> MENU = List.of(
            Map.of("type", "postback", "title", "📋 Menyu", "payload", "IG_MENU_MENU")
    );

    private static final List<Map<String, String>> ICE_BREAKERS = List.of(
            Map.of("question", "Qanday buyurtma beraman?", "payload", "IG_ICEBREAKER_ORDER")
    );

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
    @DisplayName("setPersistentMenu POSTs exactly {accountId}/messenger_profile with a bearer header and the persistent_menu JSON shape")
    void persistentMenuBuildsExpectedRequest() {
        server.expect(requestTo(GRAPH + "/17841400000000000/messenger_profile"))
                .andExpect(method(POST))
                // Token travels in the Authorization header, never the query string — same discipline
                // as every existing send.
                .andExpect(header("Authorization", "Bearer merchant-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{"
                        + "\"platform\":\"instagram\","
                        + "\"persistent_menu\":[{"
                        + "\"locale\":\"default\","
                        + "\"composer_input_disabled\":false,"
                        + "\"call_to_actions\":[{\"type\":\"postback\",\"title\":\"📋 Menyu\",\"payload\":\"IG_MENU_MENU\"}]"
                        + "}]"
                        + "}"))
                .andRespond(withSuccess("{\"result\":\"success\"}", MediaType.APPLICATION_JSON));

        InstagramSendResult result = client.setPersistentMenu(config("17841400000000000"), MENU);

        assertThat(result.delivered()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("setIceBreakers POSTs exactly {accountId}/messenger_profile with a bearer header and the ice_breakers JSON shape")
    void iceBreakersBuildExpectedRequest() {
        server.expect(requestTo(GRAPH + "/17841400000000000/messenger_profile"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer merchant-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{"
                        + "\"platform\":\"instagram\","
                        + "\"ice_breakers\":[{"
                        + "\"locale\":\"default\","
                        + "\"call_to_actions\":[{\"question\":\"Qanday buyurtma beraman?\",\"payload\":\"IG_ICEBREAKER_ORDER\"}]"
                        + "}]"
                        + "}"))
                .andRespond(withSuccess("{\"result\":\"success\"}", MediaType.APPLICATION_JSON));

        InstagramSendResult result = client.setIceBreakers(config("17841400000000000"), ICE_BREAKERS);

        assertThat(result.delivered()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("a malformed instagram account id is rejected before any messenger_profile HTTP call")
    void malformedAccountIdNeverReachesTheWire() {
        // No server expectation: any request the client attempts fails the test.
        InstagramSendResult menuResult = client.setPersistentMenu(
                config("me/subscribed_apps?access_token="), MENU);
        InstagramSendResult iceBreakersResult = client.setIceBreakers(
                config("me/subscribed_apps?access_token="), ICE_BREAKERS);

        assertThat(menuResult.delivered()).isFalse();
        assertThat(menuResult.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        assertThat(iceBreakersResult.delivered()).isFalse();
        assertThat(iceBreakersResult.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();   // no request was made
    }

    // -------------------------------------------------------------------------
    // Kill switch (instagram.enabled=false) — same MockRestServiceServer guarantee: nothing reaches Meta.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("kill switch: setPersistentMenu never touches Meta and reports the integration disabled")
    void killSwitchSuppressesPersistentMenu() {
        ReflectionTestUtils.setField(client, "enabled", false);

        InstagramSendResult result = client.setPersistentMenu(config("17841400000000000"), MENU);

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();   // no request was made
    }

    @Test
    @DisplayName("kill switch: setIceBreakers never touches Meta and reports the integration disabled")
    void killSwitchSuppressesIceBreakers() {
        ReflectionTestUtils.setField(client, "enabled", false);

        InstagramSendResult result = client.setIceBreakers(config("17841400000000000"), ICE_BREAKERS);

        assertThat(result.delivered()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();
    }
}
