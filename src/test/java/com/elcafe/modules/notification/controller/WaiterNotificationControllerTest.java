package com.elcafe.modules.notification.controller;

import com.elcafe.modules.notification.dto.waiter.PushNotificationDto;
import com.elcafe.modules.notification.dto.waiter.RegisterDeviceResponse;
import com.elcafe.modules.notification.enums.DevicePlatform;
import com.elcafe.modules.notification.service.WaiterNotificationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaiterNotificationControllerTest {

    private MockMvc mockMvc;
    @Mock private WaiterNotificationService service;
    @InjectMocks private WaiterNotificationController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /devices registers a device and returns 201")
    void registerDevice() throws Exception {
        when(service.registerDevice(eq(5L), any())).thenReturn(RegisterDeviceResponse.builder()
                .deviceId(1L).token("ExponentPushToken[abc]").platform(DevicePlatform.ANDROID).build());

        mockMvc.perform(post("/api/v1/waiter/notifications/devices")
                        .header("X-Waiter-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"ExponentPushToken[abc]\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deviceId").value(1));
    }

    @Test
    @DisplayName("POST /{id}/read scopes to X-Waiter-Id and returns the updated notification")
    void markRead() throws Exception {
        when(service.markRead(5L, 9L)).thenReturn(PushNotificationDto.builder()
                .id(9L).type("NEW_ORDER").title("t").body("b").read(true).build());

        mockMvc.perform(post("/api/v1/waiter/notifications/9/read").header("X-Waiter-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        verify(service).markRead(5L, 9L);
    }

    @Test
    @DisplayName("POST /read-all marks all read for the waiter")
    void markAllRead() throws Exception {
        mockMvc.perform(post("/api/v1/waiter/notifications/read-all").header("X-Waiter-Id", "5"))
                .andExpect(status().isOk());
        verify(service).markAllRead(5L);
    }
}
