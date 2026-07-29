package com.elcafe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The prod fail-fast guardrail: the app must refuse to boot under the {@code prod} profile with any
 * fail-open setting still active. Pure unit test — no Spring context.
 */
class ProductionConfigValidatorTest {

    private ProductionConfigValidator validator(boolean otpDev, boolean otpResp, String cors,
                                                boolean creds, boolean swagger, boolean apiDocs) {
        ProductionConfigValidator v = new ProductionConfigValidator();
        ReflectionTestUtils.setField(v, "otpDevelopmentMode", otpDev);
        ReflectionTestUtils.setField(v, "otpIncludeInResponse", otpResp);
        ReflectionTestUtils.setField(v, "corsAllowedOrigins", cors);
        ReflectionTestUtils.setField(v, "corsAllowCredentials", creds);
        ReflectionTestUtils.setField(v, "swaggerEnabled", swagger);
        ReflectionTestUtils.setField(v, "apiDocsEnabled", apiDocs);
        // A real secret by default, so each test exercises only the setting it is about; the
        // datasource-password rule has its own cases below.
        ReflectionTestUtils.setField(v, "datasourcePassword", "a-real-production-secret");
        return v;
    }

    private ProductionConfigValidator validatorWithDbPassword(String password) {
        ProductionConfigValidator v =
                validator(false, false, "https://admin.example.com", true, false, false);
        ReflectionTestUtils.setField(v, "datasourcePassword", password);
        return v;
    }

    @Test @DisplayName("a fully hardened prod config boots")
    void hardened_ok() {
        assertThatCode(() -> validator(false, false, "https://admin.example.com", true, false, false).validate())
                .doesNotThrowAnyException();
    }

    @Test @DisplayName("OTP development-mode true is rejected")
    void otpDevMode_rejected() {
        assertThatThrownBy(() -> validator(true, false, "https://a.com", true, false, false).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("OTP include-in-response true is rejected")
    void otpInResponse_rejected() {
        assertThatThrownBy(() -> validator(false, true, "https://a.com", true, false, false).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("wildcard CORS origin with credentials is rejected")
    void wildcardCorsWithCredentials_rejected() {
        assertThatThrownBy(() -> validator(false, false, "*", true, false, false).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validator(false, false, "https://a.com, *", true, false, false).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("blank CORS origins with credentials is rejected")
    void blankCorsWithCredentials_rejected() {
        assertThatThrownBy(() -> validator(false, false, "", true, false, false).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("Swagger/API-docs enabled is rejected")
    void swagger_rejected() {
        assertThatThrownBy(() -> validator(false, false, "https://a.com", true, true, false).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validator(false, false, "https://a.com", true, false, true).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("wildcard CORS is allowed when credentials are OFF")
    void wildcardCorsWithoutCredentials_ok() {
        assertThatCode(() -> validator(false, false, "*", false, false, false).validate())
                .doesNotThrowAnyException();
    }

    @Test @DisplayName("the well-known 'postgres' dev password is rejected in prod")
    void defaultDatabasePassword_rejected() {
        assertThatThrownBy(() -> validatorWithDbPassword("postgres").validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a blank database password is rejected in prod")
    void blankDatabasePassword_rejected() {
        assertThatThrownBy(() -> validatorWithDbPassword("").validate())
                .isInstanceOf(IllegalStateException.class);
    }
}
