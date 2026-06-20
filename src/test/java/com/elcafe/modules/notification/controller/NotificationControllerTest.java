package com.elcafe.modules.notification.controller;

import com.elcafe.modules.notification.entity.Notification;
import com.elcafe.modules.notification.enums.UserRole;
import com.elcafe.modules.notification.repository.NotificationRepository;
import com.elcafe.security.CustomerPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
    @Mock private NotificationRepository repo;
    @InjectMocks private NotificationController controller;

    private MockMvc consumerMvc;  // authenticated as CustomerPrincipal(id=1)
    private MockMvc staffMvc;     // non-consumer principal → @AuthenticationPrincipal CustomerPrincipal is null

    @BeforeEach void setUp() {
        consumerMvc = build(CustomerPrincipal.create("+998901234567", 1L));
        staffMvc = build(null);
    }

    private MockMvc build(CustomerPrincipal principal) {
        HandlerMethodArgumentResolver resolver = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(CustomerPrincipal.class);
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
        return MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(resolver).build();
    }

    @Test @DisplayName("consumer query: client role/userId are ignored, forced to (CUSTOMER, ownId)")
    void consumerForcedToOwn() throws Exception {
        when(repo.countUnreadForUser(eq(UserRole.CUSTOMER), eq(1L))).thenReturn(0L);
        consumerMvc.perform(get("/api/v1/notifications/unread/count?role=ADMIN&userId=999"))
                .andExpect(status().isOk());
        verify(repo).countUnreadForUser(eq(UserRole.CUSTOMER), eq(1L));
        verify(repo, never()).countUnreadForUser(eq(UserRole.ADMIN), any());
    }

    @Test @DisplayName("consumer can mark its own notification read")
    void consumerMarksOwnRead() throws Exception {
        Notification own = Notification.builder().id(5L).userRole(UserRole.CUSTOMER).userId(1L).build();
        when(repo.findById(5L)).thenReturn(Optional.of(own));
        consumerMvc.perform(patch("/api/v1/notifications/5/read")).andExpect(status().isOk());
        verify(repo).save(own);
    }

    @Test @DisplayName("consumer cannot mark another customer's notification read (IDOR)")
    void consumerCannotMarkOthersRead() {
        Notification other = Notification.builder().id(6L).userRole(UserRole.CUSTOMER).userId(2L).build();
        when(repo.findById(6L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> consumerMvc.perform(patch("/api/v1/notifications/6/read")))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        verify(repo, never()).save(any());
    }

    @Test @DisplayName("consumer cannot delete a non-CUSTOMER notification (IDOR)")
    void consumerCannotDeleteStaffNotification() {
        Notification adminNote = Notification.builder().id(7L).userRole(UserRole.ADMIN).userId(1L).build();
        when(repo.findById(7L)).thenReturn(Optional.of(adminNote));
        assertThatThrownBy(() -> consumerMvc.perform(delete("/api/v1/notifications/7")))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        verify(repo, never()).delete(any());
    }

    @Test @DisplayName("staff principal keeps pass-through behaviour (provided role/userId used)")
    void staffPassThrough() throws Exception {
        when(repo.countUnreadForUser(eq(UserRole.ADMIN), eq(999L))).thenReturn(0L);
        staffMvc.perform(get("/api/v1/notifications/unread/count?role=ADMIN&userId=999"))
                .andExpect(status().isOk());
        verify(repo).countUnreadForUser(eq(UserRole.ADMIN), eq(999L));
    }
}
