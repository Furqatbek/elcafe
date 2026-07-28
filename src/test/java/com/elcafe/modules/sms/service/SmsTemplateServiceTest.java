package com.elcafe.modules.sms.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.sms.dto.SmsTemplateRequest;
import com.elcafe.modules.sms.dto.SmsTemplateResponse;
import com.elcafe.modules.sms.entity.SmsTemplate;
import com.elcafe.modules.sms.repository.SmsTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the shared {@code AbstractChannelTemplateService} CRUD flow through its SMS subclass — the
 * create duplicate-name guard, tenant binding on create, the update name-conflict check (only when the
 * name changes), toggle, delete, and preview. SMS had no service test before; this doubles as its
 * coverage and as the base's.
 */
@ExtendWith(MockitoExtension.class)
class SmsTemplateServiceTest {

    @Mock SmsTemplateRepository templateRepository;
    @Mock RestaurantAuthorizationService authz;
    @InjectMocks SmsTemplateService service;

    private static SmsTemplate template(Long id, String name) {
        return SmsTemplate.builder().id(id).name(name).content("Hi {name}").type("PROMO").isActive(true).build();
    }

    private static SmsTemplateRequest request(String name) {
        return SmsTemplateRequest.builder().name(name).content("Body").type("PROMO").build();
    }

    @Test
    @DisplayName("getTemplateById — a missing id is 404")
    void getById_notFound() {
        when(templateRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTemplateById(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createTemplate — a duplicate name is rejected before any tenant resolution or save")
    void create_duplicateName() {
        when(templateRepository.existsByName("Promo")).thenReturn(true);
        assertThatThrownBy(() -> service.createTemplate(request("Promo")))
                .isInstanceOf(BadRequestException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate — binds the new template to the caller's restaurant")
    void create_bindsTenant() {
        when(templateRepository.existsByName("Promo")).thenReturn(false);
        when(authz.currentTenantScopeStrict()).thenReturn(7L);
        when(templateRepository.save(any(SmsTemplate.class))).thenAnswer(i -> i.getArgument(0));

        SmsTemplateResponse resp = service.createTemplate(request("Promo"));

        ArgumentCaptor<SmsTemplate> saved = ArgumentCaptor.forClass(SmsTemplate.class);
        verify(templateRepository).save(saved.capture());
        assertThat(saved.getValue().getRestaurantId()).isEqualTo(7L);
        assertThat(saved.getValue().getName()).isEqualTo("Promo");
        assertThat(resp.getName()).isEqualTo("Promo");
    }

    @Test
    @DisplayName("createTemplate — a caller with no restaurant cannot create one (fail closed)")
    void create_noTenant() {
        when(templateRepository.existsByName("Promo")).thenReturn(false);
        when(authz.currentTenantScopeStrict()).thenReturn(null);
        assertThatThrownBy(() -> service.createTemplate(request("Promo")))
                .isInstanceOf(BadRequestException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTemplate — renaming onto an existing name is rejected")
    void update_nameConflict() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template(1L, "Old")));
        when(templateRepository.existsByName("New")).thenReturn(true);
        assertThatThrownBy(() -> service.updateTemplate(1L, request("New")))
                .isInstanceOf(BadRequestException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTemplate — keeping the same name skips the uniqueness check and applies fields")
    void update_sameName_applies() {
        SmsTemplate existing = template(1L, "Old");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(SmsTemplate.class))).thenAnswer(i -> i.getArgument(0));

        SmsTemplateRequest req = SmsTemplateRequest.builder().name("Old").content("Changed").type("PROMO").build();
        service.updateTemplate(1L, req);

        assertThat(existing.getContent()).isEqualTo("Changed");
        verify(templateRepository, never()).existsByName(any());
    }

    @Test
    @DisplayName("toggleTemplateStatus — flips the active flag")
    void toggle_flips() {
        SmsTemplate existing = template(1L, "Old"); // isActive = true
        when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(SmsTemplate.class))).thenAnswer(i -> i.getArgument(0));

        SmsTemplateResponse resp = service.toggleTemplateStatus(1L);
        assertThat(resp.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("deleteTemplate — deletes the found template")
    void delete_deletes() {
        SmsTemplate existing = template(1L, "Old");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
        service.deleteTemplate(1L);
        verify(templateRepository).delete(existing);
    }

    @Test
    @DisplayName("previewTemplate — renders against sample data without persisting")
    void preview_renders() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template(1L, "Old")));
        String rendered = service.previewTemplate(1L, Map.of("name", "Aziz"));
        assertThat(rendered).isEqualTo("Hi Aziz");
        verify(templateRepository, never()).save(any());
    }
}
