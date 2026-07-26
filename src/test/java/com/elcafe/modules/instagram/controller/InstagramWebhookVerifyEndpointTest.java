package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.service.InstagramBotService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the hardening of the GET hub-challenge handshake:
 *   * the challenge is reflected caller-supplied content, so the endpoint {@code produces} text/plain
 *     only — an {@code Accept: text/html} request gets 406, never the challenge echoed as HTML (which
 *     would be reflected XSS on the API origin for anyone holding the verify token); and
 *   * every failure path is ONE opaque, empty 403 — no distinct bodies to fingerprint whether an
 *     integration exists for a given token.
 * Drop {@code produces = TEXT_PLAIN_VALUE} and the HTML case turns into a 200 reflecting the payload;
 * that is the regression this pins.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookVerifyEndpointTest {

    private static final String TOKEN = "good-verify-token";

    @Mock private InstagramWebhookService webhookService;
    @Mock private InstagramBotService botService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        InstagramWebhookController controller =
                new InstagramWebhookController(webhookService, botService, new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(botService.getConfigByVerifyToken(TOKEN))
                .thenReturn(InstagramBotConfig.builder().restaurantId(7L).build());
    }

    @Test
    @DisplayName("a genuine handshake echoes the challenge as text/plain")
    void genuineHandshakeEchoesChallengeAsPlainText() throws Exception {
        mvc.perform(get("/api/v1/instagram/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", TOKEN)
                        .param("hub.challenge", "12345")
                        .accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("12345"));
    }

    @Test
    @DisplayName("an Accept: text/html request cannot get the challenge reflected as HTML (406, not XSS)")
    void textHtmlAcceptCannotReflectChallengeAsHtml() throws Exception {
        mvc.perform(get("/api/v1/instagram/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", TOKEN)
                        .param("hub.challenge", "<script>alert(1)</script>")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable());   // produces=text/plain → no HTML handler
    }

    @Test
    @DisplayName("every failure path is one opaque empty 403 — no fingerprinting")
    void allFailuresAreOneOpaque403() throws Exception {
        mvc.perform(get("/api/v1/instagram/webhook")
                        .param("hub.mode", "unsubscribe")   // wrong mode
                        .param("hub.verify_token", TOKEN)
                        .param("hub.challenge", "x"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        mvc.perform(get("/api/v1/instagram/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong-token")   // unknown token → no config
                        .param("hub.challenge", "x"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }
}
