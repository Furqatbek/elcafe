package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.AddLinkedItemRequest;
import com.elcafe.modules.menu.dto.LinkedItemDTO;
import com.elcafe.modules.menu.enums.LinkType;
import com.elcafe.modules.menu.service.LinkedItemService;
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
class LinkedItemControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private LinkedItemService linkedItemService;
    @InjectMocks private LinkedItemController controller;

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @Test @DisplayName("GET / — linked items") void getAll() throws Exception {
        LinkedItemDTO dto = LinkedItemDTO.builder().id(1L).productId(1L).linkedProductId(2L)
                .linkType(LinkType.RECOMMENDED).build();
        when(linkedItemService.getLinkedItems(1L)).thenReturn(List.of(dto));
        mockMvc.perform(get("/api/v1/products/1/linked-items")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /by-type") void byType() throws Exception {
        when(linkedItemService.getLinkedItemsByType(1L, LinkType.UPSELL)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/products/1/linked-items/by-type").param("linkType", "UPSELL"))
                .andExpect(status().isOk());
    }
    @Test @DisplayName("POST /") void add() throws Exception {
        AddLinkedItemRequest req = new AddLinkedItemRequest();
        req.setLinkedProductId(2L); req.setLinkType(LinkType.RECOMMENDED);
        LinkedItemDTO dto = LinkedItemDTO.builder().id(1L).productId(1L).linkedProductId(2L).build();
        when(linkedItemService.addLinkedItem(eq(1L), any())).thenReturn(dto);
        mockMvc.perform(post("/api/v1/products/1/linked-items").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("DELETE /{id}") void deleteItem() throws Exception {
        mockMvc.perform(delete("/api/v1/products/1/linked-items/1")).andExpect(status().isOk());
        verify(linkedItemService).deleteLinkedItem(1L);
    }
}
