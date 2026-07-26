package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.promotion.dto.CouponCodeResponse;
import com.elcafe.modules.promotion.dto.GenerateCouponsRequest;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Routes a comment through the V173 private-reply path: {@code InstagramWebhookService.
 * processChangeEvent} matching {@code privateReplyKeyword} and sending a Meta private-reply DM via
 * {@link InstagramApiClient#sendPrivateReply}, optionally substituting a coupon code minted through
 * {@link CouponService}.
 *
 * <p>Three things this suite exists to pin, each one a place the feature could silently regress:
 * <ol>
 *   <li><b>The dedup check is shared, not doubled.</b> {@code processChangeEvent} calls
 *       {@link InstagramWebhookDedupService#firstDelivery} exactly ONCE per comment even when both the
 *       public auto-reply and the private reply fire — that method is check-and-record, so calling it
 *       a second time for the same key would read "already processed" and silently swallow whichever
 *       action runs second. This is also what caps the coupon mint at one code per comment under Meta's
 *       at-least-once redelivery.</li>
 *   <li><b>Keyword matching is case-insensitive CONTAINS, not equals.</b> A "comment MENU and we'll DM
 *       you" post invites free-form text, so an exact match would miss almost everything.</li>
 *   <li><b>The coupon mint is best-effort.</b> No promotion configured, a promotion that does not
 *       belong to the config's own restaurant, or {@link CouponService} throwing must all fall back to
 *       the {@code {code}}-stripped template and still send the DM — never abandon the send, never
 *       throw out of the webhook thread.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramWebhookServicePrivateReplyTest {

    private static final String ACCOUNT = "17841400000000000";
    private static final long   TENANT  = 5L;
    private static final long   PROMOTION_ID = 42L;

    @Mock private InstagramBotService botService;
    @Mock private InstagramApiClient apiClient;
    @Mock private InstagramWebhookDedupService dedupService;
    @Mock private InstagramMessageLogger messageLogger;
    @Mock private CouponService couponService;
    @Mock private PromotionRepository promotionRepository;

    @InjectMocks private InstagramWebhookService service;

    @BeforeEach
    void defaults() {
        // Every comment here is a first delivery unless a test says otherwise — dedup sharing gets its
        // own dedicated assertions below.
        when(dedupService.firstDelivery(any(), any())).thenReturn(true);
        when(apiClient.sendPrivateReply(any(), any(), any())).thenReturn(InstagramSendResult.ok());
        when(apiClient.replyToComment(any(), any(), any())).thenReturn(InstagramSendResult.ok());
    }

    private InstagramBotConfig config(boolean privateReplyEnabled, String keyword, String template,
                                       Long promotionId, boolean autoReplyEnabled, String autoReplyTemplate) {
        return InstagramBotConfig.builder()
                .restaurantId(TENANT).instagramAccountId(ACCOUNT).isActive(true)
                .privateReplyEnabled(privateReplyEnabled)
                .privateReplyKeyword(keyword)
                .privateReplyTemplate(template)
                .privateReplyPromotionId(promotionId)
                .autoReplyEnabled(autoReplyEnabled)
                .autoReplyTemplate(autoReplyTemplate)
                .build();
    }

    private Map<String, Object> commentDelivery(String commentId, String text) {
        return Map.of("object", "instagram", "entry", List.of(Map.of(
                "id", ACCOUNT,
                "changes", List.of(Map.of(
                        "field", "comments",
                        "value", Map.of("id", commentId, "text", text))))));
    }

    private void deliver(InstagramBotConfig cfg, String commentId, String text) {
        when(botService.getConfigByInstagramAccountId(ACCOUNT)).thenReturn(cfg);
        service.processWebhookPayload(commentDelivery(commentId, text));
    }

    // -------------------------------------------------------------------------
    // Keyword match → coupon minted and substituted
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a matching comment mints a coupon and substitutes {code} into the private-reply DM")
    void keywordMatchMintsAndSubstitutesCode() {
        InstagramBotConfig cfg = config(true, "MENU", "Mana promo kodingiz: {code}!", PROMOTION_ID,
                false, null);
        when(promotionRepository.existsByIdAndRestaurant_Id(PROMOTION_ID, TENANT)).thenReturn(true);
        when(couponService.generateCoupons(any())).thenReturn(
                List.of(CouponCodeResponse.builder().code("PROMO123").build()));

        deliver(cfg, "55", "please send the menu 🙏");   // lowercase comment vs uppercase keyword

        verify(apiClient).sendPrivateReply(eq(cfg), eq("55"), eq("Mana promo kodingiz: PROMO123!"));

        ArgumentCaptor<GenerateCouponsRequest> req = ArgumentCaptor.forClass(GenerateCouponsRequest.class);
        verify(couponService).generateCoupons(req.capture());
        assertThat(req.getValue().getPromotionId()).isEqualTo(PROMOTION_ID);
        assertThat(req.getValue().getCount()).isEqualTo(1);

        verify(messageLogger).record(eq(cfg), eq("55"), isNull(),
                eq(InstagramMessageType.PRIVATE_REPLY), eq("Mana promo kodingiz: PROMO123!"),
                any(InstagramSendResult.class), isNull());
    }

    // -------------------------------------------------------------------------
    // No coupon available → {code} stripped, DM still sent (best-effort)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("no promotion configured: {code} is stripped and the DM still goes out")
    void noPromotionStripsCodeButStillSends() {
        InstagramBotConfig cfg = config(true, "menu", "Salom! Kodingiz: {code} bizga yozing", null,
                false, null);

        deliver(cfg, "56", "menu iltimos");

        verify(apiClient).sendPrivateReply(eq(cfg), eq("56"), eq("Salom! Kodingiz:  bizga yozing"));
        verify(couponService, never()).generateCoupons(any());
    }

    @Test
    @DisplayName("a promotion belonging to another restaurant is treated as no promotion — no mint, DM still sent")
    void foreignPromotionIsIgnoredNotTrusted() {
        InstagramBotConfig cfg = config(true, "menu", "Kodingiz: {code}", PROMOTION_ID, false, null);
        when(promotionRepository.existsByIdAndRestaurant_Id(PROMOTION_ID, TENANT)).thenReturn(false);

        deliver(cfg, "57", "menu?");

        verify(couponService, never()).generateCoupons(any());
        verify(apiClient).sendPrivateReply(eq(cfg), eq("57"), eq("Kodingiz: "));
    }

    @Test
    @DisplayName("a coupon-mint failure never escapes the webhook thread — DM still sent without a code")
    void mintFailureIsBestEffort() {
        InstagramBotConfig cfg = config(true, "menu", "Kodingiz: {code}", PROMOTION_ID, false, null);
        when(promotionRepository.existsByIdAndRestaurant_Id(PROMOTION_ID, TENANT)).thenReturn(true);
        when(couponService.generateCoupons(any())).thenThrow(new RuntimeException("db is on fire"));

        deliver(cfg, "58", "menu?");   // must not throw out of this call

        verify(apiClient).sendPrivateReply(eq(cfg), eq("58"), eq("Kodingiz: "));
    }

    // -------------------------------------------------------------------------
    // Keyword / enabled gating
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("no keyword match: no private reply, but an independently-enabled auto-reply still fires")
    void noMatchSkipsPrivateReplyButNotAutoReply() {
        InstagramBotConfig cfg = config(true, "menu", "Kodingiz: {code}", null,
                true, "Rahmat, {comment}!");

        deliver(cfg, "59", "great food, love it here");

        verify(apiClient, never()).sendPrivateReply(any(), any(), any());
        verify(apiClient).replyToComment(eq(cfg), eq("59"), eq("Rahmat, great food, love it here!"));
        verify(messageLogger).record(eq(cfg), eq("59"), isNull(), eq(InstagramMessageType.AUTO_REPLY),
                any(), any(), isNull());
        verify(messageLogger, never()).record(any(), any(), any(),
                eq(InstagramMessageType.PRIVATE_REPLY), any(), any(), any());
    }

    @Test
    @DisplayName("privateReplyEnabled=false: no private reply even when the keyword is present")
    void disabledNeverFiresEvenOnKeywordMatch() {
        InstagramBotConfig cfg = config(false, "menu", "Kodingiz: {code}", PROMOTION_ID, false, null);

        deliver(cfg, "60", "menu please");

        verify(apiClient, never()).sendPrivateReply(any(), any(), any());
        verify(couponService, never()).generateCoupons(any());
    }

    @Test
    @DisplayName("a blank keyword never matches, even enabled — a DM must not fire on every comment")
    void blankKeywordNeverMatches() {
        InstagramBotConfig cfg = config(true, "  ", "Kodingiz: {code}", null, false, null);

        deliver(cfg, "61", "anything at all");

        verify(apiClient, never()).sendPrivateReply(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Dedup sharing: ONE firstDelivery call gates BOTH the auto-reply and the private reply
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("both features enabled and matching: the dedup check runs exactly once, both actions fire")
    void bothFeaturesShareOneDedupCheck() {
        InstagramBotConfig cfg = config(true, "menu", "Kodingiz: {code}", null,
                true, "Rahmat, {comment}!");

        deliver(cfg, "77", "menu please");

        verify(dedupService, times(1)).firstDelivery(eq(TENANT), eq("cmt:77"));
        verify(apiClient).replyToComment(any(), eq("77"), any());
        verify(apiClient).sendPrivateReply(any(), eq("77"), any());
    }

    @Test
    @DisplayName("a re-delivered comment does not re-send the private reply or re-mint a coupon")
    void redeliveryDoesNotRefirePrivateReply() {
        InstagramBotConfig cfg = config(true, "menu", "Kodingiz: {code}", PROMOTION_ID, false, null);
        when(promotionRepository.existsByIdAndRestaurant_Id(PROMOTION_ID, TENANT)).thenReturn(true);
        when(couponService.generateCoupons(any())).thenReturn(
                List.of(CouponCodeResponse.builder().code("ONLYONCE").build()));
        // First delivery of "cmt:78" records and returns true; the re-delivery is a duplicate.
        when(dedupService.firstDelivery(eq(TENANT), eq("cmt:78"))).thenReturn(true, false);

        deliver(cfg, "78", "menu please");   // first delivery
        deliver(cfg, "78", "menu please");   // Meta re-delivery

        verify(apiClient, times(1)).sendPrivateReply(any(), eq("78"), any());
        verify(couponService, times(1)).generateCoupons(any());
    }

    // -------------------------------------------------------------------------
    // igsid slot carries the comment id (no DM-recipient igsid exists yet)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("the log row's igsid slot carries the comment id, like AUTO_REPLY")
    void logRowCarriesCommentIdInIgsidSlot() {
        InstagramBotConfig cfg = config(true, "menu", "Kodingiz: {code}", null, false, null);

        deliver(cfg, "99", "menu?");

        verify(messageLogger).record(any(), eq("99"), isNull(), eq(InstagramMessageType.PRIVATE_REPLY),
                any(), any(), isNull());
    }
}
