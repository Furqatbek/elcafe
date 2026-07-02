package com.elcafe.modules.billing.controller;

import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.dto.ChangePlanRequest;
import com.elcafe.modules.billing.dto.TenantSummaryDto;
import com.elcafe.modules.billing.service.PlatformAdminService;
import com.elcafe.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the platform controller's route mappings, request binding, and delegation to
 * {@link PlatformAdminService}. Authorization (SUPER_ADMIN-only) is enforced declaratively by the
 * class-level {@code @PreAuthorize} and exercised end to end by the security filter chain — standalone
 * MockMvc doesn't apply method security, so it isn't asserted here.
 */
class PlatformAdminControllerTest {

    private MockMvc mvc;
    private PlatformAdminService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(PlatformAdminService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PlatformAdminController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();

        UserPrincipal superAdmin = new UserPrincipal(1L, "ops@platform.test", "x", UserRole.SUPER_ADMIN, true, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(superAdmin, null, superAdmin.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /platform/tenants returns 200 and delegates the search term")
    void listTenants() throws Exception {
        // Explicit PageRequest: a default PageImpl is unpaged, whose getters throw when Jackson tries to
        // serialize them (production sidesteps this with VIA_DTO page serialization).
        Page<TenantSummaryDto> page = new PageImpl<>(List.of(
                TenantSummaryDto.builder().restaurantId(1L).name("Cafe X").active(true).planCode("pro").build()),
                PageRequest.of(0, 20), 1);
        when(service.listTenants(eq("cafe"), any())).thenReturn(page);

        mvc.perform(get("/api/v1/platform/tenants").param("search", "cafe"))
                .andExpect(status().isOk());

        verify(service).listTenants(eq("cafe"), any());
    }

    @Test
    @DisplayName("POST /platform/tenants/{id}/plan binds the path id + body and delegates")
    void changePlan() throws Exception {
        when(service.changePlan(eq(42L), any(ChangePlanRequest.class), any()))
                .thenReturn(BillingStatusDto.builder().planCode("pro").build());

        mvc.perform(post("/api/v1/platform/tenants/42/plan")
                        .contentType("application/json")
                        .content("{\"planCode\":\"pro\"}"))
                .andExpect(status().isOk());

        verify(service).changePlan(eq(42L), any(ChangePlanRequest.class), any());
    }

    @Test
    @DisplayName("POST /platform/tenants/{id}/suspend delegates with active=false")
    void suspend() throws Exception {
        when(service.setActive(eq(5L), eq(false), any()))
                .thenReturn(TenantSummaryDto.builder().restaurantId(5L).active(false).build());

        mvc.perform(post("/api/v1/platform/tenants/5/suspend"))
                .andExpect(status().isOk());

        verify(service).setActive(eq(5L), eq(false), any());
    }

    @Test
    @DisplayName("POST /platform/tenants/{id}/reactivate delegates with active=true")
    void reactivate() throws Exception {
        when(service.setActive(eq(5L), eq(true), any()))
                .thenReturn(TenantSummaryDto.builder().restaurantId(5L).active(true).build());

        mvc.perform(post("/api/v1/platform/tenants/5/reactivate"))
                .andExpect(status().isOk());

        verify(service).setActive(eq(5L), eq(true), any());
    }

    @Test
    @DisplayName("POST /platform/tenants/{id}/cancel delegates to cancel()")
    void cancel() throws Exception {
        when(service.cancel(eq(5L), any()))
                .thenReturn(TenantSummaryDto.builder().restaurantId(5L).active(false).build());

        mvc.perform(post("/api/v1/platform/tenants/5/cancel"))
                .andExpect(status().isOk());

        verify(service).cancel(eq(5L), any());
    }
}
