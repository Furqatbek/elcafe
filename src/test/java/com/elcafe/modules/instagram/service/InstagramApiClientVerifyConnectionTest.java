package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.dto.InstagramConnectionTestResult;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Guards {@link InstagramApiClient#verifyConnection} (V177) — the connection-test / health-check GET
 * an operator's manual "test this token" click drives. Mirrors {@link InstagramApiClientPathInjectionTest}:
 * a fresh client + {@link MockRestServiceServer} per test, no Spring context, so a request the client is
 * not supposed to make (kill switch, malformed input) fails the test outright when no expectation is set.
 *
 * <p>Unlike every other method on this client, this one is a GET that sends nothing and reports a
 * verification outcome rather than a delivery — so it gets its own small result type
 * ({@link InstagramConnectionTestResult}) instead of {@link InstagramSendResult}, though it reuses that
 * type's {@code Failure} vocabulary (see that record's javadoc for why).
 *
 * <p><b>Why the Meta-error-code cases below call {@code verifyConnectionFallback} directly instead of
 * driving a failing response through {@code verifyConnection()} + {@link MockRestServiceServer}:</b> per
 * this class's own javadoc, {@code @CircuitBreaker} only converts a thrown exception into the typed
 * result via Spring AOP proxying a real bean. A plain {@code new InstagramApiClient(...)} (exactly what
 * every test in this class, including {@link InstagramApiClientPathInjectionTest}, constructs) is never
 * proxied, so a genuine HTTP error response propagates out of {@code verifyConnection()} as a thrown
 * {@code HttpClientErrorException} rather than returning a typed failure — that is not a bug, it is the
 * documented throw/fallback split working as designed. Calling the fallback method directly (reflectively,
 * since it is private) exercises exactly the classification logic resilience4j would invoke, with exactly
 * the arguments it would pass, without needing a full Spring context just to re-prove a well-trodden
 * library mechanism this class does not uniquely own.
 */
class InstagramApiClientVerifyConnectionTest {

    private static final String BASE = "https://graph.test";
    private static final String VERSION = "v19.0";
    private static final String GRAPH = BASE + "/" + VERSION;
    private static final String ACCOUNT_ID = "17841400000000000";

    private InstagramApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new InstagramApiClient(new RestTemplateBuilder(), BASE, VERSION);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    private InstagramBotConfig config() {
        return InstagramBotConfig.builder()
                .instagramAccountId(ACCOUNT_ID)
                .accessToken("merchant-token")
                .build();
    }

    /** Invoke the private {@code verifyConnectionFallback(InstagramBotConfig, Throwable)} overload
     *  directly — see the class javadoc above for why. */
    private InstagramConnectionTestResult invokeFallback(InstagramBotConfig config, Throwable t) throws Exception {
        Method fallback = InstagramApiClient.class.getDeclaredMethod(
                "verifyConnectionFallback", InstagramBotConfig.class, Throwable.class);
        fallback.setAccessible(true);
        return (InstagramConnectionTestResult) fallback.invoke(client, config, t);
    }

    /** The exact shape {@code DefaultResponseErrorHandler} builds for a non-2xx Graph response. */
    private static HttpClientErrorException metaError(HttpStatus status, String jsonBody) {
        return HttpClientErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                jsonBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // The happy path: exact URL, bearer header, 200 maps to ok with the echoed account
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a connection test hits GET {accountId}?fields=id,username with the token in a bearer header")
    void hitsTheRightUrlWithABearerHeader() {
        server.expect(requestTo(GRAPH + "/" + ACCOUNT_ID + "?fields=id,username"))
                .andExpect(method(GET))
                // Token travels in the Authorization header, never the query string — same discipline
                // as every send on this client (see InstagramApiClient#authHeaders javadoc).
                .andExpect(header("Authorization", "Bearer merchant-token"))
                .andRespond(withSuccess(
                        "{\"id\":\"" + ACCOUNT_ID + "\",\"username\":\"my_cafe\"}", MediaType.APPLICATION_JSON));

        InstagramConnectionTestResult result = client.verifyConnection(config());

        assertThat(result.ok()).isTrue();
        assertThat(result.failure()).isNull();
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.username()).isEqualTo("my_cafe");
        server.verify();
    }

