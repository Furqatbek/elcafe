package com.elcafe.modules.auth.controller;

import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.service.AuthService;
import com.elcafe.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private AuthService authService;
    @InjectMocks private AuthController controller;

    @BeforeEach void setUp() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(UserPrincipal.class);
            }
            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(principalResolver)
                .build();
    }

    @Test @DisplayName("POST /register") void register() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@test.com"); req.setPassword("password123");
        req.setFirstName("New"); req.setLastName("User");
        req.setRestaurantId(1L);
        when(authService.register(any())).thenReturn(AuthResponse.builder()
                .accessToken("at").refreshToken("rt").build());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }

    /**
     * The registration this endpoint used to perform — no restaurant — produced an OWNER bound to
     * nothing, i.e. an account that signed in and then saw an empty application. Rejected at the DTO
     * now, so the mistake surfaces as a 400 with a reason instead of as a support ticket weeks later.
     */
    @Test @DisplayName("POST /register — refused without a restaurant, which used to be the only option")
    void register_requiresRestaurant() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@test.com"); req.setPassword("password123");
        req.setFirstName("New"); req.setLastName("User");
        // restaurantId deliberately omitted
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest());
        verify(authService, never()).register(any());
    }
    @Test @DisplayName("POST /login") void login() throws Exception {
        LoginRequest req = new LoginRequest(); req.setEmail("a@t.com"); req.setPassword("pass");
        when(authService.login(any())).thenReturn(AuthResponse.builder().accessToken("at").refreshToken("rt").build());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /refresh") void refresh() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest(); req.setRefreshToken("rt");
        when(authService.refreshToken(any())).thenReturn(AuthResponse.builder().accessToken("at").refreshToken("rt").build());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /forgot-password") void forgot() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest(); req.setEmail("a@t.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
        verify(authService).forgotPassword(any());
    }
    @Test @DisplayName("POST /reset-password") void reset() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest(); req.setToken("tok"); req.setNewPassword("newpass123");
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
        verify(authService).resetPassword(any());
    }
    @Test @DisplayName("POST /change-password") void change() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest(); req.setCurrentPassword("old"); req.setNewPassword("newpass123");
        mockMvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
}
