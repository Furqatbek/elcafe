package com.elcafe.modules.billing.interceptor;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ForbiddenException;
import com.elcafe.exception.GlobalExceptionHandler;
import com.elcafe.modules.billing.service.PlanGateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanWriteGuardInterceptorTest {

    private final PlanGateService gate = mock(PlanGateService.class);
    private final RestaurantAuthorizationService authz = mock(RestaurantAuthorizationService.class);
    private final PlanWriteGuardInterceptor interceptor = new PlanWriteGuardInterceptor(gate, authz);
    private final MockHttpServletResponse res = new MockHttpServletResponse();

    private boolean preHandle(String method, String uri) {
        return interceptor.preHandle(new MockHttpServletRequest(method, uri), res, new Object());
    }

    @Test
    @DisplayName("GET is never gated")
    void get_notGated() {
        assertTrue(preHandle("GET", "/api/v1/orders"));
        verifyNoInteractions(gate);
    }

    @Test
    @DisplayName("/auth and /billing mutations always pass (so an expired tenant can log in / renew)")
    void authAndBilling_whitelisted() {
        assertTrue(preHandle("POST", "/api/v1/auth/login"));
        assertTrue(preHandle("POST", "/api/v1/billing/admin/set-plan"));
        verifyNoInteractions(gate);
    }

    @Test
    @DisplayName("SUPER_ADMIN is never gated")
    void superAdmin_notGated() {
        when(authz.isAdmin()).thenReturn(true);
        assertTrue(preHandle("POST", "/api/v1/orders"));
        verifyNoInteractions(gate);
    }

    @Test
    @DisplayName("non-staff principal (null restaurant) passes through")
    void nonStaff_notGated() {
        when(authz.isAdmin()).thenReturn(false);
        when(authz.getCurrentUserRestaurantId()).thenReturn(null);
        assertTrue(preHandle("POST", "/api/v1/orders"));
        verifyNoInteractions(gate);
    }

    @Test
    @DisplayName("staff of an active tenant: requireWriteAccess is called and passes")
    void activeStaff_passes() {
        when(authz.isAdmin()).thenReturn(false);
        when(authz.getCurrentUserRestaurantId()).thenReturn(5L);
        assertTrue(preHandle("POST", "/api/v1/orders"));
        verify(gate).requireWriteAccess(5L);
    }

    @Test
    @DisplayName("staff of a read-only tenant: requireWriteAccess throws Forbidden'plan.read_only'")
    void readOnlyStaff_throws() {
        when(authz.isAdmin()).thenReturn(false);
        when(authz.getCurrentUserRestaurantId()).thenReturn(5L);
        doThrow(new ForbiddenException("plan.read_only")).when(gate).requireWriteAccess(5L);
        assertThrows(ForbiddenException.class, () -> preHandle("POST", "/api/v1/orders"));
    }

    @Test
    @DisplayName("end-to-end: a read-only tenant's POST → 403 plan.read_only; GET → 200")
    void endToEnd_readOnly_returns403() throws Exception {
        when(authz.isAdmin()).thenReturn(false);
        when(authz.getCurrentUserRestaurantId()).thenReturn(5L);
        doThrow(new ForbiddenException("plan.read_only")).when(gate).requireWriteAccess(5L);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/orders"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("plan.read_only"));

        mvc.perform(get("/api/v1/orders")).andExpect(status().isOk());
    }

    @RestController
    static class DummyController {
        @PostMapping("/api/v1/orders")
        public String create() {
            return "ok";
        }

        @GetMapping("/api/v1/orders")
        public String list() {
            return "ok";
        }
    }
}