    @Test
    @DisplayName("the fields query parameter asks for exactly id and username")
    void requestsExactlyIdAndUsername() {
        server.expect(requestTo(GRAPH + "/" + ACCOUNT_ID + "?fields=id,username"))
                .andExpect(queryParam("fields", "id,username"))
                .andRespond(withSuccess("{\"id\":\"" + ACCOUNT_ID + "\"}", MediaType.APPLICATION_JSON));

        client.verifyConnection(config());

        server.verify();
    }

    // -------------------------------------------------------------------------
    // Meta code 190 (dead/expired token) maps to TOKEN_INVALID — via the fallback (see class javadoc)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a Meta code-190 error response maps to a TOKEN_INVALID failure")
    void metaCode190MapsToTokenInvalid() throws Exception {
        HttpClientErrorException ex = metaError(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"message\":\"Error validating access token\","
                        + "\"type\":\"OAuthException\",\"code\":190,\"error_subcode\":463}}");

        InstagramConnectionTestResult result = invokeFallback(config(), ex);

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.TOKEN_INVALID);
        assertThat(result.code()).isEqualTo(190);
    }

    @Test
    @DisplayName("a non-190 Meta error (e.g. rate limiting) does not map to TOKEN_INVALID")
    void nonTokenErrorIsNotMisclassifiedAsTokenInvalid() throws Exception {
        HttpClientErrorException ex = metaError(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"message\":\"Application request limit reached\","
                        + "\"type\":\"OAuthException\",\"code\":4,\"error_subcode\":0}}");

        InstagramConnectionTestResult result = invokeFallback(config(), ex);

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.RATE_LIMITED);
    }

    @Test
    @DisplayName("an open circuit reports CIRCUIT_OPEN, not a live-failure code")
    void openCircuitReportsCircuitOpen() throws Exception {
        Method fallback = InstagramApiClient.class.getDeclaredMethod("verifyConnectionFallback",
                InstagramBotConfig.class, io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
        fallback.setAccessible(true);
        // The exact exception instance is irrelevant to this fallback overload (it never inspects it,
        // only its own presence selects it) — null stands in for "any CallNotPermittedException".
        InstagramConnectionTestResult result =
                (InstagramConnectionTestResult) fallback.invoke(client, config(), (Object) null);

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.CIRCUIT_OPEN);
    }

    // -------------------------------------------------------------------------
    // Kill switch — no request ever reaches Meta
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("kill switch: a connection test never touches Meta")
    void killSwitchSuppressesConnectionTest() {
        ReflectionTestUtils.setField(client, "enabled", false);

        InstagramConnectionTestResult result = client.verifyConnection(config());

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();   // no expectation was set — any request would fail this
    }

    // -------------------------------------------------------------------------
    // Bad input is rejected before any HTTP call, same discipline as every send on this client
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a malformed instagram account id is rejected before any HTTP call")
    void malformedAccountIdNeverReachesTheWire() {
        InstagramBotConfig badConfig = InstagramBotConfig.builder()
                .instagramAccountId("me/subscribed_apps?access_token=")
                .accessToken("merchant-token")
                .build();

        InstagramConnectionTestResult result = client.verifyConnection(badConfig);

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();
    }

    @Test
    @DisplayName("no stored access token is rejected before any HTTP call")
    void noAccessTokenNeverReachesTheWire() {
        InstagramBotConfig noToken = InstagramBotConfig.builder()
                .instagramAccountId(ACCOUNT_ID)
                .accessToken(null)
                .build();

        InstagramConnectionTestResult result = client.verifyConnection(noToken);

        assertThat(result.ok()).isFalse();
        assertThat(result.failure()).isEqualTo(InstagramSendResult.Failure.INVALID_REQUEST);
        server.verify();
    }
}
