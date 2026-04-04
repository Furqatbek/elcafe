package com.elcafe.modules.auth.controller;

import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.service.ConsumerAuthService;
import com.elcafe.modules.customer.enums.RegistrationSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConsumerAuthControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ConsumerAuthService consumerAuthService;
    @InjectMocks private ConsumerAuthController controller;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("POST /login — request OTP") void requestOtp() throws Exception {
        ConsumerLoginRequest req = new ConsumerLoginRequest();
        req.setPhoneNumber("+998901234567"); req.setRegistrationSource(RegistrationSource.MOBILE_APP);
        when(consumerAuthService.requestOtp(any(), anyString(), anyString()))
                .thenReturn(ConsumerLoginResponse.builder().message("OTP sent").phoneNumber("+998901234567")
                        .expiresAt(LocalDateTime.now().plusMinutes(5)).expiresInSeconds(300L).build());
        mockMvc.perform(post("/api/v1/consumer/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /verify — verify OTP") void verifyOtp() throws Exception {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setPhoneNumber("+998901234567"); req.setOtpCode("123456");
        when(consumerAuthService.verifyOtp(any(), anyString(), anyString()))
                .thenReturn(ConsumerAuthResponse.builder().accessToken("at").refreshToken("rt")
                        .customerId(1L).phoneNumber("+998901234567").build());
        mockMvc.perform(post("/api/v1/consumer/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /refresh") void refresh() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest(); req.setRefreshToken("rt");
        when(consumerAuthService.refreshAccessToken(any(), anyString(), anyString()))
                .thenReturn(ConsumerAuthResponse.builder().accessToken("new-at").refreshToken("rt").build());
        mockMvc.perform(post("/api/v1/consumer/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /logout") void logout() throws Exception {
        mockMvc.perform(post("/api/v1/consumer/auth/logout").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        verify(consumerAuthService).logout("test-token");
    }
}
