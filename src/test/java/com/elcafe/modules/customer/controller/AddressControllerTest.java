package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.AddressResponse;
import com.elcafe.modules.customer.dto.CreateAddressRequest;
import com.elcafe.modules.customer.dto.UpdateAddressRequest;
import com.elcafe.modules.customer.service.AddressService;
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
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AddressControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private AddressService addressService;
    @InjectMocks private AddressController controller;
    private final String BASE = "/api/v1/customers/1/addresses";
    private AddressResponse resp;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        resp = AddressResponse.builder().id(1L).customerId(1L).label("Home").city("Tashkent").build();
    }

    @Test @DisplayName("GET /") void list() throws Exception {
        when(addressService.getCustomerAddresses(1L)).thenReturn(List.of(resp));
        mockMvc.perform(get(BASE)).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{addressId}") void getById() throws Exception {
        when(addressService.getAddress(1L, 1L)).thenReturn(resp);
        mockMvc.perform(get(BASE + "/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /default") void getDefault() throws Exception {
        when(addressService.getDefaultAddress(1L)).thenReturn(resp);
        mockMvc.perform(get(BASE + "/default")).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void create() throws Exception {
        CreateAddressRequest req = new CreateAddressRequest(); req.setLabel("Work");
        when(addressService.createAddress(eq(1L), any())).thenReturn(resp);
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("PUT /{addressId}") void update() throws Exception {
        UpdateAddressRequest req = new UpdateAddressRequest(); req.setLabel("Updated");
        when(addressService.updateAddress(eq(1L), eq(1L), any())).thenReturn(resp);
        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("PUT /{addressId}/default") void setDefault() throws Exception {
        when(addressService.setDefaultAddress(1L, 1L)).thenReturn(resp);
        mockMvc.perform(put(BASE + "/1/default")).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /{addressId}") void deleteAddr() throws Exception {
        mockMvc.perform(delete(BASE + "/1")).andExpect(status().isOk());
        verify(addressService).deleteAddress(1L, 1L);
    }
}
