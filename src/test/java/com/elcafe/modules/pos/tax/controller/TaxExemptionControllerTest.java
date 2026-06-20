package com.elcafe.modules.pos.tax.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.pos.tax.dto.*;
import com.elcafe.modules.pos.tax.entity.TaxExemptionLog;
import com.elcafe.modules.pos.tax.entity.TaxExemptionType;
import com.elcafe.modules.pos.tax.service.TaxExemptionService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TaxExemptionControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private TaxExemptionService taxExemptionService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private TaxExemptionController controller;
    private final String BASE = "/api/v1/restaurants/1/pos/tax-exemptions";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }

    @Test @DisplayName("POST /types") void createType() throws Exception {
        CreateTaxExemptionTypeRequest req = new CreateTaxExemptionTypeRequest(); req.setName("Gov");
        Restaurant r = new Restaurant(); r.setId(1L);
        when(taxExemptionService.createExemptionType(eq(1L), any()))
                .thenReturn(TaxExemptionType.builder().id(1L).restaurant(r).name("Gov").build());
        mockMvc.perform(post(BASE + "/types").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /types") void getTypes() throws Exception {
        when(taxExemptionService.getExemptionTypes(1L)).thenReturn(List.of());
        mockMvc.perform(get(BASE + "/types")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /customers/{id}") void setExempt() throws Exception {
        SetCustomerTaxExemptRequest req = new SetCustomerTaxExemptRequest();
        Customer customer = new Customer(); customer.setId(1L); customer.setIsTaxExempt(true);
        when(taxExemptionService.setCustomerTaxExempt(eq(1L), any())).thenReturn(customer);
        mockMvc.perform(post(BASE + "/customers/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /customers/{id}") void removeExempt() throws Exception {
        Customer customer = new Customer(); customer.setId(1L); customer.setIsTaxExempt(false);
        when(taxExemptionService.removeCustomerTaxExempt(1L)).thenReturn(customer);
        mockMvc.perform(delete(BASE + "/customers/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /customers/{id}/check") void checkExempt() throws Exception {
        when(taxExemptionService.checkCustomerExemption(1L))
                .thenReturn(TaxExemptCheckResult.builder().customerId(1L).isTaxExempt(true).build());
        mockMvc.perform(get(BASE + "/customers/1/check")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /orders/{id}") void applyToOrder() throws Exception {
        ApplyTaxExemptionRequest req = new ApplyTaxExemptionRequest();
        when(taxExemptionService.applyOrderTaxExemption(eq(1L), any(), eq(1L)))
                .thenReturn(TaxExemptionResult.builder().orderId(1L)
                        .taxExempted(new BigDecimal("12000")).newTotal(new BigDecimal("100000")).build());
        mockMvc.perform(post(BASE + "/orders/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)).param("operatorId", "1"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /orders/{id}") void removeFromOrder() throws Exception {
        when(taxExemptionService.removeOrderTaxExemption(1L, new BigDecimal("0.12"))).thenReturn(new Order());
        mockMvc.perform(delete(BASE + "/orders/1").param("taxRate", "0.12")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /logs") void getLogs() throws Exception {
        when(taxExemptionService.getExemptionLogs(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get(BASE + "/logs")).andExpect(status().isOk());
    }
}
