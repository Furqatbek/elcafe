package com.elcafe.modules.sms.service;

import com.elcafe.modules.sms.config.SmsProperties;
import com.elcafe.modules.sms.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for integrating with Eskiz.uz SMS broker API
 * Implements all available methods from the Eskiz.uz API
 */
@Slf4j
@Service
public class SmsService {

    private final SmsProperties smsProperties;
    private final RestTemplate restTemplate;

    private String authToken;
    private Long tokenExpirationTime;

    public SmsService(SmsProperties smsProperties, RestTemplateBuilder restTemplateBuilder) {
        this.smsProperties = smsProperties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(smsProperties.getConnectionTimeout()))
                .setReadTimeout(Duration.ofMillis(smsProperties.getReadTimeout()))
                .build();
    }

    /**
     * Authenticate with Eskiz.uz and get access token
     * Endpoint: POST /auth/login
     */
    public AuthResponse authenticate() {
        try {
            String url = smsProperties.getBaseUrl() + "/auth/login";

            AuthRequest request = AuthRequest.builder()
                    .email(smsProperties.getEmail())
                    .password(smsProperties.getPassword())
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<AuthRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<AuthResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    AuthResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                AuthResponse authResponse = response.getBody();
                this.authToken = authResponse.getData().getToken();
                this.tokenExpirationTime = System.currentTimeMillis() + smsProperties.getTokenExpirationMs();
                log.info("Successfully authenticated with Eskiz.uz SMS broker");
                return authResponse;
            }

            throw new RuntimeException("Authentication failed");
        } catch (RestClientException e) {
            log.error("Failed to authenticate with Eskiz.uz: {}", e.getMessage());
            throw new RuntimeException("Failed to authenticate with SMS broker", e);
        }
    }

    /**
     * Refresh authentication token
     * Endpoint: PATCH /auth/refresh
     */
    public AuthResponse refreshToken() {
        try {
            String url = smsProperties.getBaseUrl() + "/auth/refresh";

            HttpHeaders headers = createAuthHeaders();

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<AuthResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    entity,
                    AuthResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                AuthResponse authResponse = response.getBody();
                this.authToken = authResponse.getData().getToken();
                this.tokenExpirationTime = System.currentTimeMillis() + smsProperties.getTokenExpirationMs();
                log.info("Successfully refreshed authentication token");
                return authResponse;
            }

            throw new RuntimeException("Token refresh failed");
        } catch (RestClientException e) {
            log.error("Failed to refresh token: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh authentication token", e);
        }
    }

    /**
     * Get authenticated user information
     * Endpoint: GET /auth/user
     */
    public UserInfoResponse getUserInfo() {
        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/auth/user";

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UserInfoResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserInfoResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get user info: {}", e.getMessage());
            throw new RuntimeException("Failed to get user information", e);
        }
    }

    /**
     * Get user limit information
     * Endpoint: GET /user/get-limit
     */
    public UserLimitResponse getUserLimit() {
        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/user/get-limit";

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UserLimitResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserLimitResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get user limit: {}", e.getMessage());
            throw new RuntimeException("Failed to get user limit", e);
        }
    }

    /**
     * Send a single SMS
     * Endpoint: POST /message/sms/send
     */
    public SendSmsResponse sendSms(SendSmsRequest request) {
        if (!smsProperties.getEnabled()) {
            log.info("SMS sending is disabled. Would send: {}", request);
            return createMockResponse();
        }

        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/message/sms/send";

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<SendSmsRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<SendSmsResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    SendSmsResponse.class
            );

            log.info("SMS sent successfully to {}", request.getMobilePhone());
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to send SMS: {}", e.getMessage());
            throw new RuntimeException("Failed to send SMS", e);
        }
    }

    /**
     * Send batch SMS
     * Endpoint: POST /message/sms/send-batch
     */
    public SendSmsResponse sendBatchSms(SendBatchSmsRequest request) {
        if (!smsProperties.getEnabled()) {
            log.info("SMS sending is disabled. Would send batch of {} messages", request.getMessages().size());
            return createMockResponse();
        }

        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/message/sms/send-batch";

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<SendBatchSmsRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<SendSmsResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    SendSmsResponse.class
            );

            log.info("Batch SMS sent successfully. Total messages: {}", request.getMessages().size());
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to send batch SMS: {}", e.getMessage());
            throw new RuntimeException("Failed to send batch SMS", e);
        }
    }

    /**
     * Send global SMS to multiple recipients
     * Endpoint: POST /message/sms/send-global
     */
    public SendSmsResponse sendGlobalSms(SendGlobalSmsRequest request) {
        if (!smsProperties.getEnabled()) {
            log.info("SMS sending is disabled. Would send global SMS to: {}", request.getPhoneNumber());
            return createMockResponse();
        }

        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/message/sms/send-global";

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<SendGlobalSmsRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<SendSmsResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    SendSmsResponse.class
            );

            log.info("Global SMS sent successfully");
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to send global SMS: {}", e.getMessage());
            throw new RuntimeException("Failed to send global SMS", e);
        }
    }

    /**
     * Get message status by ID
     * Endpoint: GET /message/sms/status/{id}
     */
    public MessageStatusResponse getMessageStatus(Long messageId) {
        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/message/sms/status/" + messageId;

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<MessageStatusResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MessageStatusResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get message status: {}", e.getMessage());
            throw new RuntimeException("Failed to get message status", e);
        }
    }

    /**
     * Get user messages with pagination
     * Endpoint: GET /message/sms/get-user-messages
     */
    public UserMessagesResponse getUserMessages(Integer page, Integer perPage) {
        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/message/sms/get-user-messages";

            if (page != null || perPage != null) {
                url += "?";
                if (page != null) url += "page=" + page;
                if (perPage != null) url += (page != null ? "&" : "") + "per_page=" + perPage;
            }

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UserMessagesResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserMessagesResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get user messages: {}", e.getMessage());
            throw new RuntimeException("Failed to get user messages", e);
        }
    }

    /**
     * Get user messages by dispatch ID
     * Endpoint: GET /message/sms/get-user-messages-by-dispatch/{dispatchId}
     */
    public UserMessagesResponse getUserMessagesByDispatch(Long dispatchId, Integer page, Integer perPage) {
        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/message/sms/get-user-messages-by-dispatch/" + dispatchId;

            if (page != null || perPage != null) {
                url += "?";
                if (page != null) url += "page=" + page;
                if (perPage != null) url += (page != null ? "&" : "") + "per_page=" + perPage;
            }

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UserMessagesResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserMessagesResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get messages by dispatch: {}", e.getMessage());
            throw new RuntimeException("Failed to get messages by dispatch", e);
        }
    }

    /**
     * Get dispatch status
     * Endpoint: GET /message/sms/get-dispatch-status/{dispatchId}
     */
    public DispatchStatusResponse getDispatchStatus(Long dispatchId) {
        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/message/sms/get-dispatch-status/" + dispatchId;

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<DispatchStatusResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    DispatchStatusResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get dispatch status: {}", e.getMessage());
            throw new RuntimeException("Failed to get dispatch status", e);
        }
    }

    /**
     * Get user templates
     * Endpoint: GET /user/templates
     */
    public TemplateResponse getTemplates() {
        ensureAuthenticated();

        try {
            String url = smsProperties.getBaseUrl() + "/user/templates";

            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<TemplateResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    TemplateResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to get templates: {}", e.getMessage());
            throw new RuntimeException("Failed to get templates", e);
        }
    }

    /**
     * Ensure we have a valid authentication token
     */
    private void ensureAuthenticated() {
        if (authToken == null || isTokenExpired()) {
            if (authToken == null) {
                authenticate();
            } else {
                try {
                    refreshToken();
                } catch (Exception e) {
                    log.warn("Token refresh failed, re-authenticating");
                    authenticate();
                }
            }
        }
    }

    /**
     * Check if token is expired
     */
    private boolean isTokenExpired() {
        if (tokenExpirationTime == null) {
            return true;
        }
        // Refresh token 1 hour before expiration
        return System.currentTimeMillis() >= (tokenExpirationTime - 3600000);
    }

    /**
     * Create HTTP headers with authentication
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(authToken);
        return headers;
    }

    /**
     * Create mock response for testing when SMS is disabled
     */
    private SendSmsResponse createMockResponse() {
        return SendSmsResponse.builder()
                .message("SMS sending is disabled (mock mode)")
                .data(SendSmsResponse.SmsData.builder()
                        .id(0L)
                        .status("mock")
                        .messageText("Mock response - SMS sending is disabled")
                        .build())
                .build();
    }
}
