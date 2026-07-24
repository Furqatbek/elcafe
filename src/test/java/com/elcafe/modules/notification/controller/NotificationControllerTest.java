package com.elcafe.modules.notification.controller;

import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.enums.NotificationType;
import com.elcafe.modules.notification.enums.UserRole;
import com.elcafe.modules.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;
    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private NotificationController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        Notification n = Notification.builder()
                .id(1L).userRole(UserRole.WAITER).userId(5L)
                .type(NotificationType.NEW_ORDER).title("t").message("m").build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
    }

    @Test @DisplayName("PATCH /{id}/read marks a notification read (web admin)")
    void patchMarksRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/1/read")).andExpect(status().isOk());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test @DisplayName("POST /{id}/read marks a notification read (waiter app)")
    void postMarksRead() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/1/read")).andExpect(status().isOk());
        verify(notificationRepository).save(any(Notification.class));
    }
}
