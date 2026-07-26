package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramConnectionTestResult;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.service.InstagramBotConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the V177 test-connection endpoint's wiring: {@code POST
 * /api/v1/instagram/config/{id}/test-connection} delegates to
 * {@link InstagramBotConfigService#testConnection} for the path id and returns its typed result
 * unchanged, always 200 — the outcome (ok vs. a failure reason such as TOKEN_INVALID) is the response
 * BODY, not the HTTP status; see the endpoint's own javadoc. Role gating for the whole controller
 * (class-level {@code @PreAuthorize}) is pinned separately by {@code RbacGateAnnotationTest}, which this
 * suite does not touch — adding a sibling method to an already-gated class does not change what that
 * test asserts.
 */
@ExtendWith(MockitoExtension.class)
class InstagramBotConfigControllerTestConnectionTest {

    @Mock private InstagramBotConfigService configService;

    private InstagramBotConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new InstagramBotConfigController(configService);
    }

    @Test
    @DisplayName("test-connection delegates to the service for the path id and returns its result, 200")
    void delegatesToServiceForPathId() {
        InstagramConnectionTestResult ok = InstagramConnectionTestResult.ok("17841400000000000", "my_cafe");
        when(configService.testConnection(7L)).thenReturn(ok);

        ResponseEntity<InstagramConnectionTestResult> response = controller.testConnection(7L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(ok);
        verify(configService).testConnection(7L);
    }

    @Test
    @DisplayName("a failed connection test still returns 200 — the failure reason is in the body, not the status")
    void failedTestStillReturns200() {
        InstagramConnectionTestResult failed =
                InstagramConnectionTestResult.failed(InstagramSendResult.Failure.TOKEN_INVALID, 190, "bad token");
        when(configService.testConnection(8L)).thenReturn(failed);

        ResponseEntity<InstagramConnectionTestResult> response = controller.testConnection(8L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().ok()).isFalse();
        assertThat(response.getBody().failure()).isEqualTo(InstagramSendResult.Failure.TOKEN_INVALID);
    }
}
