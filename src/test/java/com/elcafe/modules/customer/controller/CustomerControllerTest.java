package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.service.CustomerService;
import com.elcafe.modules.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private CustomerService customerService;
    @Mock private OrderService orderService;
    @InjectMocks private CustomerController controller;
    private Customer customer;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        customer = Customer.builder().id(1L).firstName("Test").lastName("Customer")
                .phone("+998901234567").active(true).build();
    }

    @Test @DisplayName("POST /") void create() throws Exception {
        when(customerService.createCustomerWithReferral(any())).thenReturn(customer);
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Test\",\"lastName\":\"Customer\",\"phone\":\"+998901234567\"}"))
                .andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{id}") void update() throws Exception {
        when(customerService.updateCustomer(eq(1L), any())).thenReturn(customer);
        mockMvc.perform(put("/api/v1/customers/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Updated\"}")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(customerService.getCustomerWithMarketing(1L)).thenReturn(com.elcafe.modules.customer.dto.CustomerResponse.from(customer));
        mockMvc.perform(get("/api/v1/customers/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /") void getAll() throws Exception {
        when(customerService.getAllCustomersWithMarketing(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}/orders") void getOrders() throws Exception {
        when(orderService.getOrdersByCustomer(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/customers/1/orders")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /search/phone") void searchPhone() throws Exception {
        when(customerService.getCustomerByPhone("+998901234567")).thenReturn(customer);
        mockMvc.perform(get("/api/v1/customers/search/phone").param("phone", "+998901234567"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("GET /suggest/phone") void suggestPhone() throws Exception {
        when(customerService.searchCustomersByPhone("9012")).thenReturn(List.of(customer));
        mockMvc.perform(get("/api/v1/customers/suggest/phone").param("phone", "9012"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{id}") void deleteCustomer() throws Exception {
        mockMvc.perform(delete("/api/v1/customers/1")).andExpect(status().isOk());
        verify(customerService).deleteCustomer(1L);
    }

    @Test @DisplayName("GET /by-qr/{code} — returns customer for matching code")
    void getByQr_returnsCustomer() throws Exception {
        customer.setQrCode("CST-ABCDEF123456");
        when(customerService.getCustomerByQrCode("CST-ABCDEF123456")).thenReturn(customer);
        when(customerService.getCustomerWithMarketing(1L))
                .thenReturn(com.elcafe.modules.customer.dto.CustomerResponse.from(customer));

        mockMvc.perform(get("/api/v1/customers/by-qr/CST-ABCDEF123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrCode").value("CST-ABCDEF123456"));
    }

    @Test @DisplayName("GET /by-qr/{code} — surfaces 404 when code not found")
    void getByQr_notFound() throws Exception {
        when(customerService.getCustomerByQrCode("CST-MISSING000000"))
                .thenThrow(new com.elcafe.exception.ResourceNotFoundException("Customer", "qrCode", "CST-MISSING000000"));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/customers/by-qr/CST-MISSING000000")))
                .hasRootCauseInstanceOf(com.elcafe.exception.ResourceNotFoundException.class);
    }

    @Test @DisplayName("POST /{id}/qr-code/regenerate — rotates the code")
    void regenerateQrCode_returnsRefreshed() throws Exception {
        customer.setQrCode("CST-NEWCODE12345");
        when(customerService.getCustomerWithMarketing(1L))
                .thenReturn(com.elcafe.modules.customer.dto.CustomerResponse.from(customer));

        mockMvc.perform(post("/api/v1/customers/1/qr-code/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrCode").value("CST-NEWCODE12345"));

        verify(customerService).regenerateQrCode(1L);
    }
}
